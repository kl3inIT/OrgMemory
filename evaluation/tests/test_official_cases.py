import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from orgmemory_eval.official_cases import (
    DEFAULT_OFFICIAL_CASES_PATH,
    ExpectedPermission,
    load_official_cases,
)


def write_cases(path: Path, cases: list[dict[str, object]]) -> None:
    path.write_text(json.dumps(cases, ensure_ascii=False), encoding="utf-8")


def official_payload() -> list[dict[str, object]]:
    return json.loads(DEFAULT_OFFICIAL_CASES_PATH.read_text(encoding="utf-8"))


def test_loads_all_official_cases_and_splits_multi_document_goldens() -> None:
    dataset, source_sha256 = load_official_cases()

    assert len(dataset.cases) == 50
    assert len(dataset.allow_cases) == 43
    assert len(dataset.deny_cases) == 7
    assert len(source_sha256) == 64
    assert {case.question_id for case in dataset.deny_cases} == {
        "P007",
        "P009",
        "P027",
        "P032",
        "P035",
        "P037",
        "P042",
    }
    p031 = dataset.by_question_id["P031"]
    assert p031.expected_permission == ExpectedPermission.ALLOW
    assert p031.expected_document_ids == ("DOC001", "DOC011")


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("expected_permission", "Maybe"),
        ("answer_type", "Opinion"),
    ],
)
def test_rejects_unknown_closed_values(tmp_path: Path, field: str, value: str) -> None:
    cases = official_payload()
    cases[0][field] = value
    path = tmp_path / "invalid.json"
    write_cases(path, cases)

    with pytest.raises(ValidationError):
        load_official_cases(path)


def test_rejects_incomplete_official_case_set(tmp_path: Path) -> None:
    path = tmp_path / "incomplete.json"
    write_cases(path, official_payload()[:-1])

    with pytest.raises(ValidationError, match="at least 50 items"):
        load_official_cases(path)


def test_rejects_renamed_official_case_id(tmp_path: Path) -> None:
    cases = official_payload()
    cases[-1]["question_id"] = "P051"
    path = tmp_path / "renamed-case.json"
    write_cases(path, cases)

    with pytest.raises(ValidationError, match="official question ids differ"):
        load_official_cases(path)


def test_rejects_duplicate_official_case_id(tmp_path: Path) -> None:
    cases = official_payload()
    cases[-1]["question_id"] = "P049"
    path = tmp_path / "duplicate-case.json"
    write_cases(path, cases)

    with pytest.raises(ValidationError, match="question_id values must be unique"):
        load_official_cases(path)


def test_rejects_changed_official_permission_split(tmp_path: Path) -> None:
    cases = official_payload()
    cases[0]["expected_permission"] = "Deny"
    path = tmp_path / "permission-split.json"
    write_cases(path, cases)

    with pytest.raises(ValidationError, match="permission split differs: allow=42, deny=8"):
        load_official_cases(path)


@pytest.mark.parametrize(
    ("value", "message"),
    [
        ("DOC1", "semicolon-separated DOCnnn values"),
        ("DOC001; DOC001", "must be unique"),
    ],
)
def test_rejects_invalid_expected_document_ids(tmp_path: Path, value: str, message: str) -> None:
    cases = official_payload()
    cases[0]["expected_document_id"] = value
    path = tmp_path / "invalid-documents.json"
    write_cases(path, cases)

    with pytest.raises(ValidationError, match=message):
        load_official_cases(path)


def test_rejects_out_of_range_official_user(tmp_path: Path) -> None:
    cases = official_payload()
    cases[0]["user_id"] = "U033"
    path = tmp_path / "invalid-user.json"
    write_cases(path, cases)

    with pytest.raises(ValidationError, match="between U001 and U032"):
        load_official_cases(path)
