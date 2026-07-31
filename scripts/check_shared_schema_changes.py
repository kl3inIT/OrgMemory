#!/usr/bin/env python3
"""Check shared-development Flyway/OpenFGA changes without blocking normal work."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MIGRATION_DIRECTORY = Path("core/src/main/resources/db/migration")
OPENFGA_MODEL = Path("integrations/authorization-openfga/src/main/openfga/model.fga")
PROTECTED_PATHS = (MIGRATION_DIRECTORY, OPENFGA_MODEL)
MIGRATION_NAME = re.compile(r"^V(?P<version>[0-9]+)__(?P<description>[a-z0-9_]+)\.sql$")


def git(root: Path, *arguments: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *arguments],
        cwd=root,
        check=check,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )


def migration_inventory(root: Path) -> tuple[dict[int, Path], list[str]]:
    migrations: dict[int, Path] = {}
    errors: list[str] = []
    for path in sorted((root / MIGRATION_DIRECTORY).glob("*.sql")):
        match = MIGRATION_NAME.fullmatch(path.name)
        if not match:
            errors.append(f"misnamed migration: {path.relative_to(root).as_posix()}")
            continue
        version = int(match.group("version"))
        previous = migrations.get(version)
        if previous is not None:
            errors.append(
                "duplicate Flyway version "
                f"V{version}: {previous.name} and {path.name}"
            )
            continue
        migrations[version] = path
    return migrations, errors


def base_migration_paths(root: Path, base_ref: str) -> list[str]:
    result = git(
        root,
        "ls-tree",
        "-r",
        "--name-only",
        base_ref,
        "--",
        MIGRATION_DIRECTORY.as_posix(),
    )
    return [line for line in result.stdout.splitlines() if line.endswith(".sql")]


def immutable_migration_errors(root: Path, base_ref: str) -> list[str]:
    errors: list[str] = []
    for relative in base_migration_paths(root, base_ref):
        current = root / relative
        if not current.is_file():
            errors.append(f"migration already on {base_ref} was deleted: {relative}")
            continue
        base_bytes = subprocess.run(
            ["git", "show", f"{base_ref}:{relative}"],
            cwd=root,
            check=True,
            capture_output=True,
        ).stdout
        if current.read_bytes() != base_bytes:
            errors.append(f"migration already on {base_ref} was modified: {relative}")
    return errors


def changed_protected_paths(root: Path, base_ref: str, head_ref: str) -> list[str]:
    path_args = [path.as_posix() for path in PROTECTED_PATHS]
    committed = git(
        root,
        "diff",
        "--name-only",
        base_ref,
        head_ref,
        "--",
        *path_args,
    ).stdout.splitlines()
    working = git(
        root,
        "diff",
        "--name-only",
        head_ref,
        "--",
        *path_args,
    ).stdout.splitlines()
    untracked = git(
        root,
        "ls-files",
        "--others",
        "--exclude-standard",
        "--",
        *path_args,
    ).stdout.splitlines()
    return sorted({*committed, *working, *untracked})


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-ref", default="origin/main")
    parser.add_argument("--head-ref", default="HEAD")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        git(ROOT, "rev-parse", "--verify", f"{args.base_ref}^{{commit}}")
        git(ROOT, "rev-parse", "--verify", f"{args.head_ref}^{{commit}}")
    except subprocess.CalledProcessError as failure:
        print(f"Cannot resolve schema comparison ref: {failure.stderr.strip()}", file=sys.stderr)
        return 2

    _, errors = migration_inventory(ROOT)
    errors.extend(immutable_migration_errors(ROOT, args.base_ref))
    if errors:
        print("Shared-schema check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        print(
            "Update from origin/main, give the unapplied migration the next version, "
            "and never edit a migration already on main.",
            file=sys.stderr,
        )
        return 1

    changed = changed_protected_paths(ROOT, args.base_ref, args.head_ref)
    if changed:
        print("Shared-schema change detected (warning only):")
        for path in changed:
            print(f"- {path}")
        print(
            "Normal shared-ZM startup keeps Flyway and model rollout disabled. "
            "Validate the change in disposable tests, resolve conflicts with main, "
            "and let the post-merge deployment update shared ZM once."
        )
    else:
        print("No Flyway or OpenFGA model changes detected.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
