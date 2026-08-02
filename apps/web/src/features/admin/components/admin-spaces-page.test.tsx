import { describe, expect, it } from "vitest"

import {
  audienceLabel,
  isBuiltInAudience,
  subjectLabel,
} from "@/features/admin/admin-space-audience"

const directory = {
  departments: new Map([["sales-id", "Sales"]]),
  users: new Map([["lan-id", "Vũ Thị Lan"]]),
  grantOptions: new Map(),
  grantRoles: new Map(),
}

describe("Knowledge Space audience presentation", () => {
  it("uses directory names instead of exposing internal ids", () => {
    expect(subjectLabel("organizational_unit:sales-id#member", directory)).toBe("Sales")
    expect(subjectLabel("user:lan-id", directory)).toBe("Vũ Thị Lan")
    expect(subjectLabel("organizational_unit:missing-id#member", directory)).toBe(
      "Department no longer in directory",
    )
    expect(subjectLabel("user:missing-id", directory)).toBe("Person no longer in directory")
    expect(subjectLabel("organization:org-id#knowledge_reader", directory)).toBe(
      "Knowledge readers",
    )
  })

  it("labels the persisted audience promise in business terms", () => {
    expect(
      audienceLabel(
        { audienceMode: "DEPARTMENT", departmentId: "sales-id", audienceVersion: 1 },
        directory,
      ),
    ).toBe("Sales department")
    expect(audienceLabel({ audienceMode: "ORGANIZATION" }, directory)).toBe(
      "Organization audience",
    )
    expect(audienceLabel({ audienceMode: "RESTRICTED_CUSTOM" }, directory)).toBe(
      "Restricted custom audience",
    )
  })

  it("only locks the viewer tuple projected by the selected built-in mode", () => {
    expect(
      isBuiltInAudience(
        { audienceMode: "DEPARTMENT", departmentId: "sales-id" },
        "organizational_unit:sales-id#member",
      ),
    ).toBe(true)
    expect(
      isBuiltInAudience(
        { audienceMode: "DEPARTMENT", departmentId: "sales-id" },
        "organization:org-id#member",
      ),
    ).toBe(false)
    expect(
      isBuiltInAudience(
        { audienceMode: "RESTRICTED_CUSTOM" },
        "organizational_unit:sales-id#member",
      ),
    ).toBe(false)
  })
})
