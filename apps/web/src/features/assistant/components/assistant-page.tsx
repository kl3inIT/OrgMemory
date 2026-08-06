import { useChat } from "@ai-sdk/react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { type SourceUrlUIPart, type UIMessage } from "ai"
import {
  Bot,
  Check,
  ChevronsUpDown,
  Copy,
  LoaderCircle,
  RotateCcw,
  ThumbsDown,
  ThumbsUp,
} from "lucide-react"
import { Fragment, type ReactNode, type RefObject } from "react"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
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
  ModelSelector,
  ModelSelectorContent,
  ModelSelectorEmpty,
  ModelSelectorGroup,
  ModelSelectorInput,
  ModelSelectorItem,
  ModelSelectorList,
  ModelSelectorLogo,
  ModelSelectorName,
  ModelSelectorTrigger,
} from "@/components/ai-elements/model-selector"
import {
  PromptInput,
  PromptInputBody,
  PromptInputFooter,
  type PromptInputMessage,
  PromptInputSubmit,
  PromptInputTextarea,
  PromptInputTools,
} from "@/components/ai-elements/prompt-input"
import { Source, Sources, SourcesContent, SourcesTrigger } from "@/components/ai-elements/sources"
import { Suggestion, Suggestions } from "@/components/ai-elements/suggestion"
import { Button } from "@/components/ui/button"
import { InputGroupButton } from "@/components/ui/input-group"
import { createAssistantTransport } from "@/features/assistant/api/chat-transport"
import {
  activityLabel,
  hasVisibleAssistantOutput,
  reduceSkillReceipts,
  type AssistantActivity,
  type AssistantSkillReceipt,
} from "@/features/assistant/assistant-activity"
import { AssistantAnswer } from "@/features/assistant/components/assistant-answer"
import { AssistantSkillActivity } from "@/features/assistant/components/assistant-skill-activity"
import { AssistantThinkingIndicator } from "@/features/assistant/components/assistant-thinking-indicator"
import {
  type AssistantSourceRef,
  AssistantSourcesPanel,
} from "@/features/assistant/components/assistant-sources-panel"
import { GovernedDocumentViewer } from "@/features/sources/components/governed-document-viewer"
import { useAssistantDraft } from "@/features/assistant/hooks/use-assistant-draft"
import { scopeActorQueryKey } from "@/features/session/actor-cache-key"
import { copyWithToast } from "@/lib/copy"
import {
  deleteAssistantAnswerFeedbackMutation,
  getAssistantConversationHistoryOptions,
  getAssistantMessageCitationsOptions,
  getAssistantModelOptionsOptions,
  listAssistantStartersOptions,
  listAssistantConversationsQueryKey,
  selectAssistantConversationModelMutation,
  setAssistantAnswerFeedbackMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type {
  AssistantConversationMessageView,
  AssistantConversationSummary,
  AssistantCitationResponse,
  AssistantModelOptionResponse,
} from "@/lib/hey-api"

type AnswerSentiment = "HELPFUL" | "NOT_HELPFUL"

function textFor(message: UIMessage) {
  return message.parts
    .filter((part) => part.type === "text")
    .map((part) => part.text)
    .join("\n")
}

function sourcesFor(message: UIMessage) {
  const sources: AssistantSourceRef[] = []
  const seenNumbers = new Set<number>()
  const seenIds = new Set<string>()
  for (const part of message.parts) {
    if (part.type !== "source-url") continue
    const citationNumber = citationNumberFor(part)
    const url = citationUrl(part.url)
    if (
      citationNumber === null ||
      url === null ||
      seenNumbers.has(citationNumber) ||
      seenIds.has(part.sourceId)
    ) {
      continue
    }
    seenNumbers.add(citationNumber)
    seenIds.add(part.sourceId)
    sources.push({
      id: part.sourceId,
      citationNumber,
      title: part.title ?? "Company knowledge",
      url,
      excerptUrl: url.replace(/\/content$/, "/excerpt"),
    })
  }
  return sources.sort((left, right) => left.citationNumber - right.citationNumber)
}

function citedSourcesFor(content: string, sources: AssistantSourceRef[]) {
  const sourceByNumber = new Map(
    sources.map((source) => [source.citationNumber, source]),
  )
  const cited: AssistantSourceRef[] = []
  const seen = new Set<number>()
  for (const match of content.matchAll(/\[(\d{1,3})]/g)) {
    const citationNumber = Number(match[1])
    const source = sourceByNumber.get(citationNumber)
    if (!source || seen.has(citationNumber)) continue
    seen.add(citationNumber)
    cited.push(source)
  }
  return cited
}

function containsControlCharacter(value: string) {
  return Array.from(value).some((character) => {
    const codePoint = character.codePointAt(0) ?? 0
    return codePoint <= 0x1f ||
      (codePoint >= 0x7f && codePoint <= 0x9f) ||
      /\p{Cf}/u.test(character)
  })
}

function citationNumberFor(source: SourceUrlUIPart) {
  const metadata = source.providerMetadata?.orgmemory
  if (!metadata || Array.isArray(metadata)) return null
  const number = metadata.citationNumber
  return typeof number === "number" && Number.isSafeInteger(number) && number > 0
    ? number
    : null
}

function citationUrl(rawUrl: string) {
  try {
    const baseUrl = new URL("https://orgmemory.invalid")
    const sourceUrl = new URL(rawUrl, baseUrl)
    if (
      sourceUrl.origin === baseUrl.origin &&
      /^\/api\/citations\/[0-9a-f-]{36}\/content$/i.test(sourceUrl.pathname) &&
      sourceUrl.search === "" &&
      sourceUrl.hash === ""
    ) {
      return sourceUrl.pathname
    }
  } catch {
    return null
  }
  return null
}

function citationExcerptUrl(rawUrl: string) {
  try {
    const baseUrl = new URL("https://orgmemory.invalid")
    const sourceUrl = new URL(rawUrl, baseUrl)
    if (
      sourceUrl.origin === baseUrl.origin &&
      /^\/api\/citations\/[0-9a-f-]{36}\/excerpt$/i.test(sourceUrl.pathname) &&
      sourceUrl.search === "" &&
      sourceUrl.hash === ""
    ) {
      return sourceUrl.pathname
    }
  } catch {
    return null
  }
  return null
}

function hydratedSources(citations: AssistantCitationResponse[] | undefined) {
  const sources: AssistantSourceRef[] = []
  const seen = new Set<number>()
  for (const citation of citations ?? []) {
    const citationNumber = citation.citationNumber
    const url = citation.contentUrl ? citationUrl(citation.contentUrl) : null
    const excerptUrl = citation.excerptUrl
      ? citationExcerptUrl(citation.excerptUrl)
      : null
    if (
      !citation.sourceId ||
      !citationNumber ||
      !Number.isSafeInteger(citationNumber) ||
      seen.has(citationNumber) ||
      !url ||
      !excerptUrl
    ) {
      continue
    }
    seen.add(citationNumber)
    sources.push({
      id: citation.sourceId,
      citationNumber,
      title: citation.title?.trim() || "Company knowledge",
      url,
      excerptUrl,
    })
  }
  return sources.sort((left, right) => left.citationNumber - right.citationNumber)
}

function isAssistantActivity(value: unknown): value is AssistantActivity {
  if (!value || typeof value !== "object") return false
  const activity = value as Record<string, unknown>
  const validOrdinal =
    activity.skillOrdinal === undefined ||
    activity.skillOrdinal === null ||
    (typeof activity.skillOrdinal === "number" &&
      Number.isSafeInteger(activity.skillOrdinal) &&
      activity.skillOrdinal > 0)
  const validTitle =
    activity.skillTitle === undefined ||
    activity.skillTitle === null ||
    (typeof activity.skillTitle === "string" &&
      activity.skillTitle.length > 0 &&
      Array.from(activity.skillTitle).length <= 80 &&
      !containsControlCharacter(activity.skillTitle))
  const validIdentity =
    activity.skillTitle === undefined ||
    activity.skillTitle === null ||
    (activity.phase === "SKILL_ACTIVATION" &&
      activity.state === "COMPLETE" &&
      typeof activity.skillOrdinal === "number")
  return (
    (activity.phase === "RETRIEVAL" ||
      activity.phase === "GENERATION" ||
      activity.phase === "SKILL_DISCOVERY" ||
      activity.phase === "SKILL_ACTIVATION" ||
      activity.phase === "SKILL_RESOURCE") &&
    (activity.state === "ACTIVE" ||
      activity.state === "COMPLETE" ||
      activity.state === "FAILED") &&
    (activity.evidenceCount === undefined ||
      activity.evidenceCount === null ||
      (typeof activity.evidenceCount === "number" &&
        Number.isSafeInteger(activity.evidenceCount) &&
        activity.evidenceCount >= 0)) &&
    validOrdinal &&
    validTitle &&
    validIdentity
  )
}

function CitationHydration({
  message,
  actorKey,
  children,
}: {
  message: UIMessage
  actorKey: string
  children: (value: {
    sources: AssistantSourceRef[]
    unavailable: boolean
    anchorRef: RefObject<HTMLDivElement | null>
  }) => ReactNode
}) {
  const anchorRef = useRef<HTMLDivElement>(null)
  const liveSources = sourcesFor(message)
  const needsHydration =
    message.role === "assistant" &&
    liveSources.length === 0 &&
    /\[\d{1,3}]/.test(textFor(message))
  const [nearViewport, setNearViewport] = useState(false)

  useEffect(() => {
    if (!needsHydration) return
    const target = anchorRef.current
    if (!target || typeof IntersectionObserver === "undefined") {
      setNearViewport(true)
      return
    }
    const observer = new IntersectionObserver(
      ([entry]) => entry?.isIntersecting && setNearViewport(true),
      { rootMargin: "240px" },
    )
    observer.observe(target)
    return () => observer.disconnect()
  }, [needsHydration])

  const definition = getAssistantMessageCitationsOptions({
    path: { messageId: message.id },
  })
  const query = useQuery({
    ...definition,
    queryKey: scopeActorQueryKey(definition.queryKey, actorKey),
    enabled: needsHydration && nearViewport,
    retry: false,
    staleTime: 0,
  })
  return children({
    sources: liveSources.length > 0 ? liveSources : hydratedSources(query.data),
    unavailable: needsHydration && query.isError,
    anchorRef,
  })
}

function greeting() {
  const hour = new Date().getHours()
  if (hour < 12) return "Good morning"
  if (hour < 18) return "Good afternoon"
  return "Good evening"
}

function historyMessage(
  conversationId: string,
  message: AssistantConversationMessageView,
  index: number,
): UIMessage {
  return {
    id: message.id ?? `${conversationId}-${index}`,
    role: message.role === "ASSISTANT" ? "assistant" : "user",
    parts: [{ type: "text", text: message.content ?? "" }],
  }
}

function AssistantModelPicker({
  options,
  selectedId,
  disabled,
  loading,
  onSelect,
}: {
  options: AssistantModelOptionResponse[]
  selectedId?: string
  disabled: boolean
  loading: boolean
  onSelect: (model: AssistantModelOptionResponse) => void
}) {
  const [open, setOpen] = useState(false)
  const selected =
    options.find((option) => option.id === selectedId) ??
    options.find((option) => option.defaultChoice)
  const groups = Object.entries(
    options.reduce<Record<string, AssistantModelOptionResponse[]>>((current, option) => {
      const gateway = option.gatewayLabel ?? "Organization models"
      current[gateway] = [...(current[gateway] ?? []), option]
      return current
    }, {}),
  )

  return (
    <ModelSelector open={open} onOpenChange={setOpen}>
      <ModelSelectorTrigger asChild>
        <InputGroupButton
          type="button"
          size="sm"
          disabled={disabled || loading || options.length === 0}
          aria-label={`Choose model${selected?.displayName ? `, current model ${selected.displayName}` : ""}`}
          className="max-w-48 rounded-full px-2.5 text-content-secondary hover:text-content-primary"
        >
          {selected?.provider && selected.provider !== "custom" ? (
            <ModelSelectorLogo provider={selected.provider} className="size-4" />
          ) : (
            <Bot className="size-4" aria-hidden="true" />
          )}
          <span className="truncate">
            {loading ? "Loading models…" : selected?.displayName ?? "Organization default"}
          </span>
          <ChevronsUpDown className="size-3.5 opacity-60" aria-hidden="true" />
        </InputGroupButton>
      </ModelSelectorTrigger>
      <ModelSelectorContent title="Choose an Assistant model">
        <ModelSelectorInput placeholder="Search models…" />
        <ModelSelectorList>
          <ModelSelectorEmpty>No available model matches.</ModelSelectorEmpty>
          {groups.map(([gateway, models]) => (
            <ModelSelectorGroup key={gateway} heading={gateway}>
              {(models ?? []).map((model) => {
                const active = model.defaultChoice ? !selectedId : model.id === selectedId
                return (
                  <ModelSelectorItem
                    key={model.id ?? "organization-default"}
                    value={`${model.displayName ?? model.modelId ?? "model"} ${model.modelId ?? ""} ${gateway}`}
                    aria-checked={active}
                    onSelect={() => {
                      onSelect(model)
                      setOpen(false)
                    }}
                  >
                    {model.provider && model.provider !== "custom" ? (
                      <ModelSelectorLogo provider={model.provider} className="size-4" />
                    ) : (
                      <Bot className="size-4 text-muted-foreground" aria-hidden="true" />
                    )}
                    <span className="min-w-0 flex-1">
                      <ModelSelectorName className="block font-medium">
                        {model.displayName ?? model.modelId}
                      </ModelSelectorName>
                      <span className="block truncate text-xs text-muted-foreground">
                        {model.defaultChoice ? "Organization default" : model.modelId}
                      </span>
                    </span>
                    <Check
                      className={`size-4 ${active ? "opacity-100" : "opacity-0"}`}
                      aria-hidden="true"
                    />
                  </ModelSelectorItem>
                )
              })}
            </ModelSelectorGroup>
          ))}
        </ModelSelectorList>
      </ModelSelectorContent>
    </ModelSelector>
  )
}

export function AssistantPage({
  conversationId,
  actorKey,
  onConversationIdChange,
}: {
  conversationId?: string
  actorKey: string
  onConversationIdChange: (conversationId: string) => void
}) {
  const queryClient = useQueryClient()
  const conversationListQueryKey = useMemo(
    () => scopeActorQueryKey(listAssistantConversationsQueryKey(), actorKey),
    [actorKey],
  )
  const actorKeyRef = useRef(actorKey)
  const conversationIdRef = useRef(conversationId)
  const modelActivationIdRef = useRef<string | undefined>(undefined)
  const [selectedModelActivationId, setSelectedModelActivationId] = useState<string>()
  const locallyCreatedConversationRef = useRef<string | undefined>(undefined)
  const nextTitleRef = useRef("New conversation")
  const onConversationIdChangeRef = useRef(onConversationIdChange)
  useEffect(() => {
    onConversationIdChangeRef.current = onConversationIdChange
  }, [onConversationIdChange])
  const transport = useMemo(
    () =>
      createAssistantTransport({
        conversationId: () => conversationIdRef.current,
        modelActivationId: () => modelActivationIdRef.current,
        onConversationId: (nextConversationId) => {
          if (!conversationIdRef.current) {
            locallyCreatedConversationRef.current = nextConversationId
            queryClient.setQueryData<AssistantConversationSummary[]>(
              conversationListQueryKey,
              (current = []) => {
                const conversations = current
                if (
                  conversations.some(
                    (item) => item.id === nextConversationId,
                  )
                ) {
                  return conversations
                }
                return [
                  {
                    id: nextConversationId,
                    title: nextTitleRef.current,
                    lastActivityAt: new Date().toISOString(),
                    messageCount: 1,
                  },
                  ...conversations,
                ]
              },
            )
          }
          conversationIdRef.current = nextConversationId
          onConversationIdChangeRef.current(nextConversationId)
        },
      }),
    [conversationListQueryKey, queryClient],
  )
  const { text, setText, clear: clearDraft } = useAssistantDraft(
    actorKey,
    conversationId,
  )
  const [feedbackByMessage, setFeedbackByMessage] = useState<
    Record<string, AnswerSentiment | undefined>
  >({})
  const [sourcePanel, setSourcePanel] = useState<{
    messageId: string
    sources: AssistantSourceRef[]
    citedSourceIds: string[]
    selectedSourceId: string
  } | null>(null)
  const [previewSource, setPreviewSource] = useState<AssistantSourceRef | null>(null)
  const [activity, setActivity] = useState<AssistantActivity | null>(null)
  const [skillReceipts, setSkillReceipts] = useState<AssistantSkillReceipt[]>([])
  const [awaitingVisibleAnswer, setAwaitingVisibleAnswer] = useState(false)
  const [finishedWithoutAnswer, setFinishedWithoutAnswer] = useState(false)
  const activityAcceptingRef = useRef(false)
  const visibleOutputRef = useRef(false)
  const turnOrdinalRef = useRef(0)
  const submitLock = useRef(false)
  const {
    messages,
    sendMessage,
    setMessages,
    status,
    stop: stopChat,
    error,
    clearError,
  } = useChat({
    transport,
    // Coalesce high-frequency SSE deltas before React/Streamdown reparses the
    // growing answer. Without this, a fast provider can monopolize the browser
    // main thread by publishing one full-transcript render per token chunk.
    throttle: 100,
    onData: (part) => {
      if (
        !activityAcceptingRef.current ||
        part.type !== "data-assistantActivity" ||
        !isAssistantActivity(part.data)
      ) return
      const nextActivity = part.data
      setActivity(nextActivity)
      setSkillReceipts((current) => reduceSkillReceipts(current, nextActivity))
    },
    onFinish: ({ message, isAbort, isError }) => {
      const finishedTurn = turnOrdinalRef.current
      activityAcceptingRef.current = false
      if (isAbort || isError) {
        setActivity(null)
        setAwaitingVisibleAnswer(false)
        setSkillReceipts([])
      } else if (!hasVisibleAssistantOutput(message)) {
        window.setTimeout(() => {
          if (
            turnOrdinalRef.current !== finishedTurn ||
            visibleOutputRef.current
          ) return
          setActivity(null)
          setAwaitingVisibleAnswer(false)
          setSkillReceipts([])
          setFinishedWithoutAnswer(true)
        }, 0)
      }
      const invalidations = [
        queryClient.invalidateQueries({
          queryKey: conversationListQueryKey,
        }),
      ]
      const completedConversationId = conversationIdRef.current
      if (completedConversationId) {
        const completedHistoryOptions = getAssistantConversationHistoryOptions({
          path: { conversationId: completedConversationId },
        })
        invalidations.push(
          queryClient.invalidateQueries({
            queryKey: scopeActorQueryKey(
              completedHistoryOptions.queryKey,
              actorKey,
            ),
            refetchType: "none",
          }),
        )
      }
      void Promise.all(invalidations)
    },
    onError: () => {
      activityAcceptingRef.current = false
      setActivity(null)
      setAwaitingVisibleAnswer(false)
      setSkillReceipts([])
    },
  })
  const stop = useCallback(() => {
    turnOrdinalRef.current += 1
    activityAcceptingRef.current = false
    visibleOutputRef.current = false
    setActivity(null)
    setAwaitingVisibleAnswer(false)
    setFinishedWithoutAnswer(false)
    setSkillReceipts([])
    stopChat()
  }, [stopChat])
  const historyOptions = getAssistantConversationHistoryOptions({
    path: { conversationId: conversationId ?? "00000000-0000-0000-0000-000000000000" },
  })
  const history = useQuery({
    ...historyOptions,
    queryKey: scopeActorQueryKey(historyOptions.queryKey, actorKey),
    enabled: Boolean(conversationId),
  })
  const starterOptions = listAssistantStartersOptions()
  const starters = useQuery({
    ...starterOptions,
    queryKey: scopeActorQueryKey(starterOptions.queryKey, actorKey),
  })
  const modelOptionsDefinition = getAssistantModelOptionsOptions({
    query: { conversationId },
  })
  const modelOptions = useQuery({
    ...modelOptionsDefinition,
    queryKey: scopeActorQueryKey(modelOptionsDefinition.queryKey, actorKey),
  })
  const selectModel = useMutation({
    ...selectAssistantConversationModelMutation(),
    onError: () => {
      setSelectedModelActivationId(modelOptions.data?.selectedModelActivationId)
      modelActivationIdRef.current = modelOptions.data?.selectedModelActivationId
      toast.error("The model selection could not be saved")
    },
  })
  const saveFeedback = useMutation({
    ...setAssistantAnswerFeedbackMutation(),
    onSuccess: (_, variables) => {
      setFeedbackByMessage((current) => ({
        ...current,
        [variables.path.messageId]: variables.body.sentiment,
      }))
    },
    onError: () => toast.error("Answer feedback could not be saved"),
  })
  const removeFeedback = useMutation({
    ...deleteAssistantAnswerFeedbackMutation(),
    onSuccess: (_, variables) => {
      setFeedbackByMessage((current) => ({
        ...current,
        [variables.path.messageId]: undefined,
      }))
    },
    onError: () => toast.error("Answer feedback could not be removed"),
  })

  useEffect(() => {
    const actorChanged = actorKeyRef.current !== actorKey
    if (!actorChanged && conversationIdRef.current === conversationId) return
    stop()
    actorKeyRef.current = actorKey
    conversationIdRef.current = conversationId
    modelActivationIdRef.current = undefined
    locallyCreatedConversationRef.current = undefined
    setSelectedModelActivationId(undefined)
    setSourcePanel(null)
    setPreviewSource(null)
    setFeedbackByMessage({})
    setActivity(null)
    setSkillReceipts([])
    setAwaitingVisibleAnswer(false)
    setFinishedWithoutAnswer(false)
    setMessages([])
  }, [actorKey, conversationId, setMessages, stop])

  useEffect(() => {
    if (!modelOptions.data) return
    const selected = modelOptions.data.selectedModelActivationId
    setSelectedModelActivationId(selected)
    modelActivationIdRef.current = selected
  }, [modelOptions.data])

  useEffect(() => {
    if (
      !conversationId ||
      !history.data ||
      locallyCreatedConversationRef.current === conversationId
    ) {
      return
    }
    setMessages(
      history.data.map((message, index) =>
        historyMessage(conversationId, message, index),
      ),
    )
    setFeedbackByMessage(
      Object.fromEntries(
        history.data
          .filter(
            (message) =>
              message.id &&
              (message.feedback === "HELPFUL" ||
                message.feedback === "NOT_HELPFUL"),
          )
          .map((message) => [message.id as string, message.feedback as AnswerSentiment]),
      ),
    )
  }, [conversationId, history.data, setMessages])
  const busy = status === "submitted" || status === "streaming"
  const latestMessage = messages.at(-1)
  const currentAssistant = latestMessage?.role === "assistant" ? latestMessage : undefined
  const currentAssistantVisible = currentAssistant
    ? hasVisibleAssistantOutput(currentAssistant)
    : false
  visibleOutputRef.current = currentAssistantVisible

  useEffect(() => {
    if (!awaitingVisibleAnswer || !currentAssistantVisible) return
    setAwaitingVisibleAnswer(false)
    setActivity(null)
  }, [awaitingVisibleAnswer, currentAssistantVisible])

  const retryText = [...messages]
    .reverse()
    .find((message) => message.role === "user")
  const retryMessage = retryText ? textFor(retryText) : ""
  const showWaiting = awaitingVisibleAnswer && !currentAssistantVisible
  const showThinking = showWaiting
  const openSources = useCallback(
    (
      messageId: string,
      sources: AssistantSourceRef[],
      citedSources: AssistantSourceRef[],
      sourceId: string,
    ) => {
      setSourcePanel({
        messageId,
        sources,
        citedSourceIds: citedSources.map((source) => source.id),
        selectedSourceId: sourceId,
      })
    },
    [],
  )

  function send(rawMessage: string, clearComposer = true) {
    const message = rawMessage.trim()
    if (
      !message ||
      busy ||
      modelOptions.isPending ||
      selectModel.isPending ||
      submitLock.current
    ) return

    submitLock.current = true
    nextTitleRef.current =
      message.length <= 80 ? message : `${message.slice(0, 77)}...`
    clearError()
    turnOrdinalRef.current += 1
    activityAcceptingRef.current = true
    visibleOutputRef.current = false
    setActivity(null)
    setSkillReceipts([])
    setFinishedWithoutAnswer(false)
    setAwaitingVisibleAnswer(true)
    const turn = sendMessage({ text: message })
    if (clearComposer) clearDraft()
    const release = () => {
      submitLock.current = false
    }
    void turn.then(release, release)
    return turn
  }

  function submit(message: PromptInputMessage) {
    return send(message.text)
  }

  function toggleFeedback(messageId: string, sentiment: AnswerSentiment) {
    if (busy || saveFeedback.isPending || removeFeedback.isPending) return
    if (feedbackByMessage[messageId] === sentiment) {
      removeFeedback.mutate({ path: { messageId } })
      return
    }
    saveFeedback.mutate({
      path: { messageId },
      body: { sentiment },
    })
  }

  function chooseModel(model: AssistantModelOptionResponse) {
    if (busy || selectModel.isPending) return
    const next = model.defaultChoice ? undefined : model.id
    setSelectedModelActivationId(next)
    modelActivationIdRef.current = next
    const activeConversationId = conversationIdRef.current
    if (!activeConversationId) return
    selectModel.mutate({
      path: { conversationId: activeConversationId },
      body: { modelActivationId: next },
    })
  }

  const composer = (
    <PromptInput
      onSubmit={submit}
      className="w-full [&_[data-slot=input-group]]:rounded-3xl [&_[data-slot=input-group]]:border-border-subtle [&_[data-slot=input-group]]:bg-assistant-composer [&_[data-slot=input-group]]:shadow-sm [&_[data-slot=input-group]]:ring-0"
    >
      <PromptInputBody>
        <PromptInputTextarea
          value={text}
          onChange={(event) => setText(event.currentTarget.value)}
          placeholder="Ask OrgMemory…"
          autoFocus
          maxLength={4_000}
          className="min-h-12"
        />
      </PromptInputBody>
      <PromptInputFooter>
        <PromptInputTools>
          <AssistantModelPicker
            options={modelOptions.data?.options ?? []}
            selectedId={selectedModelActivationId}
            disabled={busy || selectModel.isPending}
            loading={modelOptions.isPending}
            onSelect={chooseModel}
          />
        </PromptInputTools>
        <PromptInputSubmit
          status={status}
          onStop={stop}
          disabled={!busy && (!text.trim() || modelOptions.isPending || selectModel.isPending)}
          className="rounded-full"
        />
      </PromptInputFooter>
    </PromptInput>
  )

  const isSwitchingScope =
    actorKeyRef.current !== actorKey ||
    (conversationId !== undefined && conversationIdRef.current !== conversationId)

  if (
    conversationId &&
    (isSwitchingScope || (history.isPending && messages.length === 0))
  ) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex min-w-0 flex-1 items-center justify-center gap-2 text-sm text-content-secondary"
      >
        <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
        Loading conversation…
      </div>
    )
  }

  if (
    conversationId &&
    !isSwitchingScope &&
    history.isError &&
    messages.length === 0
  ) {
    return (
      <div
        role="alert"
        className="flex min-w-0 flex-1 flex-col items-center justify-center gap-3 px-5 text-center"
      >
        <p className="text-body text-content-secondary">
          This conversation could not be loaded.
        </p>
        <Button variant="outline" onClick={() => void history.refetch()}>
          <RotateCcw className="size-4" aria-hidden="true" />
          Try again
        </Button>
      </div>
    )
  }

  if (messages.length === 0) {
    return (
      <div className="flex min-w-0 flex-1 overflow-y-auto px-5">
        <div className="mx-auto flex w-full max-w-2xl flex-col justify-center py-16 sm:py-24">
          <div className="mb-7 max-w-xl">
            <p className="mb-2 text-sm font-medium text-content-secondary">{greeting()}</p>
            <h1 className="text-page-title text-content-primary">What can we help you move forward?</h1>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              Ask about company knowledge, policies, or the next step in a process.
            </p>
          </div>
          {composer}
          <Suggestions className="mt-4 flex-wrap justify-start gap-2 whitespace-normal">
          {(starters.data ?? []).map((starter) => (
            <Suggestion
              key={starter.id}
              suggestion={starter.prompt ?? ""}
              className="border-border-subtle bg-transparent text-content-secondary hover:bg-surface-subtle hover:text-content-primary"
              onClick={(value) => {
                void send(value)?.catch(() => undefined)
              }}
            />
          ))}
          </Suggestions>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 overflow-hidden">
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <Conversation className="min-h-0 flex-1">
          <ConversationContent className="mx-auto w-full max-w-3xl gap-7 px-4 py-6">
            {messages.map((message, index) => (
              <Fragment key={message.id}>
                {currentAssistant?.id === message.id ? (
                  <AssistantSkillActivity
                    receipts={skillReceipts}
                    settled={currentAssistantVisible}
                  />
                ) : null}
                <CitationHydration message={message} actorKey={actorKey}>
                {({ sources, unavailable, anchorRef }) => {
              const content = textFor(message)
              const citedSources = citedSourcesFor(content, sources)
              const precedingUserMessage = messages[index - 1]
              const retryPrompt =
                message.role === "assistant" &&
                precedingUserMessage?.role === "user"
                  ? textFor(precedingUserMessage)
                  : ""
              const selectedFeedback = feedbackByMessage[message.id]
              const feedbackPending =
                saveFeedback.isPending || removeFeedback.isPending
              if (!content.trim() && sources.length === 0) return null

              return (
                <Message from={message.role} ref={anchorRef}>
                  {content.trim() ? (
                    <MessageContent className="text-body">
                      <AssistantAnswer
                        content={content}
                        sources={citedSources}
                        showEvidenceDisclaimer={message.role === "assistant"}
                        onOpenSource={(sourceId) => {
                          const source = sources.find((candidate) => candidate.id === sourceId)
                          if (source) setPreviewSource(source)
                        }}
                      />
                    </MessageContent>
                  ) : null}
                  {citedSources.length > 0 ? (
                    <Sources>
                      <SourcesTrigger
                        count={citedSources.length}
                        onClick={() =>
                          openSources(
                            message.id,
                            sources,
                            citedSources,
                            citedSources[0]?.id ?? sources[0]?.id ?? "",
                          )
                        }
                      />
                      <SourcesContent>
                        {citedSources.map((source) => (
                          <Source
                            key={source.id}
                            href={source.url}
                            title={source.title}
                            target="_self"
                            onClick={(event) => {
                              event.preventDefault()
                              setPreviewSource(source)
                            }}
                          />
                        ))}
                      </SourcesContent>
                    </Sources>
                  ) : null}
                  {unavailable ? (
                    <p role="status" className="text-supporting text-content-muted">
                      Sources are temporarily unavailable. The saved answer is unchanged.
                    </p>
                  ) : null}
                  {content.trim() ? (
                    <MessageActions className={message.role === "user" ? "justify-end" : undefined}>
                      <MessageAction
                        label="Copy message"
                        tooltip="Copy message"
                        onClick={() => void copyWithToast(content, "Message")}
                      >
                        <Copy className="size-4" />
                      </MessageAction>
                      {message.role === "assistant" ? (
                        <>
                          <MessageAction
                            label="Retry answer with fresh evidence"
                            tooltip="Retry with fresh evidence"
                            disabled={!retryPrompt || busy}
                            onClick={() => {
                              void send(retryPrompt, false)?.catch(() => undefined)
                            }}
                          >
                            <RotateCcw className="size-4" />
                          </MessageAction>
                          <MessageAction
                            label="Mark answer helpful"
                            tooltip="Helpful"
                            aria-pressed={selectedFeedback === "HELPFUL"}
                            variant={selectedFeedback === "HELPFUL" ? "secondary" : "ghost"}
                            disabled={busy || feedbackPending}
                            onClick={() => toggleFeedback(message.id, "HELPFUL")}
                          >
                            <ThumbsUp className="size-4" />
                          </MessageAction>
                          <MessageAction
                            label="Mark answer not helpful"
                            tooltip="Not helpful"
                            aria-pressed={selectedFeedback === "NOT_HELPFUL"}
                            variant={selectedFeedback === "NOT_HELPFUL" ? "secondary" : "ghost"}
                            disabled={busy || feedbackPending}
                            onClick={() => toggleFeedback(message.id, "NOT_HELPFUL")}
                          >
                            <ThumbsDown className="size-4" />
                          </MessageAction>
                        </>
                      ) : null}
                    </MessageActions>
                  ) : null}
                </Message>
              )
                }}
                </CitationHydration>
              </Fragment>
            ))}
            {!currentAssistant ? (
              <AssistantSkillActivity receipts={skillReceipts} settled={false} />
            ) : null}
            {showThinking ? (
              <Message from="assistant">
                <MessageContent>
                  <AssistantThinkingIndicator label={activityLabel(activity)} />
                </MessageContent>
              </Message>
            ) : null}
            {error || finishedWithoutAnswer ? (
              <div
                role="alert"
                className="flex items-center justify-between gap-4 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3"
              >
                <p className="text-sm text-destructive">
                  {finishedWithoutAnswer
                    ? "OrgMemory completed the turn without an answer."
                    : "OrgMemory could not complete this turn."}
                </p>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={!retryMessage || busy}
                  onClick={() => {
                    void send(retryMessage, false)?.catch(() => undefined)
                  }}
                >
                  <RotateCcw className="size-4" aria-hidden="true" />
                  Retry
                </Button>
              </div>
            ) : null}
          </ConversationContent>
          <ConversationScrollButton />
        </Conversation>
        <div className="mx-auto w-full max-w-3xl px-4 pb-6">{composer}</div>
      </div>
      <AssistantSourcesPanel
        open={sourcePanel !== null}
        sources={sourcePanel?.sources ?? []}
        citedSourceIds={sourcePanel?.citedSourceIds ?? []}
        selectedSourceId={sourcePanel?.selectedSourceId ?? null}
        onClose={() => setSourcePanel(null)}
        onSelect={(selectedSourceId) =>
          setSourcePanel((current) => (current ? { ...current, selectedSourceId } : current))
        }
        onPreview={setPreviewSource}
      />
      <GovernedDocumentViewer
        target={previewSource ? { kind: "citation", source: previewSource } : null}
        onOpenChange={(open) => !open && setPreviewSource(null)}
      />
    </div>
  )
}
