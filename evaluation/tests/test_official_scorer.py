import json
from pathlib import Path
from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from orgmemory_eval.official_cases import ExpectedPermission, OfficialDataset, load_official_cases
from orgmemory_eval.official_scorer import (
    DEFAULT_JUDGE,
    JudgeAssessment,
    JudgeCriterionAssessment,
    SseTerminalEvent,
    TranscriptRow,
    load_judge_plugin,
    load_transcript,
    main,
    score_official_transcript,
)


def official_dataset() -> OfficialDataset:
    dataset, _ = load_official_cases()
    return dataset


def correct_rows(dataset: OfficialDataset) -> list[TranscriptRow]:
    rows = []
    for index, case in enumerate(dataset.cases, start=1):
        allowed = case.expected_permission == ExpectedPermission.ALLOW
        rows.append(
            TranscriptRow(
                question_id=case.question_id,
                http_status=200 if allowed else 403,
                sse_terminal_event=SseTerminalEvent.FINISH if allowed else None,
                answer_text=f"Synthetic answer for {case.question_id}" if allowed else "",
                cited_document_ids=list(case.expected_document_ids) if allowed else [],
                latency_ms=float(index * 10),
                ttft_ms=float(index) if allowed else None,
                actor_user_id=case.user_id,
            )
        )
    return rows


def write_transcript(path: Path, rows: list[TranscriptRow]) -> None:
    path.write_text(
        "\n".join(row.model_dump_json() for row in rows) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def score(rows: list[TranscriptRow], *, judge=DEFAULT_JUDGE) -> dict[str, object]:
    return score_official_transcript(
        official_dataset(),
        rows,
        official_cases_sha256="a" * 64,
        transcript_sha256="b" * 64,
        judge=judge,
    )


def case_result(report: dict[str, object], question_id: str) -> dict[str, object]:
    return next(case for case in report["cases"] if case["question_id"] == question_id)


def replace_row(
    rows: list[TranscriptRow], question_id: str, **changes: object
) -> list[TranscriptRow]:
    return [
        row.model_copy(update=changes) if row.question_id == question_id else row for row in rows
    ]


def test_all_correct_offline_signals_pass_without_running_a_judge() -> None:
    report = score(correct_rows(official_dataset()))

    assert report["permission"] == {
        "passed": 50,
        "failed": 0,
        "total": 50,
        "deny_evidence_leaks": 0,
    }
    assert report["citation"] == {
        "passed": 43,
        "failed": 0,
        "total_allow_cases": 43,
        "partial": 0,
    }
    assert report["judge"]["enabled"] is False
    assert report["judge"]["evaluated_case_count"] == 0
    assert report["offline_gate_passed"] is True
    assert all("answer_text" not in case for case in report["cases"])


def test_reports_allow_wrong_doc_deny_refusal_deny_leak_and_multi_doc_partial() -> None:
    rows = correct_rows(official_dataset())
    rows = replace_row(rows, "P001", cited_document_ids=["DOC040"])
    rows = replace_row(rows, "P031", cited_document_ids=["DOC001"])
    rows = replace_row(
        rows,
        "P007",
        http_status=200,
        sse_terminal_event=SseTerminalEvent.FINISH,
        answer_text="Leaked strategy",
        cited_document_ids=["DOC036"],
        ttft_ms=10.0,
    )

    report = score(rows)

    assert case_result(report, "P001")["permission"]["verdict"] == "PASS"
    assert case_result(report, "P001")["citation"]["verdict"] == "WRONG_DOCUMENTS"
    assert case_result(report, "P009")["permission"] == {
        "verdict": "PASS",
        "reason": "DENY_REFUSED_NO_EVIDENCE",
        "evidence_leak": False,
        "denied_cited_document_ids": [],
        "verbatim_match_document_ids": [],
    }
    assert case_result(report, "P007")["permission"] == {
        "verdict": "FAIL",
        "reason": "DENY_EVIDENCE_LEAK",
        "evidence_leak": True,
        "denied_cited_document_ids": ["DOC036"],
        "verbatim_match_document_ids": [],
    }
    assert case_result(report, "P031")["citation"]["verdict"] == "PARTIAL"
    assert case_result(report, "P031")["citation"]["missing_document_ids"] == ["DOC011"]
    assert report["citation"]["partial"] == 1
    assert report["offline_gate_passed"] is False


def test_deny_polite_no_evidence_passes() -> None:
    rows = replace_row(
        correct_rows(official_dataset()),
        "P007",
        http_status=200,
        sse_terminal_event=SseTerminalEvent.FINISH,
        answer_text="Tôi không tìm thấy thông tin mà bạn có quyền truy cập.",
        cited_document_ids=[],
        ttft_ms=10.0,
    )

    permission = case_result(score(rows), "P007")["permission"]

    assert permission == {
        "verdict": "PASS",
        "reason": "DENY_FINISHED_NO_EVIDENCE",
        "evidence_leak": False,
        "denied_cited_document_ids": [],
        "verbatim_match_document_ids": [],
    }


def test_deny_cites_denied_doc_is_evidence_leak() -> None:
    rows = replace_row(
        correct_rows(official_dataset()),
        "P007",
        http_status=200,
        sse_terminal_event=SseTerminalEvent.FINISH,
        answer_text="Tôi không tìm thấy thông tin mà bạn có quyền truy cập.",
        cited_document_ids=["DOC036"],
        ttft_ms=10.0,
    )

    permission = case_result(score(rows), "P007")["permission"]

    assert permission == {
        "verdict": "FAIL",
        "reason": "DENY_EVIDENCE_LEAK",
        "evidence_leak": True,
        "denied_cited_document_ids": ["DOC036"],
        "verbatim_match_document_ids": [],
    }


def test_deny_verbatim_content_without_citation_is_evidence_leak() -> None:
    rows = replace_row(
        correct_rows(official_dataset()),
        "P007",
        http_status=200,
        sse_terminal_event=SseTerminalEvent.FINISH,
        answer_text=(
            "Ưu tiên chiến lược năm 2026 gồm mở rộng hệ sinh thái số, "
            "tăng trưởng dịch vụ giá trị gia tăng."
        ),
        cited_document_ids=[],
        ttft_ms=10.0,
    )

    permission = case_result(score(rows), "P007")["permission"]

    assert permission == {
        "verdict": "FAIL",
        "reason": "DENY_EVIDENCE_LEAK",
        "evidence_leak": True,
        "denied_cited_document_ids": [],
        "verbatim_match_document_ids": ["DOC036"],
    }


def test_multi_document_case_requires_both_expected_documents() -> None:
    report = score(correct_rows(official_dataset()))

    citation = case_result(report, "P031")["citation"]
    assert citation["verdict"] == "PASS"
    assert citation["cited_document_ids"] == ["DOC001", "DOC011"]


def test_latency_uses_median_and_observed_max_of_n_labels() -> None:
    report = score(correct_rows(official_dataset()))

    assert report["latency"]["labels"] == ("median and observed max-of-N; no percentile estimate")
    overall = report["latency"]["overall"]
    assert overall["latency_ms"] == {
        "sample_count": 50,
        "median_ms": 255.0,
        "observed_max_ms": 500.0,
        "observed_max_label": "max-of-50",
    }
    assert report["latency"]["by_difficulty"]["Hard"]["sample_count"] == 2
    assert "p95" not in str(report).lower()


def test_transcript_loader_requires_complete_cases_and_matching_actor(tmp_path: Path) -> None:
    dataset = official_dataset()
    rows = correct_rows(dataset)
    rows[0] = rows[0].model_copy(update={"actor_user_id": "U032"})
    transcript = tmp_path / "actor-mismatch.jsonl"
    write_transcript(transcript, rows)

    with pytest.raises(ValueError, match="actor mismatch for P001"):
        load_transcript(transcript, dataset)

    write_transcript(transcript, correct_rows(dataset)[:-1])
    with pytest.raises(ValueError, match="transcript cases differ"):
        load_transcript(transcript, dataset)


def test_transcript_row_rejects_ttft_after_completion() -> None:
    with pytest.raises(ValidationError, match="ttft_ms must not exceed latency_ms"):
        TranscriptRow(
            question_id="P001",
            http_status=200,
            sse_terminal_event=SseTerminalEvent.FINISH,
            answer_text="answer",
            cited_document_ids=["DOC001"],
            latency_ms=10,
            ttft_ms=11,
            actor_user_id="U001",
        )


class FakeJudge:
    name = "offline-fake"
    enabled = True

    def __init__(self) -> None:
        self.seen: list[str] = []

    def evaluate(self, case, transcript) -> JudgeAssessment:
        self.seen.append(case.question_id)
        criterion = JudgeCriterionAssessment(score=0.75, explanation="Synthetic fixture")
        return JudgeAssessment(
            comprehensiveness=criterion,
            diversity=criterion,
            empowerment=criterion,
            overall_score=0.75,
            overall_explanation="Synthetic fixture",
        )


def test_enabled_judge_hook_uses_lightrag_style_criteria_for_allow_answers_only() -> None:
    judge = FakeJudge()

    report = score(correct_rows(official_dataset()), judge=judge)

    assert len(judge.seen) == 43
    assert report["judge"]["name"] == "offline-fake"
    assert [criterion["key"] for criterion in report["judge"]["criteria"]] == [
        "comprehensiveness",
        "diversity",
        "empowerment",
    ]
    assert report["judge"]["mean_scores"]["overall"] == 0.75
    assert case_result(report, "P007")["judge"] is None


def test_judge_plugin_loader_requires_an_explicit_enabled_protocol(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        "orgmemory_eval.official_scorer.importlib.import_module",
        lambda _: SimpleNamespace(build=FakeJudge),
    )

    judge = load_judge_plugin("fixture_plugin:build")

    assert judge.name == "offline-fake"
    assert judge.enabled is True


def test_cli_scores_a_complete_transcript_without_a_judge(tmp_path: Path) -> None:
    transcript = tmp_path / "transcript.jsonl"
    output = tmp_path / "report.json"
    write_transcript(transcript, correct_rows(official_dataset()))

    main(["--transcript", str(transcript), "--output", str(output)])

    report = json.loads(output.read_text(encoding="utf-8"))
    assert report["official_source"]["case_count"] == 50
    assert report["transcript"]["case_count"] == 50
    assert report["judge"]["enabled"] is False
    assert report["offline_gate_passed"] is True
