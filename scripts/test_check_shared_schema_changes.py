from __future__ import annotations

import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("check_shared_schema_changes.py")
SPEC = importlib.util.spec_from_file_location("check_shared_schema_changes", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SharedSchemaCheckTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        migrations = self.root / MODULE.MIGRATION_DIRECTORY
        migrations.mkdir(parents=True)
        model = self.root / MODULE.OPENFGA_MODEL
        model.parent.mkdir(parents=True)
        model.write_text("model\n", encoding="utf-8")
        (migrations / "V1__baseline.sql").write_text("select 1;\n", encoding="utf-8")
        self.run_git("init")
        self.run_git("config", "user.email", "ci@example.invalid")
        self.run_git("config", "user.name", "CI")
        self.run_git("add", ".")
        self.run_git("commit", "-m", "baseline")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def run_git(self, *arguments: str) -> None:
        subprocess.run(
            ["git", *arguments],
            cwd=self.root,
            check=True,
            capture_output=True,
            text=True,
        )

    def test_rejects_duplicate_versions(self) -> None:
        migrations = self.root / MODULE.MIGRATION_DIRECTORY
        (migrations / "V1__second_baseline.sql").write_text("select 2;\n", encoding="utf-8")

        _, errors = MODULE.migration_inventory(self.root)

        self.assertTrue(any("duplicate Flyway version V1" in error for error in errors))

    def test_rejects_changes_to_a_migration_on_main(self) -> None:
        migration = self.root / MODULE.MIGRATION_DIRECTORY / "V1__baseline.sql"
        migration.write_text("select 2;\n", encoding="utf-8")

        errors = MODULE.immutable_migration_errors(self.root, "HEAD")

        self.assertEqual(
            [
                "migration already on HEAD was modified: "
                "core/src/main/resources/db/migration/V1__baseline.sql"
            ],
            errors,
        )

    def test_reports_new_migration_and_model_without_blocking(self) -> None:
        migrations = self.root / MODULE.MIGRATION_DIRECTORY
        (migrations / "V2__next.sql").write_text("select 2;\n", encoding="utf-8")
        (self.root / MODULE.OPENFGA_MODEL).write_text("model changed\n", encoding="utf-8")

        changed = MODULE.changed_protected_paths(self.root, "HEAD", "HEAD")

        self.assertEqual(
            [
                "core/src/main/resources/db/migration/V2__next.sql",
                "integrations/authorization-openfga/src/main/openfga/model.fga",
            ],
            changed,
        )


if __name__ == "__main__":
    unittest.main()
