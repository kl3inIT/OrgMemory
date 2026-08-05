import pytest

from orgmemory_eval.official_cases import DEFAULT_OFFICIAL_CASES_PATH, load_official_cases
from orgmemory_eval.retrieval_recall import (
    OFFICIAL_RECALL_DATASET_ID,
    GoldenDataset,
    ObservationSet,
    RetrievalObservation,
    derive_golden_dataset,
    recall_at_k,
    score,
)


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


def test_recall_at_k_deduplicates_retrieved_ids_and_honors_cutoff() -> None:
    retrieved = [f"DOC{index:03d}" for index in range(1, 41)] + ["DOC999"]

    assert recall_at_k(["DOC999"], retrieved, 40) == 0.0
    assert recall_at_k(["DOC999"], retrieved, 60) == 1.0


def test_score_passes_equal_keyword_and_bypass_document_recall() -> None:
    report = score(golden_dataset(), observations())

    assert report["caseCount"] == 43
    assert report["keywordSeededDocumentRecallAt40"] == 1.0
    assert report["bypassDocumentRecallAt40"] == 1.0
    assert report["bypassDeltaPoints"] == 0.0
    assert report["gatePassed"] is True


def test_score_fails_a_regression_larger_than_two_points() -> None:
    report = score(golden_dataset(), observations(bypass_failure_case="P001"))

    assert report["bypassDeltaPoints"] == pytest.approx(-(100 / 43))
    assert report["gatePassed"] is False


def test_score_rejects_incomplete_observations() -> None:
    complete = observations()
    incomplete = ObservationSet(
        schema_version="orgmemory.retrieval-observations.v2",
        dataset_id=OFFICIAL_RECALL_DATASET_ID,
        observations=complete.observations[:-1],
    )

    with pytest.raises(ValueError, match="observation cases differ"):
        score(golden_dataset(), incomplete)


def test_score_rejects_non_default_top_k_for_v2_report() -> None:
    with pytest.raises(ValueError, match="requires top_k=40"):
        score(golden_dataset(), observations(), top_k=20)
