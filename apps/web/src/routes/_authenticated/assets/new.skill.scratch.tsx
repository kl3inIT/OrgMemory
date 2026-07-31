import { createFileRoute } from "@tanstack/react-router"

import { SkillScratchPage } from "@/features/assets/components/skill-scratch-page"

export const Route = createFileRoute("/_authenticated/assets/new/skill/scratch")({
  component: SkillScratchPage,
  staticData: { title: "Start a Skill" },
})
