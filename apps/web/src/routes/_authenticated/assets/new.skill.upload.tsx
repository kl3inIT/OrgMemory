import { createFileRoute } from "@tanstack/react-router"

import { SkillUploadPage } from "@/features/assets/components/skill-upload-page"

export const Route = createFileRoute("/_authenticated/assets/new/skill/upload")({
  component: SkillUploadPage,
  staticData: { title: "Upload a Skill" },
})
