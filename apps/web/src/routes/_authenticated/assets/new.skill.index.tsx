import { createFileRoute } from "@tanstack/react-router"

import { SkillCreationPage } from "@/features/assets/components/skill-creation-page"

export const Route = createFileRoute("/_authenticated/assets/new/skill/")({
  component: SkillCreationPage,
  staticData: { title: "Create a Skill" },
})
