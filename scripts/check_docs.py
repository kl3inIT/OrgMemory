#!/usr/bin/env python3
"""Validate OrgMemory's repository documentation operating model."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
IGNORED_PARTS = {
    ".git",
    ".gradle",
    ".idea",
    ".tools",
    "build",
    "dist",
    "node_modules",
    "output",
    "test-results",
    "tmp",
}
LINK_PATTERN = re.compile(r"!?\[[^\]]*]\(([^)]+)\)")
PROVENANCE_PATTERN = re.compile(
    r"Reconciled:\s+`[^`]+\s+\(([0-9a-f]{7,40})\)`\."
)
CONFLICT_MARKERS = ("<<<<<<< ", "=======", ">>>>>>> ")


def repository_markdown() -> list[Path]:
    return sorted(
        path
        for path in ROOT.rglob("*.md")
        if not any(part in IGNORED_PARTS for part in path.relative_to(ROOT).parts)
    )


def normalize_link_target(raw_target: str) -> str:
    target = raw_target.strip()
    if target.startswith("<") and ">" in target:
        return target[1 : target.index(">")]
    if " " in target:
        target = target.split(" ", 1)[0]
    return target


def check_links(files: list[Path], errors: list[str]) -> None:
    for markdown in files:
        text = markdown.read_text(encoding="utf-8")
        for match in LINK_PATTERN.finditer(text):
            target = normalize_link_target(match.group(1))
            if (
                not target
                or target.startswith(("#", "/", "http://", "https://", "mailto:"))
                or "://" in target
            ):
                continue
            path_part = unquote(target.split("#", 1)[0])
            if not path_part:
                continue
            resolved = (markdown.parent / path_part).resolve()
            try:
                resolved.relative_to(ROOT)
            except ValueError:
                errors.append(
                    f"{markdown.relative_to(ROOT)}: link escapes repository: {target}"
                )
                continue
            if not resolved.exists():
                errors.append(
                    f"{markdown.relative_to(ROOT)}: missing local link target: {target}"
                )


def check_increment_shape(errors: list[str]) -> None:
    active = ROOT / "docs" / "increments" / "active"
    for increment in sorted(path for path in active.iterdir() if path.is_dir()):
        for required in ("design.md", "plan.md"):
            if not (increment / required).is_file():
                errors.append(
                    f"{increment.relative_to(ROOT)}: missing required {required}"
                )


def check_domain_pairs(errors: list[str]) -> None:
    specs_dir = ROOT / "docs" / "specs" / "domains"
    tests_dir = ROOT / "docs" / "tests" / "domains"
    specs = {path.name: path for path in specs_dir.glob("*.md")}
    tests = {path.name: path for path in tests_dir.glob("*.md")}

    for name in sorted(specs.keys() - tests.keys()):
        errors.append(f"docs/tests/domains/{name}: missing mirror for domain spec")
    for name in sorted(tests.keys() - specs.keys()):
        errors.append(f"docs/specs/domains/{name}: missing mirror for test matrix")

    for document in sorted([*specs.values(), *tests.values()]):
        text = document.read_text(encoding="utf-8")
        prefix = "\n".join(text.splitlines()[:20])
        if "Source:" not in prefix:
            errors.append(f"{document.relative_to(ROOT)}: missing Source provenance")
        match = PROVENANCE_PATTERN.search(prefix)
        if not match:
            errors.append(
                f"{document.relative_to(ROOT)}: missing valid Reconciled provenance"
            )


def check_conflict_markers(files: list[Path], errors: list[str]) -> None:
    for markdown in files:
        for line_number, line in enumerate(
            markdown.read_text(encoding="utf-8").splitlines(), start=1
        ):
            if line.startswith(CONFLICT_MARKERS):
                errors.append(
                    f"{markdown.relative_to(ROOT)}:{line_number}: merge marker present"
                )


def main() -> int:
    errors: list[str] = []
    files = repository_markdown()
    check_links(files, errors)
    check_increment_shape(errors)
    check_domain_pairs(errors)
    check_conflict_markers(files, errors)

    if errors:
        print("Documentation operating-model check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    domain_count = len(list((ROOT / "docs" / "specs" / "domains").glob("*.md")))
    print(
        "Documentation operating-model check passed "
        f"({len(files)} Markdown files, {domain_count} mirrored domain pairs)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
