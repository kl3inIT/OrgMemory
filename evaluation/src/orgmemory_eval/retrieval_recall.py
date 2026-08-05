from __future__ import annotations

import argparse
import json
from collections.abc import Sequence
from pathlib import Path
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

from orgmemory_eval.official_cases import (
    DEFAULT_OFFICIAL_CASES_PATH,
    OfficialDataset,
    load_official_cases,
)

Identifier = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=256),
]
NonBlankText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
Sha256Hex = Annotated[str, StringConstraints(pattern=r"^[0-9a-f]{64}$")]
OFFICIAL_RECALL_DATASET_ID = "orgmemory-public-evaluation-allow-v1"
RECALL_TOP_K = 40
DIAGNOSTIC_TOP_K = 60


class GoldenCase(BaseModel):
    model_config = ConfigDict(extra="forbid")

    case_id: Identifier
    question: NonBlankText
    golden_document_ids: list[Identifier] = Field(min_length=1)

    @model_validator(mode="after")
    def require_unique_documents(self) -> GoldenCase:
        if len(self.golden_document_ids) != len(set(self.golden_document_ids)):
            raise ValueError("golden_document_ids must be unique")
        return self


class GoldenDataset(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: Annotated[
        str,
        StringConstraints(pattern=r"^orgmemory\.retrieval-recall\.v2$"),
    ]
    dataset_id: Identifier
    official_source_sha256: Sha256Hex
    cases: list[GoldenCase] = Field(min_length=1)

    @model_validator(mode="after")
    def require_unique_cases(self) -> GoldenDataset:
        case_ids = [case.case_id for case in self.cases]
        if len(case_ids) != len(set(case_ids)):
            raise ValueError("case_id values must be unique")
        return self


class RetrievalObservation(BaseModel):
    model_config = ConfigDict(extra="forbid")

    case_id: Identifier
    keyword_seeded_document_ids: list[Identifier]
    bypass_document_ids: list[Identifier]


class ObservationSet(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: Annotated[
        str,
        StringConstraints(pattern=r"^orgmemory\.retrieval-observations\.v2$"),
    ]
    dataset_id: Identifier
    observations: list[RetrievalObservation] = Field(min_length=1)

    @model_validator(mode="after")
    def require_unique_cases(self) -> ObservationSet:
        case_ids = [observation.case_id for observation in self.observations]
        if len(case_ids) != len(set(case_ids)):
            raise ValueError("observation case_id values must be unique")
        return self


def derive_golden_dataset(
    official: OfficialDataset, *, official_source_sha256: str
) -> GoldenDataset:
    return GoldenDataset(
        schema_version="orgmemory.retrieval-recall.v2",
        dataset_id=OFFICIAL_RECALL_DATASET_ID,
        official_source_sha256=official_source_sha256,
        cases=[
            GoldenCase(
                case_id=case.question_id,
                question=case.question_vi,
                golden_document_ids=list(case.expected_document_ids),
            )
            for case in official.allow_cases
        ],
    )


def recall_at_k(golden_document_ids: list[str], retrieved_document_ids: list[str], k: int) -> float:
    if k <= 0:
        raise ValueError("k must be positive")
    golden = set(golden_document_ids)
    retrieved = set(retrieved_document_ids[:k])
    return len(golden & retrieved) / len(golden)


def score(
    golden: GoldenDataset,
    observations: ObservationSet,
    *,
    tolerance_points: float = 2.0,
) -> dict[str, object]:
    if golden.dataset_id != observations.dataset_id:
        raise ValueError("golden and observation dataset_id values differ")
    observation_by_case = {
        observation.case_id: observation for observation in observations.observations
    }
    golden_case_ids = {case.case_id for case in golden.cases}
    if set(observation_by_case) != golden_case_ids:
        missing = sorted(golden_case_ids - set(observation_by_case))
        unexpected = sorted(set(observation_by_case) - golden_case_ids)
        raise ValueError(f"observation cases differ: missing={missing}, unexpected={unexpected}")

    cases: list[dict[str, object]] = []
    for case in golden.cases:
        observation = observation_by_case[case.case_id]
        cases.append(
            {
                "caseId": case.case_id,
                "keywordSeededDocumentRecallAt40": recall_at_k(
                    case.golden_document_ids,
                    observation.keyword_seeded_document_ids,
                    RECALL_TOP_K,
                ),
                "bypassDocumentRecallAt40": recall_at_k(
                    case.golden_document_ids,
                    observation.bypass_document_ids,
                    RECALL_TOP_K,
                ),
                "keywordSeededDocumentRecallAt60": recall_at_k(
                    case.golden_document_ids,
                    observation.keyword_seeded_document_ids,
                    DIAGNOSTIC_TOP_K,
                ),
            }
        )
    keyword_recall = sum(float(case["keywordSeededDocumentRecallAt40"]) for case in cases) / len(
        cases
    )
    bypass_recall = sum(float(case["bypassDocumentRecallAt40"]) for case in cases) / len(cases)
    keyword_recall_60 = sum(float(case["keywordSeededDocumentRecallAt60"]) for case in cases) / len(
        cases
    )
    delta_points = (bypass_recall - keyword_recall) * 100.0
    return {
        "schemaVersion": "orgmemory.retrieval-recall-report.v2",
        "datasetId": golden.dataset_id,
        "officialSourceSha256": golden.official_source_sha256,
        "caseCount": len(cases),
        "topK": RECALL_TOP_K,
        "tolerancePoints": tolerance_points,
        "keywordSeededDocumentRecallAt40": keyword_recall,
        "bypassDocumentRecallAt40": bypass_recall,
        "bypassDeltaPoints": delta_points,
        "keywordSeededDocumentRecallAt60": keyword_recall_60,
        "gatePassed": delta_points >= -tolerance_points,
        "cases": cases,
    }


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Score ADR 0020 document recall derived from the official 50 cases"
    )
    parser.add_argument("--observations", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    official, official_sha256 = load_official_cases(DEFAULT_OFFICIAL_CASES_PATH)
    golden = derive_golden_dataset(official, official_source_sha256=official_sha256)
    observations = ObservationSet.model_validate_json(args.observations.read_text(encoding="utf-8"))
    report = score(golden, observations)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return 0 if report["gatePassed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
