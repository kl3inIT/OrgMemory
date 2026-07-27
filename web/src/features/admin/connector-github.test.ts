import { describe, expect, it } from "vitest"

import { CONNECTOR_CATALOG } from "@/features/admin/connector-catalog"
import { configFrom, CONNECTOR_FORMS, draftFrom } from "@/features/admin/connector-forms"
import { probeReason } from "@/features/admin/connector-probe"

describe("GitHub connector administration", () => {
  it("publishes the installed source and its least-privilege GitHub App contract", () => {
    const entry = CONNECTOR_CATALOG.find((candidate) => candidate.sourceSystem === "github")

    expect(entry).toMatchObject({
      name: "GitHub",
      aclAuthority: "SOURCE",
      credential: {
        keyName: "organization",
        requirements: ["Metadata: read", "Issues: read"],
      },
    })
  })

  it("serializes repository ids as the adapter's opaque source configuration", () => {
    const descriptor = CONNECTOR_FORMS.github
    const draft = draftFrom(descriptor, {
      repositoryIds: ["77", "88"],
      maxItemsPerRepository: 250,
    })

    expect(configFrom(descriptor, draft)).toEqual({
      repositoryIds: ["77", "88"],
      maxItemsPerRepository: 250,
    })
  })

  it("explains a GitHub permission refusal in operator language", () => {
    expect(
      probeReason("github", {
        authenticated: true,
        canReadContent: false,
        errorCode: "github_http_403",
      }),
    ).toContain("refused")
  })
})
