from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("check_public_docs_impact.py")
SPEC = importlib.util.spec_from_file_location("check_public_docs_impact", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class PublicDocsImpactTests(unittest.TestCase):
    def test_reference_matches_file_and_directory(self) -> None:
        self.assertTrue(MODULE.reference_matches_change("contracts/openapi.json", "contracts/openapi.json"))
        self.assertTrue(MODULE.reference_matches_change("apps/web", "apps/web/src/app.tsx"))
        self.assertFalse(MODULE.reference_matches_change("apps/web", "apps/worker/App.java"))

    def test_reads_english_and_vietnamese_pages(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / "apps/docs/content/docs/example"
            directory.mkdir(parents=True)
            content = """---
title: Example
sourceRefs:
  - apps/web
---
Body
"""
            (directory / "index.mdx").write_text(content, encoding="utf-8")
            (directory / "index.vi.mdx").write_text(content, encoding="utf-8")

            pages = MODULE.read_pages(root)

            self.assertEqual(["en", "vi"], [page.locale for page in pages])

    def test_report_marks_impacted_page_stale_without_blocking(self) -> None:
        page = MODULE.Page(
            "apps/docs/content/docs/example/index.mdx",
            "Example",
            "en",
            ("apps/web",),
        )

        report, stale_count = MODULE.render_report([page], {"apps/web/src/app.tsx"})

        self.assertEqual(1, stale_count)
        self.assertIn("| en | no | missing |", report)


if __name__ == "__main__":
    unittest.main()
