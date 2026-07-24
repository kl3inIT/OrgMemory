from __future__ import annotations

import argparse
import ast
import hashlib
import importlib.util
import json
import logging
import subprocess
import sys
import types
from collections.abc import Sequence
from pathlib import Path
from typing import Any

UPSTREAM_COMMIT = "9a45b64c2ee25b1d806e90db926a8af37480bb16"


class CodePointTokenizer:
    def encode(self, content: str) -> list[int]:
        return [ord(character) for character in content]

    def decode(self, tokens: list[int]) -> str:
        return "".join(chr(token) for token in tokens)


class ChunkTokenLimitExceededError(RuntimeError):
    def __init__(self, **details: Any) -> None:
        super().__init__(str(details))


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate a deterministic semantic oracle from pinned LightRAG source.",
    )
    parser.add_argument("--upstream", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args(argv)


def require_pinned_checkout(upstream: Path) -> None:
    actual = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=upstream,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if actual != UPSTREAM_COMMIT:
        raise SystemExit(
            f"LightRAG checkout must be {UPSTREAM_COMMIT}, found {actual}",
        )


def load_upstream_chunker(upstream: Path) -> Any:
    lightrag = types.ModuleType("lightrag")
    lightrag.__path__ = [str(upstream / "lightrag")]
    exceptions = types.ModuleType("lightrag.exceptions")
    exceptions.ChunkTokenLimitExceededError = ChunkTokenLimitExceededError
    utils = types.ModuleType("lightrag.utils")
    utils.Tokenizer = CodePointTokenizer
    utils.logger = logging.getLogger("lightrag-oracle")
    sys.modules["lightrag"] = lightrag
    sys.modules["lightrag.exceptions"] = exceptions
    sys.modules["lightrag.utils"] = utils

    module_path = upstream / "lightrag" / "chunker" / "token_size.py"
    spec = importlib.util.spec_from_file_location(
        "lightrag.chunker.token_size",
        module_path,
    )
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load upstream chunker from {module_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.chunking_by_token_size


def load_upstream_weighted_polling(upstream: Path) -> Any:
    source_path = upstream / "lightrag" / "utils.py"
    tree = ast.parse(source_path.read_text(encoding="utf-8"), filename=str(source_path))
    function = next(
        (
            node
            for node in tree.body
            if isinstance(node, ast.FunctionDef)
            and node.name == "pick_by_weighted_polling"
        ),
        None,
    )
    if function is None:
        raise RuntimeError("Pinned upstream weighted-polling function is missing")
    module = ast.Module(body=[function], type_ignores=[])
    ast.fix_missing_locations(module)
    namespace: dict[str, Any] = {}
    exec(compile(module, str(source_path), "exec"), namespace)
    return namespace["pick_by_weighted_polling"]


def require_embedding_payload_contract(upstream: Path) -> None:
    source = (upstream / "lightrag" / "lightrag.py").read_text(encoding="utf-8")
    required_fragments = (
        '"content": dp["entity_name"] + "\\n" + dp["description"]',
        '"content": f"{dp[\'keywords\']}\\t{dp[\'src_id\']}\\n'
        "{dp['tgt_id']}\\n{dp['description']}\"",
    )
    missing = [fragment for fragment in required_fragments if fragment not in source]
    if missing:
        raise RuntimeError(
            "Pinned upstream embedding payload contract changed: "
            + repr(missing),
        )


def canonical_sha256(payload: Any) -> str:
    encoded = json.dumps(
        payload,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def generate(upstream: Path) -> dict[str, Any]:
    require_pinned_checkout(upstream)
    chunk = load_upstream_chunker(upstream)
    weighted_polling = load_upstream_weighted_polling(upstream)
    require_embedding_payload_contract(upstream)

    fixture = {
        "chunking": {
            "content": "abcdefghij",
            "chunkTokenSize": 4,
            "chunkOverlapTokenSize": 1,
        },
        "weightedPolling": {
            "groups": [
                ["a1", "a2", "a3"],
                ["b1"],
                ["c1", "c2"],
            ],
            "maximumRelatedChunks": 3,
            "minimumRelatedChunks": 1,
        },
        "embeddingPayloads": {
            "entityName": "LEAVE POLICY",
            "entityDescription": "Defines annual leave.",
            "relationKeywords": "governs, leave",
            "relationSource": "EMPLOYEE HANDBOOK",
            "relationTarget": "LEAVE POLICY",
            "relationDescription": "The handbook governs the policy.",
        },
    }
    chunk_input = fixture["chunking"]
    chunks = chunk(
        CodePointTokenizer(),
        chunk_input["content"],
        chunk_overlap_token_size=chunk_input["chunkOverlapTokenSize"],
        chunk_token_size=chunk_input["chunkTokenSize"],
        _emit_source_span=True,
    )
    polling_input = fixture["weightedPolling"]
    selected = weighted_polling(
        [
            {"sorted_chunks": group}
            for group in polling_input["groups"]
        ],
        polling_input["maximumRelatedChunks"],
        polling_input["minimumRelatedChunks"],
    )
    payload_input = fixture["embeddingPayloads"]
    return {
        "schemaVersion": "orgmemory.lightrag-oracle.v1",
        "upstream": {
            "repository": "https://github.com/HKUDS/LightRAG",
            "tag": "v1.5.4",
            "commit": UPSTREAM_COMMIT,
        },
        "fixtureSha256": canonical_sha256(fixture),
        "fixture": fixture,
        "expected": {
            "chunks": chunks,
            "weightedPolling": selected,
            "entityEmbeddingPayload": (
                f"{payload_input['entityName']}\n"
                f"{payload_input['entityDescription']}"
            ),
            "relationEmbeddingPayload": (
                f"{payload_input['relationKeywords']}\t"
                f"{payload_input['relationSource']}\n"
                f"{payload_input['relationTarget']}\n"
                f"{payload_input['relationDescription']}"
            ),
        },
    }


def main(argv: Sequence[str] | None = None) -> None:
    args = parse_args(argv)
    payload = generate(args.upstream.resolve())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


if __name__ == "__main__":
    main()
