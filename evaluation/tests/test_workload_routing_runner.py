import io
import json
import urllib.error

import pytest

from orgmemory_eval.workload_routing_runner import (
    Observation,
    Route,
    activation,
    aggregate,
    fragment_recall,
    observe,
    request_json,
    validate_fixture_cases,
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


def test_aggregate_excludes_provider_failures_from_quality_metrics() -> None:
    report = aggregate(
        [
            Observation(True, 200, 1.0, 4, 3),
            Observation(False, 0, 0.0, 0, 0, "TimeoutError"),
        ]
    )

    assert report["samples"] == 2
    assert report["validResponses"] == 1
    assert report["providerFailures"] == 1
    assert report["meanRecall"] == 1.0
    assert report["p95LatencyMs"] == 200
    assert report["meanEntities"] == 4
    assert report["meanRelationships"] == 3


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
    slower = candidate | {"p95LatencyMs": 1100}
    assert not activation("graph", baseline, slower)
    assert activation("keyword", baseline, slower)


@pytest.mark.parametrize(
    "transient_error",
    [
        urllib.error.HTTPError("https://example.test", 429, "rate limit", {}, None),
        urllib.error.HTTPError("https://example.test", 503, "unavailable", {}, None),
        TimeoutError("timed out"),
    ],
)
def test_request_json_retries_only_transient_failures(
    monkeypatch: pytest.MonkeyPatch,
    transient_error: BaseException,
) -> None:
    calls = 0
    response = {
        "choices": [{"message": {"content": json.dumps({"ok": True})}}],
    }

    def fake_urlopen(*_args: object, **_kwargs: object) -> io.BytesIO:
        nonlocal calls
        calls += 1
        if calls == 1:
            raise transient_error
        return io.BytesIO(json.dumps(response).encode())

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    monkeypatch.setattr("time.sleep", lambda _seconds: None)

    result, latency_ms = request_json(
        base_url="https://example.test/v1",
        api_key="redacted",
        timeout_seconds=1,
        payload={"model": "test"},
    )

    assert result == {"ok": True}
    assert latency_ms >= 0
    assert calls == 2


def test_request_json_does_not_retry_non_transient_http_failures(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    calls = 0

    def fake_urlopen(*_args: object, **_kwargs: object) -> io.BytesIO:
        nonlocal calls
        calls += 1
        raise urllib.error.HTTPError("https://example.test", 400, "bad request", {}, None)

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)

    with pytest.raises(urllib.error.HTTPError):
        request_json(
            base_url="https://example.test/v1",
            api_key="redacted",
            timeout_seconds=1,
            payload={"model": "test"},
        )

    assert calls == 1


def test_observe_records_malformed_graph_elements_without_aborting() -> None:
    case = {
        "language": "en",
        "evidence": "Acme owns Atlas.",
        "requiredEntityFragments": ["Acme"],
        "minimumEntities": 1,
        "minimumRelationships": 1,
    }

    observation = observe(
        case,
        Route("graph", "candidate", "test", None),
        lambda _payload: ({"entities": [None], "relationships": []}, 10.0),
    )

    assert not observation.valid
    assert observation.error_code == "AttributeError"


def test_observe_records_empty_provider_choices_without_aborting() -> None:
    case = {
        "language": "en",
        "query": "Acme policy",
        "requiredKeywordFragments": ["Acme"],
    }

    def empty_choices(_payload: dict[str, object]) -> tuple[dict[str, object], float]:
        raise IndexError("choices is empty")

    observation = observe(
        case,
        Route("keyword", "candidate", "test", None),
        empty_choices,
    )

    assert not observation.valid
    assert observation.error_code == "IndexError"


def test_fixture_validation_fails_before_requests_for_missing_case_keys() -> None:
    with pytest.raises(SystemExit, match="requiredKeywordFragments"):
        validate_fixture_cases(
            [
                {
                    "caseId": "missing-key",
                    "language": "en",
                    "query": "query",
                    "evidence": "evidence",
                    "requiredEntityFragments": [],
                    "minimumEntities": 0,
                    "minimumRelationships": 0,
                }
            ]
        )
