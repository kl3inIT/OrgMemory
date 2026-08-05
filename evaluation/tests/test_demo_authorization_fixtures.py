import csv
import json
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
MANIFEST_PATH = REPO_ROOT / "demo" / "fixtures" / "documents" / "manifest.json"
DIRECTORY_PATH = REPO_ROOT / "demo" / "fixtures" / "postgres" / "directory.sql"
TUPLES_PATH = REPO_ROOT / "demo" / "fixtures" / "openfga" / "dataset-tuples.csv"

DEPARTMENT_SPACES = {
    "HR": "human-resources",
    "Finance": "finance",
    "Product": "product",
    "Engineering": "engineering",
    "Operations": "operations",
    "Legal & Compliance": "legal-compliance",
}


def test_document_placement_matches_declared_access() -> None:
    documents = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

    assert len(documents) == 40
    for document in documents:
        match document["allowedAccess"]:
            case "All" | "All Employees":
                expected_space = "company"
            case "Own Department":
                expected_space = DEPARTMENT_SPACES[document["department"]]
            case "Executive Only":
                expected_space = "executive-office"
            case access:
                raise AssertionError(f"unsupported access in {document['documentId']}: {access}")

        assert document["knowledgeSpaceKey"] == expected_space, document["documentId"]


def test_directory_fixture_persists_typed_space_audiences() -> None:
    sql = DIRECTORY_PATH.read_text(encoding="utf-8")
    space_rows = [
        line
        for line in sql.splitlines()
        if line.startswith("INSERT INTO knowledge_spaces")
    ]

    assert len(space_rows) == 8
    assert "'company', 'Company Knowledge', 'ORGANIZATION', 1" in space_rows[0]
    assert "NULL, 'company'" in space_rows[0]
    for row in space_rows[1:]:
        assert "'DEPARTMENT', 1" in row
        assert re.search(r"'d2000000-0000-4000-8000-00000000000[2-8]'", row)


def test_openfga_viewer_tuples_match_typed_space_audiences() -> None:
    with TUPLES_PATH.open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream))

    viewer_rows = [row for row in rows if row["relation"] == "viewer"]
    organization_viewers = {
        row["object_id"]
        for row in viewer_rows
        if row["user_type"] == "organization" and row["user_relation"] == "member"
    }
    department_viewers = {
        row["object_id"]
        for row in viewer_rows
        if row["user_type"] == "organizational_unit" and row["user_relation"] == "member"
    }

    assert organization_viewers == {"88888888-8888-4888-8888-888888888801"}
    assert department_viewers == {
        f"c3000000-0000-4000-8000-00000000000{number}" for number in range(2, 9)
    }
