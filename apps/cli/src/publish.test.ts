import { afterEach, describe, expect, it, vi } from "vitest"

import { publishSkillDraft, publicationUrl } from "./publish.js"

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("Skill Draft publication", () => {
  it("posts a bounded package to the same-origin companion endpoint", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      Response.json(
        {
          id: "10000000-0000-0000-0000-000000000001",
          type: "SKILL",
          namespace: "finance",
          slug: "expense-review",
          knowledgeSpaceId: "30000000-0000-0000-0000-000000000001",
          draft: {
            id: "20000000-0000-0000-0000-000000000001",
            lockVersion: 0,
            title: "Expense review",
            summary: "Review one expense",
          },
        },
        { status: 201 },
      ),
    )
    vi.stubGlobal("fetch", fetchMock)
    const serverUrl = new URL("https://orgmemory.example/mcp")

    const result = await publishSkillDraft({
      serverUrl,
      token: "publisher-token",
      skillPackage: {
        folder: "/skills/expense-review",
        name: "expense-review",
        description: "Review one expense",
        fileCount: 1,
        contentBytes: 8,
        archiveBytes: new Uint8Array([1, 2, 3]),
        packageDigest: "a".repeat(64),
        files: [
          { path: "SKILL.md", size: 8, sha256: "b".repeat(64) },
        ],
      },
      namespace: "finance",
      knowledgeSpaceId: "30000000-0000-0000-0000-000000000001",
      classification: "INTERNAL",
    })

    expect(result.slug).toBe("expense-review")
    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, request] = fetchMock.mock.calls[0] as [URL, RequestInit]
    expect(url.toString()).toBe("https://orgmemory.example/skill-publications")
    expect(request.headers).toEqual({
      Authorization: "Bearer publisher-token",
    })
    const form = request.body as FormData
    expect(form.get("namespace")).toBe("finance")
    expect(form.get("knowledgeSpaceId")).toBe(
      "30000000-0000-0000-0000-000000000001",
    )
    expect(form.get("classification")).toBe("INTERNAL")
    expect(form.get("file")).toBeInstanceOf(File)
  })

  it("derives no cross-origin publication URL from the MCP endpoint", () => {
    expect(
      publicationUrl(new URL("https://orgmemory.example/mcp")).toString(),
    ).toBe("https://orgmemory.example/skill-publications")
  })

  it("reports a bounded downstream Problem detail", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        Response.json(
          { detail: "A Skill already uses this namespace and name" },
          { status: 409 },
        ),
      ),
    )

    await expect(
      publishSkillDraft({
        serverUrl: new URL("https://orgmemory.example/mcp"),
        token: "publisher-token",
        skillPackage: {
          folder: "/skills/expense-review",
          name: "expense-review",
          description: "Review one expense",
          fileCount: 1,
          contentBytes: 8,
          archiveBytes: new Uint8Array([1]),
          packageDigest: "a".repeat(64),
          files: [],
        },
        namespace: "finance",
        knowledgeSpaceId: "30000000-0000-0000-0000-000000000001",
        classification: "INTERNAL",
      }),
    ).rejects.toThrow("A Skill already uses this namespace and name")
  })
})
