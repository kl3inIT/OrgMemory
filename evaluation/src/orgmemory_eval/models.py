from __future__ import annotations

from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

Identifier = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=128),
]
NonBlankText = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1),
]
SchemaVersion = Annotated[
    str,
    StringConstraints(
        strip_whitespace=True,
        pattern=r"^orgmemory\.rag-evaluation\.v1$",
    ),
]


class EvaluationCase(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    case_id: Identifier
    question: NonBlankText
    reference_answer: NonBlankText
    answer: NonBlankText
    contexts: list[NonBlankText] = Field(min_length=1)
    citation_ids: list[Identifier] = Field(default_factory=list)
    latency_ms: float = Field(ge=0)


class EvaluationDataset(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    schema_version: SchemaVersion
    dataset_id: Identifier
    system_under_test: Annotated[
        str,
        StringConstraints(strip_whitespace=True, min_length=1, max_length=256),
    ]
    cases: list[EvaluationCase] = Field(min_length=1)

    @model_validator(mode="after")
    def require_unique_case_ids(self) -> EvaluationDataset:
        case_ids = [case.case_id for case in self.cases]
        if len(case_ids) != len(set(case_ids)):
            raise ValueError("case_id values must be unique")
        return self
