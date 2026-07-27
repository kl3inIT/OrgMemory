import { describe, expect, it } from "vitest"

import {
  orgMemoryUuidSchema,
  parseSkillReference,
  resolvePackageUrl,
} from "./contracts.js"

describe("parseSkillReference", () => {
  it("accepts one exact server-compatible Skill coordinate", () => {
    expect(parseSkillReference("people/employee-onboarding@1.2.0")).toEqual({
      namespace: "people",
      slug: "employee-onboarding",
      version: "1.2.0",
    })
  })

  it("rejects coordinates the registry cannot resolve", () => {
    expect(() => parseSkillReference("people/employee_onboarding@1.2.0")).toThrow(
      "invalid",
    )
    expect(() =>
      parseSkillReference(`${"a".repeat(129)}/onboarding@1.2.0`),
    ).toThrow("invalid")
    expect(() =>
      parseSkillReference(`people/onboarding@${"1".repeat(65)}`),
    ).toThrow("invalid")
  })

  it.each([
    "people/employee-onboarding",
    "people/employee-onboarding@",
    "people/hr/employee-onboarding@1.2.0",
  ])("rejects malformed exact references: %s", (reference) => {
    expect(() => parseSkillReference(reference)).toThrow(
      "Use an exact Skill reference",
    )
  })
})

describe("OrgMemory identifiers", () => {
  it("accepts deterministic Java UUIDs used by governed fixtures", () => {
    expect(
      orgMemoryUuidSchema.parse("85000000-0000-0000-0000-000000000003"),
    ).toBe("85000000-0000-0000-0000-000000000003")
  })
})

describe("resolvePackageUrl", () => {
  const server = new URL("https://om.example.test/mcp")
  const path =
    "/skill-packages/85000000-0000-0000-0000-000000000003/releases/85000000-0000-0000-0000-000000000004"
  const link = {
    manifest: {
      assetId: "85000000-0000-0000-0000-000000000003",
      releaseId: "85000000-0000-0000-0000-000000000004",
    },
    packagePath: path,
  } as Parameters<typeof resolvePackageUrl>[1]

  it("resolves the exact same-origin package companion path", () => {
    expect(resolvePackageUrl(server, link).toString()).toBe(
      `https://om.example.test${path}`,
    )
  })

  it.each([
    "https://attacker.example/skill-packages/85000000-0000-0000-0000-000000000003/releases/85000000-0000-0000-0000-000000000004",
    "/skill-packages/../../admin",
    `${path}?redirect=https://attacker.example`,
  ])("rejects an untrusted package location: %s", (packagePath) => {
    expect(() =>
      resolvePackageUrl(server, { ...link, packagePath }),
    ).toThrow(
      "invalid Skill package location",
    )
  })

  it("rejects a package path for a different release", () => {
    expect(() =>
      resolvePackageUrl(server, {
        ...link,
        packagePath: path.replace(
          "85000000-0000-0000-0000-000000000004",
          "85000000-0000-0000-0000-000000000005",
        ),
      }),
    ).toThrow("invalid Skill package location")
  })
})
