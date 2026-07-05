import { useChat } from "@ai-sdk/react"
import { Link } from "@tanstack/react-router"
import { DefaultChatTransport } from "ai"
import { Bot, Check, Sparkles } from "lucide-react"
import { useMemo, useState } from "react"
import { toast } from "sonner"
import { PageTitle } from "@/components/layout/page-title"
import {
  Conversation,
  ConversationContent,
  ConversationEmptyState,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation"
import { Message, MessageContent, MessageResponse } from "@/components/ai-elements/message"
import {
  PromptInput,
  PromptInputBody,
  PromptInputFooter,
  PromptInputSubmit,
  PromptInputTextarea,
  type PromptInputMessage,
} from "@/components/ai-elements/prompt-input"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { formatAssetType } from "@/features/assets/asset-type"
import { StatusBadge } from "@/features/assets/status-badge"
import { useAssets, useRecordUsage } from "@/features/assets/use-assets"
import { useOrganizationLookups, userName } from "@/features/organization/use-organization-context"

export function AskMemoryPage() {
  const [conversationId] = useState(() => crypto.randomUUID())
  const [lastQuery, setLastQuery] = useState("")
  const { data, isError, isLoading } = useAssets()
  const { userById } = useOrganizationLookups()
  const usage = useRecordUsage()
  const matches = useMemo(() => rankAssets(data ?? [], lastQuery).slice(0, 3), [data, lastQuery])
  const { messages, sendMessage, status, stop } = useChat({
    transport: new DefaultChatTransport({
      api: "/api/ai/chat",
      prepareSendMessagesRequest: ({ messages }) => {
        const last = messages.at(-1)
        const text = (last?.parts ?? [])
          .filter((part) => part.type === "text")
          .map((part) => (part as { text: string }).text)
          .join("")
        return { body: { message: text, conversationId } }
      },
    }),
  })

  function onSubmit(message: PromptInputMessage) {
    if (message.text.trim()) {
      setLastQuery(message.text)
      sendMessage({ text: message.text })
    }
  }

  return (
    <div className="space-y-6">
      <PageTitle title="Ask Memory" subtitle="Find, understand, and use organizational AI capabilities with natural language." />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_28rem]">
        <Card className="min-h-[44rem]">
          <Conversation className="h-[560px]">
            <ConversationContent>
              {messages.length === 0 ? (
                <ConversationEmptyState
                  icon={<Sparkles className="size-8" />}
                  title="Ask about organizational AI memory"
                  description="Try: what approved workflows do we have for customer feedback analysis?"
                />
              ) : (
                messages.map((message) => (
                  <Message from={message.role} key={message.id}>
                    <MessageContent>
                      {message.parts.map((part, index) => {
                        if (part.type !== "text") return null
                        return <MessageResponse key={`${message.id}-${index}`}>{part.text}</MessageResponse>
                      })}
                    </MessageContent>
                  </Message>
                ))
              )}
            </ConversationContent>
            <ConversationScrollButton />
          </Conversation>
          <CardContent className="border-t pt-4">
            <PromptInput onSubmit={onSubmit}>
              <PromptInputBody>
                <PromptInputTextarea placeholder="Ask about capabilities, workflows, owners, usage, or create something new..." />
              </PromptInputBody>
              <PromptInputFooter>
                <span className="text-xs text-muted-foreground">Spring AI streaming via UI Message Stream</span>
                <PromptInputSubmit status={status} onStop={stop} />
              </PromptInputFooter>
            </PromptInput>
          </CardContent>
        </Card>

        <aside className="grid content-start gap-4">
          <Card>
            <CardHeader>
              <CardTitle>Matched Capability Assets</CardTitle>
              <CardAction>
                <Button asChild variant="link" size="sm"><Link to="/registry">View all</Link></Button>
              </CardAction>
            </CardHeader>
            <CardContent className="space-y-3">
              {isLoading ? <p className="text-sm text-muted-foreground">Loading matched assets from API...</p> : null}
              {isError ? <p className="text-sm text-destructive">Could not load matched assets.</p> : null}
              {!isLoading && !isError && matches.length === 0 ? <p className="text-sm text-muted-foreground">No assets available yet.</p> : null}
              {matches.map((asset, index) => (
                <div className={index === 0 ? "rounded-lg border border-primary bg-primary/5 p-4" : "rounded-lg border p-4"} key={asset.id}>
                  <div className="flex items-start gap-3">
                    <div className="grid size-9 place-items-center rounded-md bg-primary/10 text-primary">
                      <Bot className="size-4" />
                    </div>
                    <div className="min-w-0 flex-1 space-y-3">
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <div className="text-sm font-medium">{asset.title}</div>
                          <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{asset.summary}</p>
                        </div>
                        <StatusBadge status={asset.status} />
                      </div>
                      <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                        <Badge variant="outline">{userName(userById, asset.ownerUserId)}</Badge>
                        <Badge variant="outline">{formatAssetType(asset.assetType)}</Badge>
                        <Badge variant="outline">{asset.currentVersionId ? "Current" : "Draft"}</Badge>
                        <Badge variant="outline">{asset.usageCount} uses</Badge>
                      </div>
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          disabled={usage.isPending}
                          onClick={() => usage.mutate(asset.id, { onSuccess: () => toast.success("Asset usage recorded.") })}
                        >
                          Use Asset
                        </Button>
                        <Button asChild size="sm" variant="outline">
                          <Link to="/assets/$assetId" params={{ assetId: asset.id }}>Open Asset</Link>
                        </Button>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>Why this match</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              {["Asset tagged with customer workflow", "High usage and approval rating", "Owned by an active contributor"].map((item) => (
                <div className="flex items-center gap-2" key={item}>
                  <Check className="size-4 text-emerald-600 dark:text-emerald-400" />
                  <span>{item}</span>
                </div>
              ))}
            </CardContent>
          </Card>
        </aside>
      </div>
    </div>
  )
}

function rankAssets<T extends { title: string; summary: string; useCase: string | null; businessProcess: string | null; tagNames: string | null; status: string; usageCount: number }>(
  assets: T[],
  query: string,
) {
  const tokens = query.toLowerCase().split(/[^a-z0-9]+/).filter((token) => token.length > 2)
  if (!tokens.length) {
    return assets
  }

  return [...assets].sort((left, right) => scoreAsset(right, tokens, query) - scoreAsset(left, tokens, query))
}

function scoreAsset(asset: { title: string; summary: string; useCase: string | null; businessProcess: string | null; tagNames: string | null; status: string; usageCount: number }, tokens: string[], query: string) {
  const haystack = [
    asset.title,
    asset.summary,
    asset.useCase ?? "",
    asset.businessProcess ?? "",
    asset.tagNames ?? "",
    asset.status,
  ].join(" ").toLowerCase()
  const normalizedQuery = query.toLowerCase()
  let score = tokens.reduce((sum, token) => sum + (haystack.includes(token) ? 2 : 0), 0)
  if (haystack.includes("customer feedback") && normalizedQuery.includes("customer feedback")) score += 8
  if (asset.status === "APPROVED") score += normalizedQuery.includes("approved") ? 6 : 2
  score += Math.min(asset.usageCount, 3)
  return score
}
