import { create } from "zustand"
import { createJSONStorage, persist } from "zustand/middleware"

import type { SourceStatusFilter } from "@/features/sources/source-status"

type DocumentManagerState = {
  search: string
  statusFilter: SourceStatusFilter
  setSearch: (search: string) => void
  setStatusFilter: (statusFilter: SourceStatusFilter) => void
}

export const useDocumentManagerStore = create<DocumentManagerState>()(
  persist(
    (set) => ({
      search: "",
      statusFilter: "ALL",
      setSearch: (search) => set({ search }),
      setStatusFilter: (statusFilter) => set({ statusFilter }),
    }),
    {
      name: "orgmemory-document-manager",
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({ statusFilter: state.statusFilter }),
    },
  ),
)
