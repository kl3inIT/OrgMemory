import { create } from "zustand"
import { createJSONStorage, persist } from "zustand/middleware"

import type { SourceStatusFilter } from "@/features/sources/source-status"

type DocumentManagerState = {
  statusFilter: SourceStatusFilter
  setStatusFilter: (statusFilter: SourceStatusFilter) => void
}

export const useDocumentManagerStore = create<DocumentManagerState>()(
  persist(
    (set) => ({
      statusFilter: "ALL",
      setStatusFilter: (statusFilter) => set({ statusFilter }),
    }),
    {
      name: "orgmemory-document-manager",
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({ statusFilter: state.statusFilter }),
    },
  ),
)
