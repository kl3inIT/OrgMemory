import { create } from "zustand"
import { createJSONStorage, persist } from "zustand/middleware"

type GraphExplorerState = {
  selectedKnowledgeSpaceId: string | null
  entityLimit: 200 | 500 | 1000
  setSelectedKnowledgeSpaceId: (selectedKnowledgeSpaceId: string) => void
  setEntityLimit: (entityLimit: 200 | 500 | 1000) => void
}

export const useGraphExplorerStore = create<GraphExplorerState>()(
  persist(
    (set) => ({
      selectedKnowledgeSpaceId: null,
      entityLimit: 200,
      setSelectedKnowledgeSpaceId: (selectedKnowledgeSpaceId) =>
        set({ selectedKnowledgeSpaceId }),
      setEntityLimit: (entityLimit) => set({ entityLimit }),
    }),
    {
      name: "orgmemory-graph-explorer",
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        selectedKnowledgeSpaceId: state.selectedKnowledgeSpaceId,
        entityLimit: state.entityLimit,
      }),
    },
  ),
)
