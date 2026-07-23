import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import type {
  AdminSlackConnectionResponse,
  AdminUserResponse,
  KnowledgeSpaceResponse,
} from "@/lib/hey-api"

export type CrawlSettings = {
  crawlEnabled: boolean
  knowledgeSpaceId?: string
  actorUserId?: string
  channels: string[]
  contentCrawlIntervalSeconds: number
  maxThreadsPerChannel: number
}

const DEFAULT_INTERVAL_MINUTES = 60
const DEFAULT_MAX_THREADS = 500

/**
 * The half of a connection Slack has no opinion about: which tenant's Space the workspace
 * publishes into, whose name the ingestion is recorded under, and how hard the crawl is
 * allowed to pull. A crawl cannot be enabled without the first two, which the form enforces
 * before the request rather than letting the failure arrive per object at ingestion time.
 */
export function ConfigureCrawlDialog({
  connection,
  spaces,
  users,
  pending,
  onSave,
  onOpenChange,
}: {
  connection?: AdminSlackConnectionResponse
  spaces: KnowledgeSpaceResponse[]
  users: AdminUserResponse[]
  pending: boolean
  onSave: (settings: CrawlSettings) => void
  onOpenChange: (open: boolean) => void
}) {
  const [crawlEnabled, setCrawlEnabled] = useState(false)
  const [knowledgeSpaceId, setKnowledgeSpaceId] = useState<string>()
  const [actorUserId, setActorUserId] = useState<string>()
  const [channels, setChannels] = useState("")
  const [intervalMinutes, setIntervalMinutes] = useState(String(DEFAULT_INTERVAL_MINUTES))
  const [maxThreads, setMaxThreads] = useState(String(DEFAULT_MAX_THREADS))

  // Reopening on another connection has to show that connection, not whatever the last
  // one was left at.
  useEffect(() => {
    if (!connection) return
    setCrawlEnabled(connection.crawlEnabled ?? false)
    setKnowledgeSpaceId(connection.knowledgeSpaceId)
    setActorUserId(connection.actorUserId)
    setChannels((connection.channels ?? []).join(", "))
    setIntervalMinutes(
      String(Math.max(1, Math.round((connection.contentCrawlIntervalSeconds ?? 3600) / 60))),
    )
    setMaxThreads(String(connection.maxThreadsPerChannel ?? DEFAULT_MAX_THREADS))
  }, [connection])

  const parsedInterval = Number.parseInt(intervalMinutes, 10)
  const parsedMaxThreads = Number.parseInt(maxThreads, 10)
  const boundsValid = parsedInterval > 0 && parsedMaxThreads > 0
  const targetsValid = !crawlEnabled || (Boolean(knowledgeSpaceId) && Boolean(actorUserId))

  return (
    <Dialog open={Boolean(connection)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Crawl settings</DialogTitle>
          <DialogDescription>
            {connection?.sourceConnectionKey
              ? `Workspace ${connection.sourceConnectionKey}. Saved settings take effect on the worker's next poll.`
              : ""}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="flex items-center justify-between gap-4 rounded-md border p-3">
            <div className="space-y-0.5">
              <Label htmlFor="crawl-enabled">Crawl this workspace</Label>
              <p className="text-xs text-muted-foreground">
                Nothing is read from Slack until this is on.
              </p>
            </div>
            <Switch id="crawl-enabled" checked={crawlEnabled} onCheckedChange={setCrawlEnabled} />
          </div>

          <div className="space-y-2">
            <Label htmlFor="crawl-space">Knowledge Space</Label>
            <Select value={knowledgeSpaceId} onValueChange={setKnowledgeSpaceId}>
              <SelectTrigger id="crawl-space" className="w-full">
                <SelectValue placeholder="Select a Space to publish into" />
              </SelectTrigger>
              <SelectContent>
                {spaces
                  .filter((space) => space.id)
                  .map((space) => (
                    <SelectItem key={space.id} value={space.id!}>
                      {space.name ?? space.key}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="crawl-actor">Recorded as</Label>
            <Select value={actorUserId} onValueChange={setActorUserId}>
              <SelectTrigger id="crawl-actor" className="w-full">
                <SelectValue placeholder="Select the user the crawl publishes as" />
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
            <p className="text-xs text-muted-foreground">
              Every crawled object is attributed to this user in the audit trail.
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="crawl-channels">Channels</Label>
            <Input
              id="crawl-channels"
              value={channels}
              placeholder="general, engineering"
              onChange={(event) => setChannels(event.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              Leave empty to crawl every channel the bot can see. A filter also stops the crawl
              claiming it enumerated the workspace, so nothing is retired on its word.
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="crawl-interval">Content interval (minutes)</Label>
              <Input
                id="crawl-interval"
                inputMode="numeric"
                value={intervalMinutes}
                onChange={(event) => setIntervalMinutes(event.target.value)}
              />
              <p className="text-xs text-muted-foreground">
                Between these, only access is re-read.
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="crawl-threads">Threads per channel</Label>
              <Input
                id="crawl-threads"
                inputMode="numeric"
                value={maxThreads}
                onChange={(event) => setMaxThreads(event.target.value)}
              />
              <p className="text-xs text-muted-foreground">A bound on one crawl.</p>
            </div>
          </div>

          {!targetsValid ? (
            <p className="text-sm text-destructive">
              A crawl needs a Knowledge Space to publish into and a user to publish as.
            </p>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            disabled={pending || !boundsValid || !targetsValid}
            onClick={() =>
              onSave({
                crawlEnabled,
                knowledgeSpaceId,
                actorUserId,
                channels: channels
                  .split(",")
                  .map((channel) => channel.trim().replace(/^#/, ""))
                  .filter(Boolean),
                contentCrawlIntervalSeconds: parsedInterval * 60,
                maxThreadsPerChannel: parsedMaxThreads,
              })
            }
          >
            {pending ? "Saving…" : "Save settings"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
