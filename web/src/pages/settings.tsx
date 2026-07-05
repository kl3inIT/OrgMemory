import { PageTitle } from "@/components/layout/page-title"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

export function SettingsPage() {
  return (
    <div className="space-y-6">
      <PageTitle title="Settings" subtitle="Workspace-level configuration for OrgMemory." />
      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle>Runtime</CardTitle>
          <CardDescription>Spring AI is provider-optional. Set model chat to OpenAI when you want live AI.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <MetaLine label="Spring AI mode" value="ORGMEMORY_AI_MODEL_CHAT" />
          <MetaLine label="Model" value="ORGMEMORY_OPENAI_MODEL" />
          <MetaLine label="Provider key" value="OPENAI_API_KEY in local .env" />
        </CardContent>
      </Card>
    </div>
  )
}

function MetaLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b pb-3 last:border-b-0 last:pb-0">
      <span className="text-muted-foreground">{label}</span>
      <strong className="font-mono text-xs">{value}</strong>
    </div>
  )
}
