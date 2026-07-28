#!/usr/bin/env python3
"""Verify the live docs route allowlist and publication boundary."""

from __future__ import annotations

import argparse
import json
import re
import ssl
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

CONNECT_AND_REQUEST_TIMEOUT_SECONDS = 15
FORBIDDEN = {
    "sourceRefs metadata": re.compile(rb"sourceRefs"),
    "active increment path": re.compile(rb"docs/increments/active/"),
    "private research path": re.compile(rb"docs/research/"),
    "Windows workspace path": re.compile(rb"\b[A-Z]:\\(?:Users|OrgMemory|apps)\\"),
    "private IPv4 address": re.compile(
        rb"https?://(?:127\.0\.0\.1|10\.\d+\.\d+\.\d+|"
        rb"192\.168\.\d+\.\d+|172\.(?:1[6-9]|2\d|3[01])\.\d+\.\d+)"
    ),
    "secret assignment": re.compile(
        rb"(?i)(?:api[_-]?key|client[_-]?secret|password|private[_-]?key)"
        rb"\s*[:=]\s*[^\s<]{8,}"
    ),
}


def read_routes(path: Path) -> set[str]:
    document = json.loads(path.read_text(encoding="utf-8"))
    return {
        entry["route"]
        for entry in document["entries"]
        if entry.get("status") == "public"
    }


def fetch(url: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "OrgMemory-publication-verifier/1.0"},
    )
    context = ssl.create_default_context()
    with urllib.request.urlopen(
        request,
        timeout=CONNECT_AND_REQUEST_TIMEOUT_SECONDS,
        context=context,
    ) as response:
        if response.status != 200:
            raise RuntimeError(f"{url} returned HTTP {response.status}")
        return response.read()


def scan(url: str, payload: bytes) -> None:
    for label, pattern in FORBIDDEN.items():
        if pattern.search(payload):
            raise RuntimeError(f"{url} exposes {label}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("base_url", help="HTTPS docs origin")
    parser.add_argument(
        "--docs-root",
        type=Path,
        default=Path(__file__).resolve().parents[3] / "apps" / "docs",
    )
    parser.add_argument(
        "--allow-http-local",
        action="store_true",
        help="allow a loopback HTTP origin for local contract testing only",
    )
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    parsed = urllib.parse.urlparse(base_url)
    local_http = (
        args.allow_http_local
        and parsed.scheme == "http"
        and parsed.hostname in {"127.0.0.1", "localhost"}
    )
    if (
        (parsed.scheme != "https" and not local_http)
        or not parsed.netloc
        or parsed.path
    ):
        parser.error("base_url must be an HTTPS origin without a path")

    expected = read_routes(args.docs_root / "public-content.manifest.json")
    expected |= read_routes(args.docs_root / "generated-api.manifest.json")

    sitemap_url = f"{base_url}/sitemap.xml"
    sitemap = fetch(sitemap_url)
    root = ET.fromstring(sitemap)
    namespace = {"sm": "http://www.sitemaps.org/schemas/sitemap/0.9"}
    actual = {
        urllib.parse.urlparse(element.text or "").path
        for element in root.findall("sm:url/sm:loc", namespace)
        if urllib.parse.urlparse(element.text or "").path.startswith("/docs/")
    }
    if actual != expected:
        missing = sorted(expected - actual)
        unexpected = sorted(actual - expected)
        raise RuntimeError(
            f"sitemap route mismatch; missing={missing}, unexpected={unexpected}"
        )

    urls = [f"{base_url}{route}" for route in sorted(expected)]
    urls.extend(
        [
            base_url,
            f"{base_url}/llms.txt",
            f"{base_url}/llms-full.txt",
            f"{base_url}/robots.txt",
            sitemap_url,
        ]
    )
    for url in urls:
        scan(url, fetch(url))

    print(
        f"Public docs verification passed: {len(expected)} allowlisted routes "
        f"and {len(urls) - len(expected)} public outputs"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001
        print(f"Public docs verification failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
