from __future__ import annotations

import argparse
import hashlib
import importlib
import json
import statistics
import tempfile
from collections.abc import Sequence
from datetime import UTC, datetime
from enum import StrEnum
from pathlib import Path
from typing import Annotated, Protocol, runtime_checkable

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    ValidationError,
    model_validator,
)

from orgmemory_eval.official_cases import (
    DEFAULT_OFFICIAL_CASES_PATH,
    AnswerType,
    Difficulty,
    ExpectedPermission,
    OfficialCase,
    OfficialDataset,
    load_official_cases,
)

QuestionId = Annotated[str, StringConstraints(pattern=r"^P\d{3}$")]
UserId = Annotated[str, StringConstraints(pattern=r"^U\d{3}$")]
DocumentId = Annotated[str, StringConstraints(pattern=r"^DOC\d{3}$")]


class SseTerminalEvent(StrEnum):
    FINISH = "finish"
    ERROR = "error"
    ABORT = "abort"


class TranscriptRow(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    question_id: QuestionId
    http_status: int = Field(ge=100, le=599)
    sse_terminal_event: SseTerminalEvent | None
    answer_text: str
    cited_document_ids: list[DocumentId]
    latency_ms: float = Field(ge=0)
    ttft_ms: float | None = Field(default=None, ge=0)
    actor_user_id: UserId

    @model_validator(mode="after")
    def validate_timings_and_citations(self) -> TranscriptRow:
        if len(self.cited_document_ids) != len(set(self.cited_document_ids)):
            raise ValueError("cited_document_ids must be unique")
        if self.ttft_ms is not None and self.ttft_ms > self.latency_ms:
            raise ValueError("ttft_ms must not exceed latency_ms")
        return self

    @property
    def answer_present(self) -> bool:
        return bool(self.answer_text.strip())


class JudgeCriterionAssessment(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    score: float = Field(ge=0.0, le=1.0)
    explanation: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class JudgeAssessment(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    comprehensiveness: JudgeCriterionAssessment
    diversity: JudgeCriterionAssessment
    empowerment: JudgeCriterionAssessment
    overall_score: float = Field(ge=0.0, le=1.0)
    overall_explanation: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


JUDGE_CRITERIA = (
    {
        "key": "comprehensiveness",
        "name": "Comprehensiveness",
        "description": "Coverage of the aspects and details needed to answer the question.",
    },
    {
        "key": "diversity",
        "name": "Diversity",
        "description": "Variety and richness of relevant perspectives or insights.",
    },
    {
        "key": "empowerment",
        "name": "Empowerment",
        "description": "Helpfulness for understanding the topic and making informed judgments.",
    },
)


@runtime_checkable
class OfficialJudge(Protocol):
    name: str
    enabled: bool

    def evaluate(self, case: OfficialCase, transcript: TranscriptRow) -> JudgeAssessment | None: ...


class NoOpOfficialJudge:
    name = "disabled"
    enabled = False

    def evaluate(self, case: OfficialCase, transcript: TranscriptRow) -> JudgeAssessment | None:
        return None


DEFAULT_JUDGE = NoOpOfficialJudge()


def load_transcript(path: Path, dataset: OfficialDataset) -> tuple[list[TranscriptRow], str]:
    raw = path.read_bytes()
    rows: list[TranscriptRow] = []
    for line_number, line in enumerate(raw.splitlines(), start=1):
        if not line.strip():
            raise ValueError(f"transcript line {line_number} must not be blank")
        try:
            rows.append(TranscriptRow.model_validate_json(line))
        except ValidationError as failure:
            raise ValueError(f"invalid transcript line {line_number}: {failure}") from failure

    row_ids = [row.question_id for row in rows]
    if len(row_ids) != len(set(row_ids)):
        raise ValueError("transcript question_id values must be unique")
    expected_ids = set(dataset.by_question_id)
    if set(row_ids) != expected_ids:
        missing = sorted(expected_ids - set(row_ids))
        unexpected = sorted(set(row_ids) - expected_ids)
        raise ValueError(f"transcript cases differ: missing={missing}, unexpected={unexpected}")

    rows_by_id = {row.question_id: row for row in rows}
    ordered_rows = [rows_by_id[case.question_id] for case in dataset.cases]
    for case, row in zip(dataset.cases, ordered_rows, strict=True):
        if row.actor_user_id != case.user_id:
            raise ValueError(
                f"transcript actor mismatch for {case.question_id}: "
                f"expected {case.user_id}, got {row.actor_user_id}"
            )
    return ordered_rows, hashlib.sha256(raw).hexdigest()


def permission_result(case: OfficialCase, row: TranscriptRow) -> dict[str, object]:
    if case.expected_permission == ExpectedPermission.ALLOW:
        passed = (
            row.http_status == 200
            and row.sse_terminal_event == SseTerminalEvent.FINISH
            and row.answer_present
            and row.ttft_ms is not None
        )
        return {
            "verdict": "PASS" if passed else "FAIL",
            "reason": "ALLOW_ANSWERED" if passed else "ALLOW_ANSWER_SIGNAL_MISSING",
            "evidence_leak": False,
        }

    evidence_leak = (
        row.answer_present
        or bool(row.cited_document_ids)
        or row.sse_terminal_event == SseTerminalEvent.FINISH
        or row.ttft_ms is not None
    )
    refused_without_evidence = (
        row.http_status == 403 and row.sse_terminal_event is None and not evidence_leak
    )
    if refused_without_evidence:
        reason = "DENY_REFUSED_NO_EVIDENCE"
    elif evidence_leak:
        reason = "DENY_EVIDENCE_LEAK"
    else:
        reason = "DENY_REFUSAL_SIGNAL_MISSING"
    return {
        "verdict": "PASS" if refused_without_evidence else "FAIL",
        "reason": reason,
        "evidence_leak": evidence_leak,
    }


def citation_result(case: OfficialCase, row: TranscriptRow) -> dict[str, object]:
    expected = set(case.expected_document_ids)
    cited = set(row.cited_document_ids)
    missing = sorted(expected - cited)
    unexpected = sorted(cited - expected)

    if case.expected_permission == ExpectedPermission.DENY:
        verdict = "NOT_APPLICABLE"
    elif cited == expected:
        verdict = "PASS"
    elif not cited:
        verdict = "MISSING"
    elif expected & cited and missing:
        verdict = "PARTIAL"
    elif missing:
        verdict = "WRONG_DOCUMENTS"
    else:
        verdict = "UNEXPECTED_DOCUMENTS"
    return {
        "verdict": verdict,
        "expected_document_ids": sorted(expected),
        "cited_document_ids": sorted(cited),
        "missing_document_ids": missing,
        "unexpected_document_ids": unexpected,
    }


def metric_statistics(values: Sequence[float]) -> dict[str, object]:
    if not values:
        return {
            "sample_count": 0,
            "median_ms": None,
            "observed_max_ms": None,
            "observed_max_label": "max-of-0",
        }
    return {
        "sample_count": len(values),
        "median_ms": statistics.median(values),
        "observed_max_ms": max(values),
        "observed_max_label": f"max-of-{len(values)}",
    }


def latency_statistics(rows: Sequence[TranscriptRow]) -> dict[str, object]:
    return {
        "sample_count": len(rows),
        "latency_ms": metric_statistics([row.latency_ms for row in rows]),
        "ttft_ms": metric_statistics([row.ttft_ms for row in rows if row.ttft_ms is not None]),
    }


def grouped_latency(
    dataset: OfficialDataset,
    rows: Sequence[TranscriptRow],
    *,
    field: str,
    values: Sequence[Difficulty | AnswerType],
) -> dict[str, object]:
    paired = list(zip(dataset.cases, rows, strict=True))
    return {
        value.value: latency_statistics(
            [row for case, row in paired if getattr(case, field) == value]
        )
        for value in values
    }


def judge_summary(
    judge: OfficialJudge,
    assessments: Sequence[JudgeAssessment],
) -> dict[str, object]:
    summary: dict[str, object] = {
        "enabled": judge.enabled,
        "name": judge.name,
        "criteria": list(JUDGE_CRITERIA),
        "evaluated_case_count": len(assessments),
    }
    if assessments:
        summary["mean_scores"] = {
            "comprehensiveness": statistics.fmean(
                result.comprehensiveness.score for result in assessments
            ),
            "diversity": statistics.fmean(result.diversity.score for result in assessments),
            "empowerment": statistics.fmean(result.empowerment.score for result in assessments),
            "overall": statistics.fmean(result.overall_score for result in assessments),
        }
    return summary


def score_official_transcript(
    dataset: OfficialDataset,
    rows: Sequence[TranscriptRow],
    *,
    official_cases_sha256: str,
    transcript_sha256: str,
    judge: OfficialJudge = DEFAULT_JUDGE,
) -> dict[str, object]:
    if len(rows) != len(dataset.cases):
        raise ValueError("scoring requires exactly one transcript row per official case")

    case_results: list[dict[str, object]] = []
    assessments: list[JudgeAssessment] = []
    for case, row in zip(dataset.cases, rows, strict=True):
        if case.question_id != row.question_id:
            raise ValueError("transcript rows must follow official case order")
        permission = permission_result(case, row)
        citation = citation_result(case, row)
        assessment = None
        if (
            judge.enabled
            and case.expected_permission == ExpectedPermission.ALLOW
            and permission["verdict"] == "PASS"
        ):
            assessment = judge.evaluate(case, row)
            if assessment is None:
                raise ValueError("an enabled judge must return an assessment")
            assessments.append(assessment)
        case_results.append(
            {
                "question_id": case.question_id,
                "expected_permission": case.expected_permission.value,
                "answer_type": case.answer_type.value,
                "difficulty": case.difficulty.value,
                "http_status": row.http_status,
                "sse_terminal_event": (
                    row.sse_terminal_event.value if row.sse_terminal_event is not None else None
                ),
                "permission": permission,
                "citation": citation,
                "latency_ms": row.latency_ms,
                "ttft_ms": row.ttft_ms,
                "judge": assessment.model_dump(mode="json") if assessment is not None else None,
            }
        )

    permission_passes = sum(result["permission"]["verdict"] == "PASS" for result in case_results)
    allow_results = [
        result
        for result in case_results
        if result["expected_permission"] == ExpectedPermission.ALLOW.value
    ]
    citation_passes = sum(result["citation"]["verdict"] == "PASS" for result in allow_results)
    deny_leaks = sum(
        result["permission"]["reason"] == "DENY_EVIDENCE_LEAK" for result in case_results
    )
    permission_total = len(case_results)
    citation_total = len(allow_results)
    return {
        "schema_version": "orgmemory.official-evaluation-report.v1",
        "generated_at": datetime.now(UTC).isoformat(),
        "official_source": {
            "path": "demo/fixtures/public-evaluation.json",
            "sha256": official_cases_sha256,
            "case_count": len(dataset.cases),
            "allow_case_count": len(dataset.allow_cases),
            "deny_case_count": len(dataset.deny_cases),
        },
        "transcript": {
            "schema_version": "orgmemory.official-transcript.v1",
            "sha256": transcript_sha256,
            "case_count": len(rows),
        },
        "permission": {
            "passed": permission_passes,
            "failed": permission_total - permission_passes,
            "total": permission_total,
            "deny_evidence_leaks": deny_leaks,
        },
        "citation": {
            "passed": citation_passes,
            "failed": citation_total - citation_passes,
            "total_allow_cases": citation_total,
            "partial": sum(result["citation"]["verdict"] == "PARTIAL" for result in allow_results),
        },
        "latency": {
            "labels": "median and observed max-of-N; no percentile estimate",
            "overall": latency_statistics(rows),
            "by_difficulty": grouped_latency(
                dataset,
                rows,
                field="difficulty",
                values=list(Difficulty),
            ),
            "by_answer_type": grouped_latency(
                dataset,
                rows,
                field="answer_type",
                values=list(AnswerType),
            ),
        },
        "judge": judge_summary(judge, assessments),
        "offline_gate_passed": (
            permission_passes == permission_total and citation_passes == citation_total
        ),
        "cases": case_results,
    }


def load_judge_plugin(specification: str) -> OfficialJudge:
    module_name, separator, attribute_name = specification.partition(":")
    if not separator or not module_name or not attribute_name:
        raise ValueError("judge plugin must use module:factory syntax")
    factory = getattr(importlib.import_module(module_name), attribute_name)
    judge = factory()
    if not isinstance(judge, OfficialJudge):
        raise TypeError("judge plugin must implement OfficialJudge")
    if not judge.enabled:
        raise ValueError("an explicitly configured judge plugin must be enabled")
    return judge


def write_json_atomically(path: Path, payload: dict[str, object]) -> None:
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


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Score an OrgMemory official 50-case production transcript offline.",
    )
    parser.add_argument("--transcript", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--judge-plugin",
        help=(
            "Optional module:factory implementing OfficialJudge; omitted means deterministic no-op."
        ),
    )
    args = parser.parse_args(argv)
    if args.transcript.resolve() == args.output.resolve():
        parser.error("--output must not overwrite --transcript")
    return args


def main(argv: Sequence[str] | None = None) -> None:
    args = parse_args(argv)
    dataset, official_sha256 = load_official_cases(DEFAULT_OFFICIAL_CASES_PATH)
    rows, transcript_sha256 = load_transcript(args.transcript, dataset)
    judge = load_judge_plugin(args.judge_plugin) if args.judge_plugin else DEFAULT_JUDGE
    write_json_atomically(
        args.output,
        score_official_transcript(
            dataset,
            rows,
            official_cases_sha256=official_sha256,
            transcript_sha256=transcript_sha256,
            judge=judge,
        ),
    )


if __name__ == "__main__":
    main()
