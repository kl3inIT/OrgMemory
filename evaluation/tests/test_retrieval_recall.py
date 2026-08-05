import pytest

from orgmemory_eval.retrieval_recall import (
    GoldenCase,
    GoldenDataset,
    ObservationSet,
    RetrievalObservation,
    recall_at_k,
    score,
)


def golden_dataset() -> GoldenDataset:
    return GoldenDataset(
        schema_version="orgmemory.retrieval-recall.v1",
        dataset_id="fixture",
        corpus_manifest="demo/fixtures/documents/manifest.json",
        cases=[
            GoldenCase(
                case_id="q1",
                question="How much annual leave is available?",
                golden_chunk_ids=["DOC002#3.1-annual-leave"],
                deterministic_lexical_terms=["annual leave"],
                source_path="demo/fixtures/documents/DOC002.md",
            ),
            GoldenCase(
                case_id="q2",
                question="How are expenses submitted?",
                golden_chunk_ids=["DOC011#3.1-submission"],
                deterministic_lexical_terms=["expense submission"],
                source_path="demo/fixtures/documents/DOC011.md",
            ),
        ],
    )


def observations(*, bypass_second: bool = True) -> ObservationSet:
    return ObservationSet(
        schema_version="orgmemory.retrieval-observations.v1",
        dataset_id="fixture",
        observations=[
            RetrievalObservation(
                case_id="q1",
                keyword_seeded_chunk_ids=["DOC002#3.1-annual-leave"],
                bypass_chunk_ids=["DOC002#3.1-annual-leave"],
            ),
            RetrievalObservation(
                case_id="q2",
                keyword_seeded_chunk_ids=["DOC011#3.1-submission"],
                bypass_chunk_ids=(
                    ["DOC011#3.1-submission"] if bypass_second else ["unrelated"]
                ),
            ),
        ],
    )


def test_recall_at_k_deduplicates_retrieved_ids_and_honors_cutoff() -> None:
    retrieved = [f"noise-{index}" for index in range(40)] + ["golden"]

    assert recall_at_k(["golden"], retrieved, 40) == 0.0
    assert recall_at_k(["golden"], retrieved, 60) == 1.0


def test_score_passes_equal_keyword_and_bypass_recall() -> None:
    report = score(golden_dataset(), observations())

    assert report["keywordSeededRecallAt40"] == 1.0
    assert report["bypassRecallAt40"] == 1.0
    assert report["bypassDeltaPoints"] == 0.0
    assert report["gatePassed"] is True


def test_score_fails_a_regression_larger_than_two_points() -> None:
    report = score(golden_dataset(), observations(bypass_second=False))

    assert report["bypassDeltaPoints"] == -50.0
    assert report["gatePassed"] is False


def test_score_rejects_incomplete_observations() -> None:
    incomplete = ObservationSet(
        schema_version="orgmemory.retrieval-observations.v1",
        dataset_id="fixture",
        observations=observations().observations[:1],
    )

    with pytest.raises(ValueError, match="observation cases differ"):
        score(golden_dataset(), incomplete)


def test_score_rejects_non_default_top_k_for_v1_report() -> None:
    with pytest.raises(ValueError, match="requires top_k=40"):
        score(golden_dataset(), observations(), top_k=20)
