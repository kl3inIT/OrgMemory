from __future__ import annotations

import json
from pathlib import Path

from orgmemory_eval.models import EvaluationDataset

ROOT = Path(__file__).parents[2]


def test_checked_in_oracle_is_pinned_and_complete() -> None:
    payload = json.loads(
        (ROOT / "evaluation/baselines/lightrag-v1.5.4-oracle.json").read_text(
            encoding="utf-8",
        ),
    )

    assert payload["schemaVersion"] == "orgmemory.lightrag-oracle.v1"
    assert (
        payload["upstream"]["commit"]
        == "9a45b64c2ee25b1d806e90db926a8af37480bb16"
    )
    assert len(payload["fixtureSha256"]) == 64
    assert payload["expected"]["chunks"]
    assert payload["expected"]["weightedPolling"]
    assert "\n" in payload["expected"]["entityEmbeddingPayload"]
    assert "\t" in payload["expected"]["relationEmbeddingPayload"]


def test_sample_ragas_fixture_is_strictly_validated() -> None:
    EvaluationDataset.model_validate_json(
        (ROOT / "evaluation/fixtures/sample-evaluation.json").read_bytes(),
    )
