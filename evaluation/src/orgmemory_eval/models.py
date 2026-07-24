from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, model_validator


class EvaluationCase(BaseModel):
    model_config = ConfigDict(extra="forbid")

    case_id: str = Field(min_length=1, max_length=128)
    question: str = Field(min_length=1)
    reference_answer: str = Field(min_length=1)
    answer: str = Field(min_length=1)
    contexts: list[str] = Field(min_length=1)
    citation_ids: list[str] = Field(default_factory=list)
    latency_ms: float = Field(ge=0)

    @model_validator(mode="after")
    def reject_blank_contexts(self) -> EvaluationCase:
        if any(not context.strip() for context in self.contexts):
            raise ValueError("contexts must not contain blank values")
        return self


class EvaluationDataset(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: str = Field(pattern=r"^orgmemory\.rag-evaluation\.v1$")
    dataset_id: str = Field(min_length=1, max_length=128)
    system_under_test: str = Field(min_length=1, max_length=256)
    cases: list[EvaluationCase] = Field(min_length=1)

    @model_validator(mode="after")
    def require_unique_case_ids(self) -> EvaluationDataset:
        case_ids = [case.case_id for case in self.cases]
        if len(case_ids) != len(set(case_ids)):
            raise ValueError("case_id values must be unique")
        return self
