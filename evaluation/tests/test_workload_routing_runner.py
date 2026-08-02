from orgmemory_eval.workload_routing_runner import (
    Observation,
    activation,
    aggregate,
    fragment_recall,
)


def test_fragment_recall_is_case_and_whitespace_insensitive() -> None:
    assert fragment_recall(["Annual   Leave", "ACME"], ["annual leave", "acme"]) == 1.0


def test_aggregate_contains_only_redacted_measurements() -> None:
    report = aggregate(
        [
            Observation(True, 100, 1.0, 3, 2),
            Observation(True, 200, 0.5, 2, 1),
        ]
    )

    assert report == {
        "samples": 2,
        "validResponses": 2,
        "providerFailures": 0,
        "meanRecall": 0.75,
        "p95LatencyMs": 200,
        "meanEntities": 2.5,
        "meanRelationships": 1.5,
        "errorCodes": [],
    }


def test_keyword_and_graph_activation_are_independent() -> None:
    baseline = {
        "samples": 12,
        "validResponses": 12,
        "providerFailures": 0,
        "meanRecall": 1.0,
        "p95LatencyMs": 1000,
        "meanEntities": 3.0,
        "meanRelationships": 2.0,
    }
    candidate = baseline | {
        "p95LatencyMs": 800,
        "meanEntities": 2.5,
        "meanRelationships": 1.7,
    }

    assert activation("keyword", baseline, candidate)
    assert activation("graph", baseline, candidate)
    assert not activation("graph", baseline, candidate | {"p95LatencyMs": 1100})
