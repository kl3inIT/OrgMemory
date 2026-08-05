import json
from functools import cache
from pathlib import Path

import pytest
from pydantic import ValidationError

from orgmemory_eval.official_cases import DEFAULT_OFFICIAL_CASES_PATH, load_official_cases
from orgmemory_eval.retrieval_recall import (
    OFFICIAL_RECALL_DATASET_ID,
    GoldenDataset,
    ObservationSet,
    RetrievalKeywordPlan,
    RetrievalObservation,
    derive_golden_dataset,
    main,
    recall_at_k,
    score,
)


@cache
def golden_dataset() -> GoldenDataset:
    official, source_sha256 = load_official_cases(DEFAULT_OFFICIAL_CASES_PATH)
    return derive_golden_dataset(official, official_source_sha256=source_sha256)


def observations(*, bypass_failure_case: str | None = None) -> ObservationSet:
    golden = golden_dataset()
    return ObservationSet(
        schema_version="orgmemory.retrieval-observations.v2",
        dataset_id=OFFICIAL_RECALL_DATASET_ID,
        observations=[
            RetrievalObservation(
                case_id=case.case_id,
                keyword_seeded_document_ids=case.golden_document_ids,
                bypass_document_ids=(
                    ["DOC999"] if case.case_id == bypass_failure_case else case.golden_document_ids
                ),
                keyword_seeded_golden_ranks={
                    document_id: rank
                    for rank, document_id in enumerate(case.golden_document_ids, start=1)
                },
                bypass_golden_ranks={
                    document_id: (
                        None
                        if case.case_id == bypass_failure_case
                        else rank
                    )
                    for rank, document_id in enumerate(case.golden_document_ids, start=1)
                },
                keyword_plan=RetrievalKeywordPlan(
                    high_level_keywords=["policy"],
                    low_level_keywords=["probation"],
                    source="model",
                ),
            )
            for case in golden.cases
        ],
    )


def test_official_allow_cases_are_the_only_recall_goldens() -> None:
    golden = golden_dataset()

    assert len(golden.cases) == 43
    assert {case.case_id for case in golden.cases}.isdisjoint(
        {"P007", "P009", "P027", "P032", "P035", "P037", "P042"}
    )
    multi_document = next(case for case in golden.cases if case.case_id == "P031")
    assert multi_document.golden_document_ids == ["DOC001", "DOC011"]


def test_recall_at_k_honors_cutoff_and_counts_duplicate_slots() -> None:
    retrieved = [f"DOC{index:03d}" for index in range(1, 41)] + ["DOC999"]

    assert recall_at_k(["DOC999"], retrieved, 40) == 0.0
    assert recall_at_k(["DOC999"], retrieved, 60) == 1.0

    duplicated = ["DOC001"] * 40 + ["DOC999"]
    assert recall_at_k(["DOC001", "DOC999"], duplicated, 40) == 0.5


def test_score_passes_equal_keyword_and_bypass_document_recall() -> None:
    report = score(golden_dataset(), observations())

    assert report["caseCount"] == 43
    assert report["topK"] == 40
    assert report["keywordSeededDocumentRecallAt40"] == 1.0
    assert report["bypassDocumentRecallAt40"] == 1.0
    assert report["bypassDeltaPoints"] == 0.0
    assert report["gatePassed"] is True
    p031 = next(case for case in report["cases"] if case["caseId"] == "P031")
    assert p031["keywordSeededGoldenRanks"] == {"DOC001": 1, "DOC011": 2}
    assert p031["keywordPlan"]["high_level_keywords"] == ["policy"]


@pytest.mark.parametrize(("keyword_rank", "bypass_rank"), [(-1, 1), (1, 0)])
def test_observation_rejects_a_non_positive_rank_in_either_map(
    keyword_rank: int,
    bypass_rank: int,
) -> None:
    with pytest.raises(ValueError, match="golden ranks must be positive or null"):
        RetrievalObservation(
            case_id="P031",
            keyword_seeded_document_ids=["DOC001"],
            bypass_document_ids=["DOC001"],
            keyword_seeded_golden_ranks={"DOC001": keyword_rank},
            bypass_golden_ranks={"DOC001": bypass_rank},
        )


def test_score_rejects_golden_ranks_that_disagree_with_retrieved_order() -> None:
    complete = observations()
    first = complete.observations[0].model_copy(
        update={"keyword_seeded_golden_ranks": {"DOC001": 2}}
    )
    inconsistent = complete.model_copy(update={"observations": [first, *complete.observations[1:]]})

    with pytest.raises(ValueError, match="keyword golden ranks disagree"):
        score(golden_dataset(), inconsistent)


def test_score_rejects_bypass_ranks_that_disagree_with_retrieved_order() -> None:
    complete = observations()
    first = complete.observations[0].model_copy(
        update={"bypass_golden_ranks": {"DOC001": 9}}
    )
    inconsistent = complete.model_copy(update={"observations": [first, *complete.observations[1:]]})

    with pytest.raises(ValueError, match="bypass golden ranks disagree"):
        score(golden_dataset(), inconsistent)


def test_score_fails_a_regression_larger_than_two_points() -> None:
    report = score(golden_dataset(), observations(bypass_failure_case="P001"))

    assert report["bypassDeltaPoints"] == pytest.approx(-(100 / 43))
    assert report["gatePassed"] is False


def test_score_passes_a_partial_multi_document_regression_within_tolerance() -> None:
    complete = observations()
    adjusted = ObservationSet(
        schema_version="orgmemory.retrieval-observations.v2",
        dataset_id=OFFICIAL_RECALL_DATASET_ID,
        observations=[
            observation.model_copy(
                update={
                    "bypass_document_ids": ["DOC001"],
                    "bypass_golden_ranks": {"DOC001": 1, "DOC011": None},
                }
            )
            if observation.case_id == "P031"
            else observation
            for observation in complete.observations
        ],
    )

    report = score(golden_dataset(), adjusted)

    assert report["bypassDeltaPoints"] == pytest.approx(-(50 / 43))
    assert report["gatePassed"] is True


def test_score_rejects_incomplete_observations() -> None:
    complete = observations()
    incomplete = ObservationSet(
        schema_version="orgmemory.retrieval-observations.v2",
        dataset_id=OFFICIAL_RECALL_DATASET_ID,
        observations=complete.observations[:-1],
    )

    with pytest.raises(ValueError, match="observation cases differ"):
        score(golden_dataset(), incomplete)


def test_score_rejects_mismatched_dataset_id() -> None:
    complete = observations()
    mismatched = ObservationSet(
        schema_version="orgmemory.retrieval-observations.v2",
        dataset_id="some-other-dataset",
        observations=complete.observations,
    )

    with pytest.raises(ValueError, match="dataset_id values differ"):
        score(golden_dataset(), mismatched)


@pytest.mark.parametrize("digest", ["abc", "g" * 64, "A" * 64])
def test_golden_dataset_rejects_a_malformed_official_digest(digest: str) -> None:
    payload = golden_dataset().model_dump()
    payload["official_source_sha256"] = digest

    with pytest.raises(ValidationError, match="String should match pattern"):
        GoldenDataset.model_validate(payload)


@pytest.mark.parametrize(
    ("failure_case", "expected_status"),
    [(None, 0), ("P001", 1)],
)
def test_cli_returns_the_gate_status_after_writing_the_report(
    tmp_path: Path,
    failure_case: str | None,
    expected_status: int,
) -> None:
    observation_path = tmp_path / "observations.json"
    output_path = tmp_path / "report.json"
    observation_path.write_text(
        observations(bypass_failure_case=failure_case).model_dump_json(),
        encoding="utf-8",
    )

    exit_status = main(["--observations", str(observation_path), "--output", str(output_path)])

    report = json.loads(output_path.read_text(encoding="utf-8"))
    assert exit_status == expected_status
    assert report["gatePassed"] is (expected_status == 0)
