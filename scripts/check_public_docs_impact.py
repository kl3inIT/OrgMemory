#!/usr/bin/env python3
"""Report public Fumadocs pages whose repository evidence changed."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTENT_ROOT = ROOT / "apps/docs/content/docs"
FRONTMATTER = re.compile(r"^---\r?\n(?P<body>.*?)\r?\n---", re.DOTALL)
TITLE = re.compile(r"^title:\s*(?P<value>.+?)\s*$", re.MULTILINE)
SOURCE_REFS = re.compile(
    r"^sourceRefs:\s*$\r?\n(?P<items>(?:\s+-\s+[^\r\n]+\r?\n?)+)",
    re.MULTILINE,
)
SOURCE_ITEM = re.compile(r"^\s+-\s+(?P<value>[^\r\n]+)\s*$", re.MULTILINE)


@dataclass(frozen=True)
class Page:
    path: str
    title: str
    locale: str
    source_refs: tuple[str, ...]


def git(root: Path, *arguments: str) -> list[str]:
    result = subprocess.run(
        ["git", *arguments],
        cwd=root,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return result.stdout.splitlines()


def clean_scalar(value: str) -> str:
    return value.strip().strip("'\"")


def read_page(root: Path, path: Path) -> Page:
    relative = path.relative_to(root).as_posix()
    raw = path.read_text(encoding="utf-8")
    frontmatter = FRONTMATTER.match(raw)
    if not frontmatter:
        raise ValueError(f"{relative}: missing frontmatter")
    body = frontmatter.group("body")
    title = TITLE.search(body)
    sources = SOURCE_REFS.search(body)
    if not title or not sources:
        raise ValueError(f"{relative}: missing title or sourceRefs")
    locale = "vi" if path.name.endswith(".vi.mdx") else "en"
    source_refs = tuple(
        clean_scalar(match.group("value"))
        for match in SOURCE_ITEM.finditer(sources.group("items"))
    )
    return Page(relative, clean_scalar(title.group("value")), locale, source_refs)


def read_pages(root: Path) -> list[Page]:
    return [read_page(root, path) for path in sorted((root / "apps/docs/content/docs").rglob("*.mdx"))]


def reference_matches_change(reference: str, changed_path: str) -> bool:
    normalized = reference.rstrip("/")
    return changed_path == normalized or changed_path.startswith(f"{normalized}/")


def impacted_pages(pages: list[Page], changed: set[str]) -> list[Page]:
    return [
        page
        for page in pages
        if any(
            reference_matches_change(reference, changed_path)
            for reference in page.source_refs
            for changed_path in changed
        )
    ]


def changed_paths(root: Path, base_ref: str, head_ref: str) -> set[str]:
    committed = git(root, "diff", "--name-only", base_ref, head_ref)
    working = git(root, "diff", "--name-only", head_ref)
    untracked = git(root, "ls-files", "--others", "--exclude-standard")
    return {path for path in [*committed, *working, *untracked] if path}


def counterpart_path(path: str, locale: str) -> str:
    if locale == "vi":
        return path.removesuffix(".vi.mdx") + ".mdx"
    return path.removesuffix(".mdx") + ".vi.mdx"


def render_report(pages: list[Page], changed: set[str]) -> tuple[str, int]:
    impacted = impacted_pages(pages, changed)
    changed_docs = {path for path in changed if path.startswith("apps/docs/")}
    stale = [page for page in impacted if page.path not in changed_docs]
    known_paths = {page.path for page in pages}

    lines = ["## Public docs impact", ""]
    if not impacted:
        lines.append("No existing Fumadocs page declares a changed source reference.")
    else:
        lines.extend(
            [
                "| Page | Locale | Updated in change | Translation pair |",
                "| --- | --- | --- | --- |",
            ]
        )
        for page in impacted:
            pair = counterpart_path(page.path, page.locale)
            lines.append(
                f"| `{page.path}` | {page.locale} | "
                f"{'yes' if page.path in changed_docs else 'no'} | "
                f"{'present' if pair in known_paths else 'missing'} |"
            )

    unmapped = sorted(
        path
        for path in changed
        if not path.startswith(("apps/docs/", "docs/increments/"))
        and not any(
            reference_matches_change(reference, path)
            for page in pages
            for reference in page.source_refs
        )
    )
    if unmapped:
        lines.extend(
            [
                "",
                "### Changed paths not mapped by public `sourceRefs`",
                "",
                *[f"- `{path}`" for path in unmapped],
            ]
        )

    lines.extend(
        [
            "",
            "This report detects likely drift; product meaning still requires review. "
            "Update the relevant English/Vietnamese pages when reader-visible behavior changed.",
        ]
    )
    return "\n".join(lines), len(stale)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-ref", required=True)
    parser.add_argument("--head-ref", default="HEAD")
    parser.add_argument("--fail-on-stale", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        changed = changed_paths(ROOT, args.base_ref, args.head_ref)
        pages = read_pages(ROOT)
        report, stale_count = render_report(pages, changed)
    except (OSError, ValueError, subprocess.CalledProcessError) as failure:
        print(f"Public docs impact check failed: {failure}", file=sys.stderr)
        return 2
    print(report)
    return 1 if args.fail_on_stale and stale_count else 0


if __name__ == "__main__":
    raise SystemExit(main())
