import math

import pytest
from pydantic import ValidationError

from orgmemory_eval.models import EvaluationDataset


def dataset() -> dict:
    return {
        "schema_version": "orgmemory.rag-evaluation.v1",
        "dataset_id": "employee-policy-v1",
        "system_under_test": "orgmemory-local",
        "cases": [
            {
                "case_id": "probation-policy",
                "question": "What is the probation policy?",
                "reference_answer": "The probation period is 60 days.",
                "answer": "The probation period is 60 days. [1]",
                "contexts": ["The standard probation period is 60 days."],
                "citation_ids": ["citation-1"],
                "latency_ms": 120.5,
            }
        ],
    }


def test_accepts_a_complete_dataset() -> None:
    parsed = EvaluationDataset.model_validate(dataset())

    assert parsed.cases[0].case_id == "probation-policy"


def test_rejects_duplicate_case_ids() -> None:
    payload = dataset()
    payload["cases"].append(payload["cases"][0].copy())

    with pytest.raises(ValidationError, match="case_id values must be unique"):
        EvaluationDataset.model_validate(payload)


def test_rejects_blank_contexts() -> None:
    payload = dataset()
    payload["cases"][0]["contexts"] = [" "]

    with pytest.raises(ValidationError, match="string_too_short"):
        EvaluationDataset.model_validate(payload)


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("case_id", " "),
        ("question", "\t"),
        ("reference_answer", "\n"),
        ("answer", "  "),
    ],
)
def test_rejects_blank_semantic_case_fields(field: str, value: str) -> None:
    payload = dataset()
    payload["cases"][0][field] = value

    with pytest.raises(ValidationError):
        EvaluationDataset.model_validate(payload)


def test_rejects_blank_citation_ids() -> None:
    payload = dataset()
    payload["cases"][0]["citation_ids"] = [" "]

    with pytest.raises(ValidationError):
        EvaluationDataset.model_validate(payload)


@pytest.mark.parametrize("latency", [math.nan, math.inf, -math.inf])
def test_rejects_non_finite_latency(latency: float) -> None:
    payload = dataset()
    payload["cases"][0]["latency_ms"] = latency

    with pytest.raises(ValidationError):
        EvaluationDataset.model_validate(payload)
