#!/usr/bin/env python3
"""Validate canonical repository skills and Claude/Codex discovery wrappers."""

from __future__ import annotations

from pathlib import Path
import re
import sys
from typing import NoReturn

ROOT = Path(__file__).resolve().parents[1]
CANONICAL_ROOT = ROOT / ".agents" / "skills"
WRAPPER_ROOTS = (ROOT / ".claude" / "skills", ROOT / ".codex" / "skills")
NAME_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")


def parse_frontmatter(path: Path) -> tuple[dict[str, str], str]:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines or lines[0] != "---":
        raise ValueError("missing opening YAML frontmatter delimiter")
    try:
        end = lines.index("---", 1)
    except ValueError as error:
        raise ValueError("missing closing YAML frontmatter delimiter") from error

    metadata: dict[str, str] = {}
    for line in lines[1:end]:
        if not line.strip() or line[:1].isspace():
            continue
        if ":" not in line:
            raise ValueError(f"unsupported top-level frontmatter line: {line!r}")
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip()
        if not key or key in metadata:
            raise ValueError(f"invalid frontmatter field: {line!r}")
        # Nested mappings and optional metadata are allowed; governance needs
        # only the top-level name and description scalars.
        if value:
            metadata[key] = value
    return metadata, "\n".join(lines[end + 1 :]).strip()


def fail(message: str) -> NoReturn:
    print(f"agent-skill validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    canonical_files = sorted(CANONICAL_ROOT.glob("*/SKILL.md"))
    if not canonical_files:
        fail("no canonical skills found")

    canonical: dict[str, tuple[str, Path]] = {}
    for path in canonical_files:
        name = path.parent.name
        try:
            metadata, body = parse_frontmatter(path)
        except ValueError as error:
            fail(f"{path.relative_to(ROOT)}: {error}")
        if metadata.get("name") != name or not NAME_PATTERN.fullmatch(name):
            fail(f"{path.relative_to(ROOT)}: name must equal kebab-case directory {name!r}")
        description = metadata.get("description", "")
        if not description:
            fail(f"{path.relative_to(ROOT)}: description is required")
        if not body:
            fail(f"{path.relative_to(ROOT)}: body is required")
        canonical[name] = (description, path)

    expected_names = set(canonical)
    wrappers_checked = 0
    peer_descriptions: dict[str, tuple[str, Path]] = {}
    for wrapper_root in WRAPPER_ROOTS:
        wrapper_files = sorted(wrapper_root.glob("*/SKILL.md"))
        wrapper_names = {path.parent.name for path in wrapper_files}
        missing = sorted(expected_names - wrapper_names)
        extra = sorted(wrapper_names - expected_names)
        if missing or extra:
            fail(
                f"{wrapper_root.relative_to(ROOT)} wrapper set mismatch; "
                f"missing={missing}, extra={extra}"
            )

        for name in sorted(expected_names):
            path = wrapper_root / name / "SKILL.md"
            try:
                metadata, body = parse_frontmatter(path)
            except ValueError as error:
                fail(f"{path.relative_to(ROOT)}: {error}")
            _description, canonical_path = canonical[name]
            description = metadata.get("description", "")
            if metadata.get("name") != name or not description:
                fail(f"{path.relative_to(ROOT)}: wrapper name and description are required")
            if name in peer_descriptions:
                peer_description, peer_path = peer_descriptions[name]
                if description != peer_description:
                    fail(
                        f"{path.relative_to(ROOT)}: description must match peer wrapper "
                        f"{peer_path.relative_to(ROOT)}"
                    )
            else:
                peer_descriptions[name] = (description, path)
            expected_target = f"../../../.agents/skills/{name}/SKILL.md"
            expected_body = (
                "# Canonical project skill\n\n"
                f"Read and follow `{expected_target}` in full."
            )
            if body != expected_body:
                fail(f"{path.relative_to(ROOT)}: wrapper body or target is not canonical")
            if (path.parent / expected_target).resolve() != canonical_path.resolve():
                fail(f"{path.relative_to(ROOT)}: wrapper target does not resolve to the canonical skill")
            wrappers_checked += 1

    print(f"Agent skills valid: {len(canonical)} canonical, {wrappers_checked} wrappers.")


if __name__ == "__main__":
    main()
