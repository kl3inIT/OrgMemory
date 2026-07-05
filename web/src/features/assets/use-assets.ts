import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  createAsset,
  assignBackupOwner,
  getAsset,
  getKnowledgeGraph,
  listAssets,
  listVersions,
  normalizeAsset,
  recordUsage,
  updateAssetStatus,
  type AssetType,
  type AssetStatus,
  type CreateAssetPayload,
} from "@/lib/api"

export function useAssets(status?: AssetStatus | "", query?: string, assetType?: AssetType | "") {
  return useQuery({
    queryKey: ["assets", status || "", query || "", assetType || ""],
    queryFn: () => listAssets(status || undefined, query || undefined, assetType || undefined),
  })
}

export function useAssetVersions(assetId?: string | null) {
  return useQuery({
    queryKey: ["asset-versions", assetId],
    queryFn: () => listVersions(assetId ?? ""),
    enabled: Boolean(assetId),
  })
}

export function useAsset(assetId?: string | null) {
  return useQuery({
    queryKey: ["asset", assetId],
    queryFn: () => getAsset(assetId ?? ""),
    enabled: Boolean(assetId),
  })
}

export function useKnowledgeGraph(options?: { query?: string; focusAssetId?: string; depth?: number }) {
  return useQuery({
    queryKey: ["knowledge-graph", options?.query ?? "", options?.focusAssetId ?? "", options?.depth ?? 0],
    queryFn: () => getKnowledgeGraph(options),
  })
}

export function useCreateAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateAssetPayload) => createAsset(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["assets"] }),
  })
}

export function useNormalizeAsset() {
  return useMutation({
    mutationFn: ({ rawText, aiTool, businessProcess }: { rawText: string; aiTool?: string; businessProcess?: string }) =>
      normalizeAsset(rawText, aiTool, businessProcess),
  })
}

export function useRecordUsage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (assetId: string) => recordUsage(assetId, "USED"),
    onSuccess: (_data, assetId) => {
      queryClient.invalidateQueries({ queryKey: ["assets"] })
      queryClient.invalidateQueries({ queryKey: ["asset", assetId] })
    },
  })
}

export function useAssetStatusAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ assetId, action }: { assetId: string; action: "submit-review" | "approve" | "reject" | "deprecate" }) =>
      updateAssetStatus(assetId, action),
    onSuccess: (asset) => {
      queryClient.invalidateQueries({ queryKey: ["assets"] })
      queryClient.invalidateQueries({ queryKey: ["asset", asset.id] })
    },
  })
}

export function useAssignBackupOwner() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ assetId, backupOwnerUserId }: { assetId: string; backupOwnerUserId: string }) =>
      assignBackupOwner(assetId, backupOwnerUserId),
    onSuccess: (asset) => {
      queryClient.invalidateQueries({ queryKey: ["assets"] })
      queryClient.invalidateQueries({ queryKey: ["asset", asset.id] })
    },
  })
}
