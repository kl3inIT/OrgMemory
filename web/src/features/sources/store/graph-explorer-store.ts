import { create } from "zustand"
import { createJSONStorage, persist } from "zustand/middleware"

export type GraphEntityLimit = 200 | 500 | 1000

type GraphExplorerState = {
  selectedKnowledgeSpaceId: string | null
  entityLimit: GraphEntityLimit
  maximumDepth: number
  showPropertyPanel: boolean
  showSearchBar: boolean
  showNodeLabels: boolean
  enableNodeDrag: boolean
  showEdgeLabels: boolean
  hideUnselectedEdges: boolean
  enableEdgeEvents: boolean
  showLegend: boolean
  minimumEdgeSize: number
  maximumEdgeSize: number
  setSelectedKnowledgeSpaceId: (selectedKnowledgeSpaceId: string) => void
  setEntityLimit: (entityLimit: GraphEntityLimit) => void
  setMaximumDepth: (maximumDepth: number) => void
  setViewerPreference: <
    Key extends keyof Pick<
      GraphExplorerState,
      | "showPropertyPanel"
      | "showSearchBar"
      | "showNodeLabels"
      | "enableNodeDrag"
      | "showEdgeLabels"
      | "hideUnselectedEdges"
      | "enableEdgeEvents"
      | "showLegend"
    >,
  >(
    key: Key,
    value: GraphExplorerState[Key],
  ) => void
  setEdgeSizeRange: (minimumEdgeSize: number, maximumEdgeSize: number) => void
}

export const useGraphExplorerStore = create<GraphExplorerState>()(
  persist(
    (set) => ({
      selectedKnowledgeSpaceId: null,
      entityLimit: 200,
      maximumDepth: 3,
      showPropertyPanel: true,
      showSearchBar: true,
      showNodeLabels: true,
      enableNodeDrag: true,
      showEdgeLabels: false,
      hideUnselectedEdges: true,
      enableEdgeEvents: true,
      showLegend: false,
      minimumEdgeSize: 1,
      maximumEdgeSize: 5,
      setSelectedKnowledgeSpaceId: (selectedKnowledgeSpaceId) =>
        set({ selectedKnowledgeSpaceId }),
      setEntityLimit: (entityLimit) => set({ entityLimit }),
      setMaximumDepth: (maximumDepth) => set({ maximumDepth }),
      setViewerPreference: (key, value) => set({ [key]: value }),
      setEdgeSizeRange: (minimumEdgeSize, maximumEdgeSize) =>
        set({ minimumEdgeSize, maximumEdgeSize }),
    }),
    {
      name: "orgmemory-graph-explorer",
      version: 2,
      storage: createJSONStorage(() => sessionStorage),
      migrate: (persistedState) => {
        const state = persistedState as Partial<GraphExplorerState>
        const entityLimit = [200, 500, 1000].includes(Number(state.entityLimit))
          ? (state.entityLimit as GraphEntityLimit)
          : 200
        const minimumEdgeSize =
          Number(state.minimumEdgeSize) >= 1 ? Number(state.minimumEdgeSize) : 1
        const maximumEdgeSize =
          Number(state.maximumEdgeSize) >= minimumEdgeSize
            ? Math.min(Number(state.maximumEdgeSize), 10)
            : 5
        return {
          ...state,
          entityLimit,
          minimumEdgeSize,
          maximumEdgeSize,
        }
      },
      partialize: (state) => ({
        entityLimit: state.entityLimit,
        maximumDepth: state.maximumDepth,
        showPropertyPanel: state.showPropertyPanel,
        showSearchBar: state.showSearchBar,
        showNodeLabels: state.showNodeLabels,
        enableNodeDrag: state.enableNodeDrag,
        showEdgeLabels: state.showEdgeLabels,
        hideUnselectedEdges: state.hideUnselectedEdges,
        enableEdgeEvents: state.enableEdgeEvents,
        showLegend: state.showLegend,
        minimumEdgeSize: state.minimumEdgeSize,
        maximumEdgeSize: state.maximumEdgeSize,
      }),
    },
  ),
)
