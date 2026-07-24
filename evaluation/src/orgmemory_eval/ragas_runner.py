from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import math
import os
import statistics
import tempfile
from collections.abc import Awaitable, Callable, Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from importlib.metadata import version
from pathlib import Path
from typing import Any

from openai import AsyncOpenAI, OpenAI
from ragas.embeddings.base import BaseRagasEmbedding, embedding_factory
from ragas.llms import InstructorBaseRagasLLM, llm_factory
from ragas.metrics.collections import (
    AnswerRelevancy,
    ContextPrecisionWithReference,
    ContextRecall,
    Faithfulness,
)
from ragas.metrics.result import MetricResult

from orgmemory_eval.models import EvaluationCase, EvaluationDataset

METRIC_NAMES = (
    "faithfulness",
    "answer_relevancy",
    "context_precision",
    "context_recall",
)


@dataclass(frozen=True)
class MetricSuite:
    faithfulness: Faithfulness
    answer_relevancy: AnswerRelevancy
    context_precision: ContextPrecisionWithReference
    context_recall: ContextRecall


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Evaluate an exported OrgMemory Assistant dataset without retaining prompts.",
    )
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--trials", type=int, default=3)
    parser.add_argument(
        "--evaluator-model",
        default=os.getenv("ORGMEMORY_EVAL_MODEL", "gpt-4.1-mini"),
    )
    parser.add_argument(
        "--embedding-model",
        default=os.getenv("ORGMEMORY_EVAL_EMBEDDING_MODEL", "text-embedding-3-large"),
    )
    parser.add_argument("--max-workers", type=int, default=4)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    args = parser.parse_args(argv)
    if args.trials < 2:
        parser.error("--trials must be at least 2 so judge variance is measurable")
    if args.max_workers < 1:
        parser.error("--max-workers must be positive")
    return args


def load_dataset(path: Path) -> tuple[EvaluationDataset, str]:
    raw = path.read_bytes()
    parsed = EvaluationDataset.model_validate_json(raw)
    return parsed, hashlib.sha256(raw).hexdigest()


def evaluator(
    *,
    llm_client: AsyncOpenAI,
    embedding_client: OpenAI,
    evaluator_model: str,
    embedding_model: str,
) -> tuple[InstructorBaseRagasLLM, BaseRagasEmbedding]:
    llm = llm_factory(evaluator_model, client=llm_client)
    embeddings = embedding_factory(
        "openai",
        model=embedding_model,
        client=embedding_client,
        interface="modern",
    )
    if not isinstance(embeddings, BaseRagasEmbedding):
        raise TypeError("modern embedding_factory returned a legacy embedding adapter")
    return llm, embeddings


def metric_suite(
    *,
    llm: InstructorBaseRagasLLM,
    embeddings: BaseRagasEmbedding,
) -> MetricSuite:
    return MetricSuite(
        faithfulness=Faithfulness(llm=llm),
        answer_relevancy=AnswerRelevancy(llm=llm, embeddings=embeddings),
        context_precision=ContextPrecisionWithReference(llm=llm),
        context_recall=ContextRecall(llm=llm),
    )


def validated_score(metric_name: str, result: MetricResult) -> float:
    score = float(result.value)
    if not math.isfinite(score):
        raise ValueError(f"{metric_name} returned a non-finite score")
    if not 0.0 <= score <= 1.0:
        raise ValueError(f"{metric_name} returned an out-of-range score: {score}")
    return score


async def bounded_score(
    *,
    semaphore: asyncio.Semaphore,
    timeout_seconds: int,
    metric_name: str,
    operation: Callable[[], Awaitable[MetricResult]],
) -> float:
    async with semaphore:
        result = await asyncio.wait_for(operation(), timeout=timeout_seconds)
    return validated_score(metric_name, result)


async def score_case(
    case: EvaluationCase,
    *,
    metrics: MetricSuite,
    semaphore: asyncio.Semaphore,
    timeout_seconds: int,
) -> dict[str, float]:
    scores = await asyncio.gather(
        bounded_score(
            semaphore=semaphore,
            timeout_seconds=timeout_seconds,
            metric_name="faithfulness",
            operation=lambda: metrics.faithfulness.ascore(
                user_input=case.question,
                response=case.answer,
                retrieved_contexts=case.contexts,
            ),
        ),
        bounded_score(
            semaphore=semaphore,
            timeout_seconds=timeout_seconds,
            metric_name="answer_relevancy",
            operation=lambda: metrics.answer_relevancy.ascore(
                user_input=case.question,
                response=case.answer,
            ),
        ),
        bounded_score(
            semaphore=semaphore,
            timeout_seconds=timeout_seconds,
            metric_name="context_precision",
            operation=lambda: metrics.context_precision.ascore(
                user_input=case.question,
                reference=case.reference_answer,
                retrieved_contexts=case.contexts,
            ),
        ),
        bounded_score(
            semaphore=semaphore,
            timeout_seconds=timeout_seconds,
            metric_name="context_recall",
            operation=lambda: metrics.context_recall.ascore(
                user_input=case.question,
                retrieved_contexts=case.contexts,
                reference=case.reference_answer,
            ),
        ),
    )
    return dict(zip(METRIC_NAMES, scores, strict=True))


async def run_trial(
    dataset: EvaluationDataset,
    *,
    llm: InstructorBaseRagasLLM,
    embeddings: BaseRagasEmbedding,
    max_workers: int,
    timeout_seconds: int,
) -> list[dict[str, float]]:
    metrics = metric_suite(llm=llm, embeddings=embeddings)
    semaphore = asyncio.Semaphore(max_workers)
    return await asyncio.gather(
        *[
            score_case(
                case,
                metrics=metrics,
                semaphore=semaphore,
                timeout_seconds=timeout_seconds,
            )
            for case in dataset.cases
        ]
    )


async def run_trials(
    dataset: EvaluationDataset,
    *,
    llm: InstructorBaseRagasLLM,
    embeddings: BaseRagasEmbedding,
    trial_count: int,
    max_workers: int,
    timeout_seconds: int,
) -> list[list[dict[str, float]]]:
    trials = []
    for _ in range(trial_count):
        trials.append(
            await run_trial(
                dataset,
                llm=llm,
                embeddings=embeddings,
                max_workers=max_workers,
                timeout_seconds=timeout_seconds,
            )
        )
    return trials


def summarize_trials(
    dataset: EvaluationDataset,
    trials: Sequence[Sequence[dict[str, float]]],
    *,
    dataset_sha256: str,
    evaluator_model: str,
    embedding_model: str,
) -> dict[str, Any]:
    if not trials or any(len(trial) != len(dataset.cases) for trial in trials):
        raise ValueError("every trial must contain one score row per evaluation case")

    case_results = []
    for index, case in enumerate(dataset.cases):
        scores: dict[str, dict[str, float]] = {}
        for metric in METRIC_NAMES:
            values = [float(trial[index][metric]) for trial in trials]
            scores[metric] = {
                "mean": statistics.fmean(values),
                "standard_deviation": statistics.pstdev(values),
                "minimum": min(values),
                "maximum": max(values),
            }
        case_results.append(
            {
                "case_id": case.case_id,
                "latency_ms": case.latency_ms,
                "citation_count": len(case.citation_ids),
                "scores": scores,
            }
        )

    aggregate: dict[str, dict[str, float]] = {}
    for metric in METRIC_NAMES:
        case_means = [case["scores"][metric]["mean"] for case in case_results]
        aggregate[metric] = {
            "mean": statistics.fmean(case_means),
            "minimum_case_mean": min(case_means),
        }

    return {
        "schema_version": "orgmemory.ragas-results.v1",
        "generated_at": datetime.now(UTC).isoformat(),
        "dataset_id": dataset.dataset_id,
        "dataset_sha256": dataset_sha256,
        "system_under_test": dataset.system_under_test,
        "evaluator_model": evaluator_model,
        "embedding_model": embedding_model,
        "ragas_version": version("ragas"),
        "trial_count": len(trials),
        "case_count": len(dataset.cases),
        "aggregate": aggregate,
        "cases": case_results,
    }


def write_json_atomically(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        newline="\n",
        dir=path.parent,
        delete=False,
    ) as temporary:
        temporary.write(encoded)
        temporary_path = Path(temporary.name)
    temporary_path.replace(path)


async def async_main(args: argparse.Namespace, *, api_key: str) -> None:
    dataset, dataset_sha256 = load_dataset(args.input)
    llm_client = AsyncOpenAI(
        api_key=api_key,
        base_url=os.getenv("OPENAI_BASE_URL"),
        timeout=args.timeout_seconds,
        max_retries=3,
    )
    embedding_client = OpenAI(
        api_key=api_key,
        base_url=os.getenv("OPENAI_BASE_URL"),
        timeout=args.timeout_seconds,
        max_retries=3,
    )
    try:
        llm, embeddings = evaluator(
            llm_client=llm_client,
            embedding_client=embedding_client,
            evaluator_model=args.evaluator_model,
            embedding_model=args.embedding_model,
        )
        trials = await run_trials(
            dataset,
            llm=llm,
            embeddings=embeddings,
            trial_count=args.trials,
            max_workers=args.max_workers,
            timeout_seconds=args.timeout_seconds,
        )
    finally:
        await llm_client.close()
        embedding_client.close()

    write_json_atomically(
        args.output,
        summarize_trials(
            dataset,
            trials,
            dataset_sha256=dataset_sha256,
            evaluator_model=args.evaluator_model,
            embedding_model=args.embedding_model,
        ),
    )


def main(argv: Sequence[str] | None = None) -> None:
    args = parse_args(argv)
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise SystemExit("OPENAI_API_KEY is required; the value is never written to results")
    asyncio.run(async_main(args, api_key=api_key))


if __name__ == "__main__":
    main()
