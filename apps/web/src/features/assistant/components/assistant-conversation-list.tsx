import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link, useNavigate } from "@tanstack/react-router"
import { Ellipsis, MessageSquare, Pencil, Plus, Trash2 } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuAction,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import { scopeActorQueryKey } from "@/features/session/actor-cache-key"
import {
  deleteAssistantConversationMutation,
  listAssistantConversationsOptions,
  renameAssistantConversationMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AssistantConversationSummary } from "@/lib/hey-api"

export function AssistantConversationList({
  activeConversationId,
  actorKey,
}: {
  activeConversationId?: string
  actorKey: string
}) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const conversationOptions = listAssistantConversationsOptions()
  const conversationQueryKey = scopeActorQueryKey(
    conversationOptions.queryKey,
    actorKey,
  )
  const conversations = useQuery({
    ...conversationOptions,
    queryKey: conversationQueryKey,
  })
  const [renamingId, setRenamingId] = useState<string | null>(null)
  const [draftTitle, setDraftTitle] = useState("")
  const [deleteCandidate, setDeleteCandidate] =
    useState<AssistantConversationSummary | null>(null)
  const rename = useMutation({
    ...renameAssistantConversationMutation(),
    onSuccess: async () => {
      setRenamingId(null)
      await queryClient.invalidateQueries({
        queryKey: conversationQueryKey,
      })
    },
    onError: () => toast.error("The conversation could not be renamed"),
  })
  const remove = useMutation({
    ...deleteAssistantConversationMutation(),
    onSuccess: async (_, variables) => {
      setDeleteCandidate(null)
      queryClient.setQueryData(
        conversationQueryKey,
        (current = []) =>
          current.filter(
            (conversation) =>
              conversation.id !== variables.path.conversationId,
          ),
      )
      if (activeConversationId === variables.path.conversationId) {
        await navigate({ to: "/", search: {} })
      }
    },
    onError: () => toast.error("The conversation could not be deleted"),
  })

  function startRename(conversation: AssistantConversationSummary) {
    if (!conversation.id) return
    setRenamingId(conversation.id)
    setDraftTitle(conversation.title ?? "Untitled conversation")
  }

  function submitRename(conversationId: string) {
    const title = draftTitle.trim()
    if (!title || rename.isPending) return
    rename.mutate({
      path: { conversationId },
      body: { title },
    })
  }

  const visible = conversations.data ?? []

  return (
    <>
      <SidebarGroup className="min-h-0 flex-1 pt-0 group-data-[collapsible=icon]:hidden">
        <SidebarGroupLabel>Recent</SidebarGroupLabel>
        <SidebarGroupContent className="min-h-0 overflow-y-auto">
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton asChild className="font-medium">
                <Link to="/" search={{}}>
                  <Plus aria-hidden="true" />
                  <span>New conversation</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
            {conversations.isPending ? (
              <li className="px-2 py-3 text-xs text-content-muted">Loading conversations…</li>
            ) : null}
            {conversations.isError ? (
              <li className="px-2 py-3 text-xs text-status-danger-content">
                Conversation history is unavailable.
              </li>
            ) : null}
            {!conversations.isPending &&
            !conversations.isError &&
            visible.length === 0 ? (
              <li className="px-2 py-3 text-xs text-content-muted">
                Your recent conversations will appear here.
              </li>
            ) : null}
            {visible.map((conversation) => {
              if (!conversation.id) return null
              const conversationId = conversation.id
              const isRenaming = renamingId === conversationId
              return (
                <SidebarMenuItem key={conversationId}>
                  {isRenaming ? (
                    <Input
                      autoFocus
                      value={draftTitle}
                      maxLength={120}
                      aria-label="Conversation title"
                      className="h-8"
                      onChange={(event) => setDraftTitle(event.currentTarget.value)}
                      onBlur={() => setRenamingId(null)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          event.preventDefault()
                          submitRename(conversationId)
                        }
                        if (event.key === "Escape") setRenamingId(null)
                      }}
                    />
                  ) : (
                    <>
                      <SidebarMenuButton
                        asChild
                        isActive={activeConversationId === conversationId}
                        tooltip={conversation.title}
                        className="pr-8"
                      >
                        <Link to="/" search={{ chat: conversationId }}>
                          <MessageSquare aria-hidden="true" />
                          <span>{conversation.title ?? "Untitled conversation"}</span>
                        </Link>
                      </SidebarMenuButton>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <SidebarMenuAction
                            showOnHover
                            aria-label={`Actions for ${conversation.title ?? "conversation"}`}
                          >
                            <Ellipsis aria-hidden="true" />
                          </SidebarMenuAction>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent side="right" align="start">
                          <DropdownMenuItem onSelect={() => startRename(conversation)}>
                            <Pencil aria-hidden="true" />
                            Rename
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            variant="destructive"
                            onSelect={() => setDeleteCandidate(conversation)}
                          >
                            <Trash2 aria-hidden="true" />
                            Delete
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </>
                  )}
                </SidebarMenuItem>
              )
            })}
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>

      <AlertDialog
        open={deleteCandidate !== null}
        onOpenChange={(open: boolean) => {
          if (!open && !remove.isPending) setDeleteCandidate(null)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this conversation?</AlertDialogTitle>
            <AlertDialogDescription>
              The transcript and its bounded model context will be permanently deleted. This
              cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={remove.isPending}>Keep it</AlertDialogCancel>
            <AlertDialogAction
              disabled={!deleteCandidate?.id || remove.isPending}
              onClick={() => {
                if (!deleteCandidate?.id) return
                remove.mutate({
                  path: { conversationId: deleteCandidate.id },
                })
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
