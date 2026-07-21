import { useChat } from "@ai-sdk/react"
import type { UIMessage } from "ai"
import { AlertCircle, Copy, LoaderCircle, ShieldCheck } from "lucide-react"
import { Fragment, lazy, Suspense, useMemo, useRef, useState } from "react"
import { toast } from "sonner"

import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation"
import {
  Message,
  MessageAction,
  MessageActions,
  MessageContent,
} from "@/components/ai-elements/message"
import {
  PromptInput,
  PromptInputBody,
  PromptInputFooter,
  type PromptInputMessage,
  PromptInputSubmit,
  PromptInputTextarea,
} from "@/components/ai-elements/prompt-input"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { createAssistantTransport } from "@/features/assistant/api/chat-transport"

const SUGGESTIONS = [
  "Which approved workflow should I reuse for customer follow-up?",
  "Show capability assets that are missing a backup owner.",
  "What does OrgMemory currently know about onboarding workflows?",
]

const MessageResponse = lazy(async () => {
  const module = await import("@/components/ai-elements/message-response")
  return { default: module.MessageResponse }
})

function textFor(message: UIMessage) {
  return message.parts
    .filter((part) => part.type === "text")
    .map((part) => part.text)
    .join("\n")
}

function hasVisibleOutput(message: UIMessage) {
  return textFor(message).trim().length > 0
}

export function AssistantWorkspace() {
  const conversationId = useRef(crypto.randomUUID()).current
  const transport = useMemo(() => createAssistantTransport(conversationId), [conversationId])
  const [input, setInput] = useState("")
  const submitLock = useRef(false)
  const { messages, sendMessage, status, stop, error, clearError } = useChat({
    id: conversationId,
    transport,
  })

  const busy = status === "submitted" || status === "streaming"
  const latestMessage = messages.at(-1)
  const showWaiting =
    busy &&
    (latestMessage === undefined || latestMessage.role === "user" || !hasVisibleOutput(latestMessage))

  function send(text: string) {
    const message = text.trim()
    if (!message || busy || submitLock.current) return

    submitLock.current = true
    clearError()
    const turn = sendMessage({ text: message })
    setInput("")
    const releaseSubmitLock = () => {
      submitLock.current = false
    }
    void turn.then(releaseSubmitLock, releaseSubmitLock)
    return turn
  }

  function submit(message: PromptInputMessage) {
    return send(message.text)
  }

  const composer = (
    <PromptInput onSubmit={submit} className="w-full rounded-2xl shadow-none">
      <PromptInputBody>
        <PromptInputTextarea
          value={input}
          onChange={(event) => setInput(event.currentTarget.value)}
          placeholder="Ask about approved workflows, owners, or reusable capability assets…"
          className="min-h-14"
          autoFocus
        />
      </PromptInputBody>
      <PromptInputFooter className="justify-end">
        <PromptInputSubmit
          status={status}
          onStop={stop}
          disabled={!busy && !input.trim()}
          className="rounded-full"
        />
      </PromptInputFooter>
    </PromptInput>
  )

  if (messages.length === 0) {
    return (
      <section className="flex min-h-0 min-w-0 flex-1 flex-col items-center justify-center overflow-y-auto px-4 py-8">
        <div className="flex w-full max-w-2xl flex-col items-center gap-5">
          <div className="space-y-2 text-center">
            <div className="mx-auto grid size-10 place-items-center rounded-full border bg-muted/50">
              <ShieldCheck className="size-5" aria-hidden="true" />
            </div>
            <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">Ask your organization&apos;s memory</h1>
            <p className="text-sm text-muted-foreground">Answers use only the OrgMemory assets you can access.</p>
          </div>
          {composer}
          <div className="flex flex-wrap justify-center gap-2" aria-label="Suggested questions">
            {SUGGESTIONS.map((suggestion) => (
              <Button
                key={suggestion}
                type="button"
                variant="outline"
                size="sm"
                className="h-auto whitespace-normal text-left font-normal"
                onClick={() => {
                  setInput(suggestion)
                  void send(suggestion)?.catch(() => undefined)
                }}
              >
                {suggestion}
              </Button>
            ))}
          </div>
        </div>
      </section>
    )
  }

  return (
    <section className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden" aria-label="OrgMemory assistant">
      <Conversation className="min-h-0 flex-1">
        <ConversationContent className="mx-auto w-full max-w-3xl px-4 py-6">
          {messages.map((message) => {
            const text = textFor(message)
            if (!text.trim()) return null

            return (
              <Fragment key={message.id}>
                <Message from={message.role}>
                  <MessageContent>
                    <Suspense fallback={<p className="whitespace-pre-wrap">{text}</p>}>
                      <MessageResponse>{text}</MessageResponse>
                    </Suspense>
                  </MessageContent>
                  <MessageActions className={message.role === "user" ? "justify-end" : undefined}>
                    <MessageAction
                      label="Copy message"
                      tooltip="Copy message"
                      onClick={() =>
                        navigator.clipboard
                          .writeText(text)
                          .then(() => toast.success("Message copied"))
                          .catch(() => toast.error("Could not copy message"))
                      }
                    >
                      <Copy className="size-4" aria-hidden="true" />
                    </MessageAction>
                  </MessageActions>
                </Message>
              </Fragment>
            )
          })}
          {showWaiting ? (
            <Message from="assistant">
              <MessageContent>
                <span className="flex items-center gap-2 py-1 text-sm text-muted-foreground" role="status">
                  <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
                  Searching permitted memory…
                </span>
              </MessageContent>
            </Message>
          ) : null}
          {error ? (
            <Alert variant="destructive">
              <AlertCircle aria-hidden="true" />
              <AlertTitle>The assistant could not complete this turn</AlertTitle>
              <AlertDescription>Check the connection and try your question again.</AlertDescription>
            </Alert>
          ) : null}
        </ConversationContent>
        <ConversationScrollButton />
      </Conversation>
      <div className="mx-auto w-full max-w-3xl px-4 pb-4 md:pb-6">{composer}</div>
    </section>
  )
}
