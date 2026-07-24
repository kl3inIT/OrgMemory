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

    with pytest.raises(ValidationError, match="contexts must not contain blank"):
        EvaluationDataset.model_validate(payload)
