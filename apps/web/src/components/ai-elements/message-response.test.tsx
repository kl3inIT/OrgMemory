import { describe, expect, it } from "vitest"

import { pluginsFor } from "@/components/ai-elements/message-response"

function pluginNames(markdown: string) {
  return Object.keys(pluginsFor(markdown)).sort()
}

describe("MessageResponse plugin selection", () => {
  it.each([
    "```js\nconst value = 1\n```",
    "~~~js\nconst value = 1\n~~~",
  ])("enables code rendering for fenced code", (markdown) => {
    expect(pluginNames(markdown)).toEqual(["cjk", "code"])
  })

  it.each([
    "```mermaid\ngraph TD\n```",
    "``` mermaid\ngraph TD\n```",
    "~~~mermaid\ngraph TD\n~~~",
    "~~~ mermaid\ngraph TD\n~~~",
  ])("enables Mermaid rendering for valid fenced diagrams", (markdown) => {
    expect(pluginNames(markdown)).toEqual(["cjk", "code", "mermaid"])
  })

  it("does not enable expensive fenced-block plugins for plain prose", () => {
    expect(pluginNames("A governed answer with evidence.")).toEqual(["cjk"])
  })
})
