import { createFileRoute } from "@tanstack/react-router"

import { SkillGitHubImportPage } from "@/features/assets/components/skill-github-import-page"

export const Route = createFileRoute("/_authenticated/assets/new/skill/github")({
  component: SkillGitHubImportPage,
  staticData: { title: "Import Skills from GitHub" },
})
