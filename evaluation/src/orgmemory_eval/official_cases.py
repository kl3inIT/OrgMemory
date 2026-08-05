from __future__ import annotations

import hashlib
import re
from enum import StrEnum
from pathlib import Path
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, RootModel, StringConstraints, field_validator

NonBlankText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
QuestionId = Annotated[str, StringConstraints(pattern=r"^P\d{3}$")]
UserId = Annotated[str, StringConstraints(pattern=r"^U\d{3}$")]
DocumentId = Annotated[str, StringConstraints(pattern=r"^DOC\d{3}$")]

OFFICIAL_CASE_COUNT = 50
OFFICIAL_ALLOW_CASE_COUNT = 43
OFFICIAL_DENY_CASE_COUNT = 7
OFFICIAL_CASE_IDS = frozenset(f"P{index:03d}" for index in range(1, OFFICIAL_CASE_COUNT + 1))
DEFAULT_OFFICIAL_CASES_PATH = (
    Path(__file__).resolve().parents[3] / "demo" / "fixtures" / "public-evaluation.json"
)
DOCUMENT_ID = re.compile(r"^DOC\d{3}$")


class ExpectedPermission(StrEnum):
    ALLOW = "Allow"
    DENY = "Deny"


class AnswerType(StrEnum):
    EXACT = "Exact"
    SEMANTIC = "Semantic"
    SUMMARY = "Summary"
    PERMISSION = "Permission"
    MULTI_DOCUMENT = "Multi-document"


class Difficulty(StrEnum):
    EASY = "Easy"
    MEDIUM = "Medium"
    HARD = "Hard"


class OfficialCase(BaseModel):
    model_config = ConfigDict(extra="forbid")

    question_id: QuestionId
    category: NonBlankText
    user_id: UserId
    user_role: NonBlankText
    user_department: NonBlankText
    question_vi: NonBlankText
    expected_permission: ExpectedPermission
    expected_document_id: NonBlankText
    answer_type: AnswerType
    difficulty: Difficulty

    @field_validator("user_id")
    @classmethod
    def require_official_user_range(cls, value: str) -> str:
        user_number = int(value.removeprefix("U"))
        if not 1 <= user_number <= 32:
            raise ValueError("user_id must be between U001 and U032")
        return value

    @field_validator("expected_document_id")
    @classmethod
    def normalize_document_ids(cls, value: str) -> str:
        document_ids = [item.strip() for item in value.split(";")]
        if any(not item or DOCUMENT_ID.fullmatch(item) is None for item in document_ids):
            raise ValueError("expected_document_id must contain semicolon-separated DOCnnn values")
        if len(document_ids) != len(set(document_ids)):
            raise ValueError("expected_document_id values must be unique")
        return "; ".join(document_ids)

    @property
    def expected_document_ids(self) -> tuple[str, ...]:
        return tuple(item.strip() for item in self.expected_document_id.split(";"))


class OfficialCaseList(RootModel[list[OfficialCase]]):
    pass


class OfficialDataset(BaseModel):
    model_config = ConfigDict(extra="forbid")

    cases: list[OfficialCase] = Field(
        min_length=OFFICIAL_CASE_COUNT, max_length=OFFICIAL_CASE_COUNT
    )

    @field_validator("cases")
    @classmethod
    def require_complete_official_case_ids(cls, cases: list[OfficialCase]) -> list[OfficialCase]:
        case_ids = [case.question_id for case in cases]
        if len(case_ids) != len(set(case_ids)):
            raise ValueError("question_id values must be unique")
        if set(case_ids) != OFFICIAL_CASE_IDS:
            missing = sorted(OFFICIAL_CASE_IDS - set(case_ids))
            unexpected = sorted(set(case_ids) - OFFICIAL_CASE_IDS)
            raise ValueError(
                f"official question ids differ: missing={missing}, unexpected={unexpected}"
            )
        return cases

    @field_validator("cases")
    @classmethod
    def require_official_permission_split(cls, cases: list[OfficialCase]) -> list[OfficialCase]:
        allow_count = sum(case.expected_permission == ExpectedPermission.ALLOW for case in cases)
        deny_count = sum(case.expected_permission == ExpectedPermission.DENY for case in cases)
        if (allow_count, deny_count) != (OFFICIAL_ALLOW_CASE_COUNT, OFFICIAL_DENY_CASE_COUNT):
            raise ValueError(
                f"official permission split differs: allow={allow_count}, deny={deny_count}"
            )
        return cases

    @property
    def allow_cases(self) -> tuple[OfficialCase, ...]:
        return tuple(
            case for case in self.cases if case.expected_permission == ExpectedPermission.ALLOW
        )

    @property
    def deny_cases(self) -> tuple[OfficialCase, ...]:
        return tuple(
            case for case in self.cases if case.expected_permission == ExpectedPermission.DENY
        )

    @property
    def by_question_id(self) -> dict[str, OfficialCase]:
        return {case.question_id: case for case in self.cases}


def load_official_cases(
    path: Path = DEFAULT_OFFICIAL_CASES_PATH,
) -> tuple[OfficialDataset, str]:
    raw = path.read_bytes()
    cases = OfficialCaseList.model_validate_json(raw).root
    dataset = OfficialDataset(cases=cases)
    return dataset, hashlib.sha256(raw).hexdigest()
