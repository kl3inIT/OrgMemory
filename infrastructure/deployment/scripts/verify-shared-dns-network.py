#!/usr/bin/env python3
"""Verify the cross-stack Docker DNS and isolation contract."""

from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys

REPOSITORY = Path(__file__).resolve().parents[3]
PRODUCTION_COMPOSE = Path("infrastructure/deployment/compose.production.yaml")
OBSERVABILITY_COMPOSE = Path(
    "infrastructure/observability/compose.observability.yaml"
)
DOCS_COMPOSE = Path("infrastructure/deployment/compose.docs.yaml")

COMPOSE_CONTRACTS = {
    PRODUCTION_COMPOSE: {"orgmemory-internal", "proxy", "shared-infra"},
    OBSERVABILITY_COMPOSE: {"observability-internal", "proxy", "shared-infra"},
    DOCS_COMPOSE: {"proxy", "shared-infra"},
}

STABLE_CROSS_STACK_NAMES = {
    PRODUCTION_COMPOSE: {
        "api": "orgmemory-api",
        "keycloak": "orgmemory-keycloak",
        "mcp": "orgmemory-mcp",
        "minio": "orgmemory-minio",
        "openfga": "orgmemory-openfga",
        "web": "orgmemory-web",
        "worker": "orgmemory-worker",
    },
    OBSERVABILITY_COMPOSE: {
        "alloy": "observability-alloy",
        "cadvisor": "observability-cadvisor",
        "grafana": "observability-grafana",
        "loki": "observability-loki",
        "node-exporter": "observability-node-exporter",
        "postgres-exporter": "observability-postgres-exporter",
        "prometheus": "observability-prometheus",
        "tempo": "observability-tempo",
    },
    DOCS_COMPOSE: {"orgmemory-docs": "orgmemory-docs"},
}

PRESERVED_NETWORK_MEMBERSHIPS = {
    PRODUCTION_COMPOSE: {
        "orgmemory-internal": {
            "age-reconcile",
            "api",
            "keycloak",
            "mcp",
            "minio",
            "openfga",
            "openfga-bootstrap",
            "openfga-migrate",
            "openfga-model-write",
            "openfga-ready",
            "web",
            "worker",
        },
        "proxy": {"keycloak", "mcp", "web"},
    },
    OBSERVABILITY_COMPOSE: {
        "observability-internal": {
            "alloy",
            "cadvisor",
            "grafana",
            "loki",
            "node-exporter",
            "postgres-exporter",
            "prometheus",
            "tempo",
        },
        "proxy": {"grafana"},
    },
    DOCS_COMPOSE: {"proxy": {"orgmemory-docs"}},
}

EXPECTED_PORTS = {
    PRODUCTION_COMPOSE: {},
    OBSERVABILITY_COMPOSE: {
        "alloy": ["127.0.0.1:${ALLOY_PORT:-12345}:12345"],
        "grafana": ["127.0.0.1:${GRAFANA_PORT:-3001}:3000"],
        "prometheus": ["127.0.0.1:${PROMETHEUS_PORT:-9090}:9090"],
    },
    DOCS_COMPOSE: {},
}

EXPECTED_EXPOSE = {
    PRODUCTION_COMPOSE: {},
    OBSERVABILITY_COMPOSE: {},
    DOCS_COMPOSE: {"orgmemory-docs": ["3000"]},
}


def render_compose(relative_path: Path) -> dict[str, object]:
    completed = subprocess.run(
        [
            "docker",
            "compose",
            "--file",
            str(relative_path),
            "config",
            "--no-interpolate",
            "--format",
            "json",
        ],
        cwd=REPOSITORY,
        check=False,
        capture_output=True,
        text=True,
    )
    if completed.returncode != 0:
        raise RuntimeError(
            f"Docker Compose could not render {relative_path}:\n{completed.stderr.strip()}"
        )
    return json.loads(completed.stdout)


def network_members(services: dict[str, object], network_name: str) -> set[str]:
    return {
        service_name
        for service_name, service in services.items()
        if isinstance(service, dict)
        and isinstance(service.get("networks"), dict)
        and network_name in service["networks"]
    }


def verify(
    relative_path: Path, required_networks: set[str]
) -> tuple[list[str], int, str | None]:
    rendered = render_compose(relative_path)
    networks = rendered.get("networks", {})
    services = rendered.get("services", {})
    failures: list[str] = []

    if not isinstance(networks, dict) or not isinstance(services, dict):
        return [f"{relative_path}: rendered Compose document has an invalid shape"], 0, None

    missing_networks = required_networks.difference(networks)
    if missing_networks:
        failures.append(
            f"{relative_path}: missing network declarations {sorted(missing_networks)}"
        )

    shared = networks.get("shared-infra")
    if not isinstance(shared, dict) or shared.get("external") is not True:
        failures.append(f"{relative_path}: shared-infra must remain an external network")
        shared_name = None
    else:
        candidate_name = shared.get("name")
        shared_name = candidate_name if isinstance(candidate_name, str) else None
        if shared_name is None:
            failures.append(f"{relative_path}: shared-infra must declare a network name")

    if relative_path == PRODUCTION_COMPOSE:
        private_network = networks.get("orgmemory-internal")
        if not isinstance(private_network, dict) or private_network.get("internal") is not True:
            failures.append(
                f"{relative_path}: orgmemory-internal must remain an internal network"
            )

    if relative_path == OBSERVABILITY_COMPOSE:
        private_network = networks.get("observability-internal")
        if not isinstance(private_network, dict) or private_network.get("external") is True:
            failures.append(
                f"{relative_path}: observability-internal must remain Compose-private"
            )

    proxy_network = networks.get("proxy")
    if not isinstance(proxy_network, dict) or proxy_network.get("external") is not True:
        failures.append(f"{relative_path}: proxy must remain an external network")

    for network_name, expected_services in PRESERVED_NETWORK_MEMBERSHIPS[
        relative_path
    ].items():
        actual_services = network_members(services, network_name)
        if actual_services != expected_services:
            failures.append(
                f"{relative_path}:{network_name}: expected members "
                f"{sorted(expected_services)}, got {sorted(actual_services)}"
            )

    for service_name, service in services.items():
        if not isinstance(service, dict):
            failures.append(f"{relative_path}:{service_name}: invalid service shape")
            continue

        memberships = service.get("networks", {})
        if not isinstance(memberships, dict) or "shared-infra" not in memberships:
            failures.append(
                f"{relative_path}:{service_name}: missing shared-infra DNS membership"
            )

        actual_ports = service.get("ports") or []
        expected_ports = EXPECTED_PORTS[relative_path].get(service_name, [])
        if actual_ports != expected_ports:
            failures.append(
                f"{relative_path}:{service_name}: expected host ports "
                f"{expected_ports}, got {actual_ports}"
            )

        actual_expose = service.get("expose") or []
        expected_expose = EXPECTED_EXPOSE[relative_path].get(service_name, [])
        if actual_expose != expected_expose:
            failures.append(
                f"{relative_path}:{service_name}: expected exposed ports "
                f"{expected_expose}, got {actual_expose}"
            )

    for service_name, expected_name in STABLE_CROSS_STACK_NAMES[relative_path].items():
        service = services.get(service_name)
        if not isinstance(service, dict):
            failures.append(f"{relative_path}:{service_name}: missing required service")
            continue
        memberships = service.get("networks", {})
        shared_membership = (
            memberships.get("shared-infra", {}) if isinstance(memberships, dict) else {}
        )
        aliases = (
            shared_membership.get("aliases", [])
            if isinstance(shared_membership, dict)
            else []
        )
        if service.get("container_name") != expected_name and expected_name not in aliases:
            failures.append(
                f"{relative_path}:{service_name}: missing stable DNS name {expected_name}"
            )

    return failures, len(services), shared_name


def main() -> int:
    failures: list[str] = []
    service_count = 0
    shared_names: dict[Path, str] = {}

    for relative_path, required_networks in COMPOSE_CONTRACTS.items():
        compose_failures, compose_service_count, shared_name = verify(
            relative_path, required_networks
        )
        failures.extend(compose_failures)
        service_count += compose_service_count
        if shared_name is not None:
            shared_names[relative_path] = shared_name

    if len(set(shared_names.values())) > 1:
        rendered_names = ", ".join(
            f"{path}={name}" for path, name in shared_names.items()
        )
        failures.append(
            f"Compose projects resolve shared-infra to different names: {rendered_names}"
        )

    if failures:
        print("Shared Docker DNS contract failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print(
        f"Shared Docker DNS contract passed for {service_count} services "
        f"across {len(COMPOSE_CONTRACTS)} Compose projects."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
