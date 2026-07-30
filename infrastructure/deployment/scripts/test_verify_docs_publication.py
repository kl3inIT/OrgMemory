#!/usr/bin/env python3
"""Focused contracts for the public docs publication verifier."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("verify-docs-publication.py")
SPEC = importlib.util.spec_from_file_location("verify_docs_publication", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Cannot load publication verifier from {MODULE_PATH}")
VERIFIER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFIER)


class PublicationVerifierTests(unittest.TestCase):
    def test_expands_each_english_route_to_one_vietnamese_representation(self) -> None:
        routes = {
            "/docs/getting-started",
            "/vi/docs/getting-started",
            "/docs/reference/api-reference",
        }

        self.assertEqual(
            VERIFIER.expand_localized_routes(routes),
            {
                "/docs/getting-started",
                "/vi/docs/getting-started",
                "/docs/reference/api-reference",
                "/vi/docs/reference/api-reference",
            },
        )

    def test_reads_both_document_locales_and_ignores_non_docs_urls(self) -> None:
        sitemap = b"""<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>https://docs.example/docs/getting-started</loc></url>
  <url><loc>https://docs.example/vi/docs/getting-started</loc></url>
  <url><loc>https://docs.example/healthz</loc></url>
</urlset>
"""

        self.assertEqual(
            VERIFIER.read_sitemap_routes(sitemap),
            {
                "/docs/getting-started",
                "/vi/docs/getting-started",
            },
        )


if __name__ == "__main__":
    unittest.main()
