import { useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { connectionLabel, principalName } from "@/features/admin/admin-labels"
import type { AdminSourcePrincipalResponse, AdminUserResponse } from "@/lib/hey-api"

/**
 * Confirming is an administrator vouching for an identity, so the dialog states what
 * the source observed next to the internal user being vouched for — the two facts the
 * decision rests on.
 */
export function ConfirmMappingDialog({
  principal,
  users,
  pending,
  onConfirm,
  onOpenChange,
}: {
  principal?: AdminSourcePrincipalResponse
  users: AdminUserResponse[]
  pending: boolean
  onConfirm: (appUserId: string) => void
  onOpenChange: (open: boolean) => void
}) {
  const [appUserId, setAppUserId] = useState<string>()

  function close(open: boolean) {
    if (!open) setAppUserId(undefined)
    onOpenChange(open)
  }

  return (
    <Dialog open={Boolean(principal)} onOpenChange={close}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Confirm identity</DialogTitle>
          <DialogDescription>
            This records an administrator-confirmed link. It resolves grants the source already sealed; it
            does not create new access.
          </DialogDescription>
        </DialogHeader>

        {principal ? (
          <dl className="grid gap-2 rounded-md border p-3 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Principal</dt>
              <dd className="truncate font-medium">{principalName(principal)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Connection</dt>
              <dd className="truncate">
                {connectionLabel(principal.sourceSystem, principal.sourceConnectionKey)}
              </dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Observed email</dt>
              <dd className="truncate">{principal.observedEmail || "None reported"}</dd>
            </div>
          </dl>
        ) : null}

        <div className="space-y-2">
          <label className="text-sm font-medium" htmlFor="confirm-mapping-user">
            Internal user
          </label>
          <Select value={appUserId} onValueChange={setAppUserId}>
            <SelectTrigger id="confirm-mapping-user" className="w-full">
              <SelectValue placeholder="Select a user" />
            </SelectTrigger>
            <SelectContent>
              {users
                .filter((user) => user.active && user.id)
                .map((user) => (
                  <SelectItem key={user.id} value={user.id!}>
                    {user.name ?? user.email} · {user.email}
                  </SelectItem>
                ))}
            </SelectContent>
          </Select>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => close(false)}>
            Cancel
          </Button>
          <Button disabled={!appUserId || pending} onClick={() => appUserId && onConfirm(appUserId)}>
            {pending ? "Confirming…" : "Confirm identity"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
