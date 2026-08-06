import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { MailPlus, X } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import {
  clearanceLabel,
  CLEARANCES,
  type ClearanceValue,
} from "@/features/admin/admin-labels"
import {
  adminQuery,
  invalidateAdminData,
  organizationContextQueryOptions,
} from "@/features/admin/admin-queries"
import {
  createAdminInvitationMutation,
  listAdminInvitationsOptions,
  revokeAdminInvitationMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"

const STATUS_VARIANTS = {
  OPEN: { label: "Waiting for first sign-in", variant: "outline" as const },
  ACCEPTED: { label: "Signed in", variant: "secondary" as const },
  REVOKED: { label: "Withdrawn", variant: "outline" as const },
}

const NO_DEPARTMENT = "__none__"

/**
 * Invited addresses, kept apart from users on purpose.
 *
 * <p>An invitation has no user row behind it — it is a statement about somebody who has not
 * arrived. Folding the two lists together is what made a single "not linked" label mean two
 * unrelated things: never expected, or expected and not yet here.
 */
export function AdminInvitationsCard() {
  const queryClient = useQueryClient()
  const invitations = useQuery(adminQuery(listAdminInvitationsOptions()))
  const organization = useQuery(organizationContextQueryOptions())
  const create = useMutation(createAdminInvitationMutation())
  const revoke = useMutation(revokeAdminInvitationMutation())

  const [email, setEmail] = useState("")
  const [clearance, setClearance] = useState<ClearanceValue>("STANDARD")
  const [departmentId, setDepartmentId] = useState(NO_DEPARTMENT)

  const open = (invitations.data ?? []).filter((invitation) => invitation.status === "OPEN")
  const settled = (invitations.data ?? []).filter((invitation) => invitation.status !== "OPEN")

  async function invite() {
    try {
      await create.mutateAsync({
        body: {
          email,
          clearance,
          departmentId: departmentId === NO_DEPARTMENT ? undefined : departmentId,
        },
      })
      setEmail("")
      await invalidateAdminData(queryClient)
      toast.success(`${email} can now sign in`)
    } catch {
      toast.error("That address could not be invited")
    }
  }

  async function withdraw(invitationId: string, address: string) {
    try {
      await revoke.mutateAsync({ path: { invitationId } })
      await invalidateAdminData(queryClient)
      toast.success(`Withdrew the invitation for ${address}`)
    } catch {
      toast.error("The invitation could not be withdrawn")
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Invited addresses</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            void invite()
          }}
        >
          <Input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="name@example.com"
            aria-label="Email to invite"
            className="min-w-[260px] flex-1"
          />
          <Select
            value={clearance}
            onValueChange={(next: string) => setClearance(next as ClearanceValue)}
          >
            <SelectTrigger className="w-44" aria-label="Clearance on arrival">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {CLEARANCES.map((value) => (
                <SelectItem key={value} value={value}>
                  {clearanceLabel(value)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={departmentId} onValueChange={setDepartmentId}>
            <SelectTrigger className="w-48" aria-label="Department on arrival">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NO_DEPARTMENT}>No department</SelectItem>
              {(organization.data?.departments ?? []).map((department) => (
                <SelectItem key={department.id} value={department.id!}>
                  {department.name ?? "Unnamed department"}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button type="submit" disabled={!email || create.isPending}>
            <MailPlus className="size-4" aria-hidden />
            Invite
          </Button>
        </form>

        {invitations.isPending ? (
          <Skeleton className="h-20 w-full" />
        ) : open.length === 0 && settled.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Nobody is expected. An address must be invited before its first sign-in is accepted.
          </p>
        ) : (
          <ul className="divide-y rounded-lg border">
            {[...open, ...settled].map((invitation) => {
              const status = STATUS_VARIANTS[invitation.status as keyof typeof STATUS_VARIANTS]
              return (
                <li key={invitation.id} className="flex items-center justify-between gap-3 px-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{invitation.email}</p>
                    <p className="text-xs text-muted-foreground">
                      {clearanceLabel(invitation.clearance)}
                      {invitation.departmentId
                        ? ` · ${organization.data?.departments?.find(
                            (department) => department.id === invitation.departmentId,
                          )?.name ?? "Unknown department"}`
                        : " · No department"}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <Badge variant={status?.variant ?? "outline"} className="font-normal">
                      {status?.label ?? invitation.status}
                    </Badge>
                    {invitation.status === "OPEN" ? (
                      <Button
                        variant="ghost"
                        size="icon"
                        className="size-7"
                        aria-label={`Withdraw the invitation for ${invitation.email}`}
                        onClick={() => void withdraw(invitation.id!, invitation.email!)}
                      >
                        <X className="size-4" aria-hidden />
                      </Button>
                    ) : null}
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
