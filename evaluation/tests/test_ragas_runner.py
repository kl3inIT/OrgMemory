import asyncio

import pytest
from ragas.embeddings.base import BaseRagasEmbedding
from ragas.llms import InstructorBaseRagasLLM
from ragas.metrics.result import MetricResult

from orgmemory_eval.models import EvaluationDataset
from orgmemory_eval.ragas_runner import (
    METRIC_NAMES,
    metric_suite,
    parse_args,
    run_trial,
    summarize_trials,
    validated_score,
)


class FakeInstructorLlm(InstructorBaseRagasLLM):
    def generate(self, prompt: str, response_model: type):  # noqa: ANN001
        return self._response(response_model)

    async def agenerate(self, prompt: str, response_model: type):  # noqa: ANN001
        return self._response(response_model)

    @staticmethod
    def _response(response_model: type):  # noqa: ANN001
        payloads = {
            "StatementGeneratorOutput": {
                "statements": ["The standard probation period is 60 days."]
            },
            "NLIStatementOutput": {
                "statements": [
                    {
                        "statement": "The standard probation period is 60 days.",
                        "reason": "The context states the same policy.",
                        "verdict": 1,
                    }
                ]
            },
            "AnswerRelevanceOutput": {
                "question": "What is the probation policy?",
                "noncommittal": 0,
            },
            "ContextPrecisionOutput": {
                "reason": "The context directly answers the question.",
                "verdict": 1,
            },
            "ContextRecallOutput": {
                "classifications": [
                    {
                        "statement": "The standard probation period is 60 days.",
                        "reason": "The retrieved context contains the statement.",
                        "attributed": 1,
                    }
                ]
            },
        }
        return response_model.model_validate(payloads[response_model.__name__])


class FakeEmbeddings(BaseRagasEmbedding):
    def embed_text(self, text: str, **kwargs: object) -> list[float]:
        return [1.0, 0.0]

    async def aembed_text(self, text: str, **kwargs: object) -> list[float]:
        return self.embed_text(text, **kwargs)


def evaluation_dataset() -> EvaluationDataset:
    return EvaluationDataset.model_validate(
        {
            "schema_version": "orgmemory.rag-evaluation.v1",
            "dataset_id": "employee-policy-v1",
            "system_under_test": "orgmemory-local",
            "cases": [
                {
                    "case_id": "probation-policy",
                    "question": "What is the probation policy?",
                    "reference_answer": "The standard probation period is 60 days.",
                    "answer": "The standard probation period is 60 days.",
                    "contexts": [
                        "Employee Handbook: The standard probation period is 60 days."
                    ],
                    "citation_ids": ["opaque-citation"],
                    "latency_ms": 80,
                }
            ],
        }
    )


def test_modern_collections_metrics_accept_modern_adapters_and_score() -> None:
    llm = FakeInstructorLlm()
    embeddings = FakeEmbeddings()

    suite = metric_suite(llm=llm, embeddings=embeddings)
    rows = asyncio.run(
        run_trial(
            evaluation_dataset(),
            llm=llm,
            embeddings=embeddings,
            max_workers=2,
            timeout_seconds=5,
        )
    )

    assert suite.faithfulness.name == "faithfulness"
    assert rows[0].keys() == set(METRIC_NAMES)
    assert all(score == pytest.approx(1.0) for score in rows[0].values())


@pytest.mark.parametrize("score", [float("nan"), float("inf"), -0.01, 1.01])
def test_non_finite_or_out_of_range_metric_scores_fail_closed(score: float) -> None:
    with pytest.raises(ValueError):
        validated_score("faithfulness", MetricResult(value=score))


def test_summary_contains_scores_but_no_prompt_or_evidence_text() -> None:
    dataset = evaluation_dataset()
    row = {metric: 0.8 for metric in METRIC_NAMES}

    summary = summarize_trials(
        dataset,
        [[row], [row]],
        dataset_sha256="a" * 64,
        evaluator_model="judge-model",
        embedding_model="embedding-model",
    )

    encoded = str(summary)
    assert summary["trial_count"] == 2
    assert summary["cases"][0]["citation_count"] == 1
    assert "What is the probation policy?" not in encoded
    assert "The standard probation period is 60 days." not in encoded
    assert "Employee Handbook" not in encoded
    assert "opaque-citation" not in encoded


def test_rejects_output_that_resolves_to_input(tmp_path) -> None:
    dataset_path = tmp_path / "dataset.json"

    with pytest.raises(SystemExit):
        parse_args(
            [
                "--input",
                str(dataset_path),
                "--output",
                str(tmp_path / "." / "dataset.json"),
            ]
        )


@pytest.mark.parametrize("timeout_seconds", ["0", "-1"])
def test_rejects_non_positive_timeout(timeout_seconds: str, tmp_path) -> None:
    with pytest.raises(SystemExit):
        parse_args(
            [
                "--input",
                str(tmp_path / "input.json"),
                "--output",
                str(tmp_path / "output.json"),
                "--timeout-seconds",
                timeout_seconds,
            ]
        )
