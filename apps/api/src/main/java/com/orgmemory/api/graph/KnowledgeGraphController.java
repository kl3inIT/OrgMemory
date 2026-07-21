package com.orgmemory.api.graph;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.capability.CapabilityAsset;
import com.orgmemory.core.capability.CapabilityAssetService;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.Department;
import com.orgmemory.core.organization.DepartmentRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
class KnowledgeGraphController {

    private final CapabilityAssetService assets;
    private final DepartmentRepository departments;
    private final AppUserRepository users;
    private final CurrentActorProvider actors;

    KnowledgeGraphController(CapabilityAssetService assets, DepartmentRepository departments, AppUserRepository users,
            CurrentActorProvider actors) {
        this.assets = assets;
        this.departments = departments;
        this.users = users;
        this.actors = actors;
    }

    @GetMapping
    KnowledgeGraphResponse graph(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID focusAssetId,
            @RequestParam(defaultValue = "2") int depth,
            Authentication authentication
    ) {
        CurrentActor actor = actors.current(authentication);
        List<CapabilityAsset> selectedAssets = assets.search(actor, null, null, q);
        Map<UUID, Department> departmentById = indexDepartments(actor.organizationId());
        Map<UUID, AppUser> userById = indexUsers(actor.organizationId());

        Map<String, KnowledgeGraphNodeResponse> nodes = new LinkedHashMap<>();
        Map<String, KnowledgeGraphEdgeResponse> edges = new LinkedHashMap<>();
        Map<String, List<String>> tagToAssets = new LinkedHashMap<>();
        Map<String, List<String>> ownerToAssets = new LinkedHashMap<>();
        Map<String, List<String>> processToAssets = new LinkedHashMap<>();

        for (CapabilityAsset asset : selectedAssets) {
            String assetNodeId = assetNodeId(asset.getId());
            int usageCount = Math.toIntExact(Math.min(Integer.MAX_VALUE, assets.usageCount(actor, asset.getId())));
            nodes.put(assetNodeId, new KnowledgeGraphNodeResponse(
                    assetNodeId,
                    asset.getTitle(),
                    GraphNodeKind.ASSET,
                    asset.getSummary(),
                    asset.getId().toString(),
                    asset.getAssetType().name(),
                    asset.getStatus().name(),
                    Math.max(1, usageCount)
            ));

            String typeNodeId = typeNodeId(asset.getAssetType().name());
            nodes.putIfAbsent(typeNodeId, new KnowledgeGraphNodeResponse(
                    typeNodeId,
                    readableName(asset.getAssetType().name()),
                    GraphNodeKind.ASSET_TYPE,
                    "Capability asset type",
                    null,
                    asset.getAssetType().name(),
                    null,
                    1
            ));
            addEdge(edges, assetNodeId, typeNodeId, GraphEdgeKind.HAS_TYPE, "type", 1);

            if (asset.getDepartmentId() != null) {
                Department department = departmentById.get(asset.getDepartmentId());
                String departmentNodeId = departmentNodeId(asset.getDepartmentId());
                nodes.putIfAbsent(departmentNodeId, new KnowledgeGraphNodeResponse(
                        departmentNodeId,
                        department == null ? "Unknown department" : department.getName(),
                        GraphNodeKind.DEPARTMENT,
                        "Department ownership context",
                        null,
                        null,
                        null,
                        1
                ));
                addEdge(edges, assetNodeId, departmentNodeId, GraphEdgeKind.BELONGS_TO, "department", 1);
            }

            if (asset.getOwnerUserId() != null) {
                addUserNode(nodes, userById, asset.getOwnerUserId());
                String ownerNodeId = userNodeId(asset.getOwnerUserId());
                addEdge(edges, assetNodeId, ownerNodeId, GraphEdgeKind.OWNED_BY, "owner", 2);
                ownerToAssets.computeIfAbsent(ownerNodeId, ignored -> new ArrayList<>()).add(assetNodeId);
            }

            if (asset.getBackupOwnerUserId() != null) {
                addUserNode(nodes, userById, asset.getBackupOwnerUserId());
                addEdge(edges, assetNodeId, userNodeId(asset.getBackupOwnerUserId()), GraphEdgeKind.BACKED_UP_BY, "backup", 1);
            }

            for (String tag : splitTags(asset.getTagNames())) {
                String tagNodeId = tagNodeId(tag);
                nodes.putIfAbsent(tagNodeId, new KnowledgeGraphNodeResponse(
                        tagNodeId,
                        tag,
                        GraphNodeKind.TAG,
                        "Capability tag",
                        null,
                        null,
                        null,
                        1
                ));
                addEdge(edges, assetNodeId, tagNodeId, GraphEdgeKind.TAGGED_WITH, "tag", 1);
                tagToAssets.computeIfAbsent(tagNodeId, ignored -> new ArrayList<>()).add(assetNodeId);
            }

            String process = normalize(asset.getBusinessProcess());
            if (!process.isBlank()) {
                processToAssets.computeIfAbsent(process, ignored -> new ArrayList<>()).add(assetNodeId);
            }
        }

        addRelatedAssetEdges(edges, tagToAssets, GraphEdgeKind.RELATED_BY_TAG, "shared tag", 1);
        addRelatedAssetEdges(edges, ownerToAssets, GraphEdgeKind.RELATED_BY_OWNER, "same owner", 1);
        addRelatedAssetEdges(edges, processToAssets, GraphEdgeKind.RELATED_BY_PROCESS, "same process", 1);

        String focusNodeId = focusAssetId == null ? null : assetNodeId(focusAssetId);
        if (focusNodeId != null && nodes.containsKey(focusNodeId)) {
            return localGraph(nodes, edges, focusNodeId, Math.max(1, Math.min(4, depth)));
        }

        return new KnowledgeGraphResponse(List.copyOf(nodes.values()), List.copyOf(edges.values()), focusNodeId, 0);
    }

    private Map<UUID, Department> indexDepartments(UUID organizationId) {
        return departments.findByOrganizationIdOrderByName(organizationId).stream()
                .collect(LinkedHashMap::new, (map, department) -> map.put(department.getId(), department), Map::putAll);
    }

    private Map<UUID, AppUser> indexUsers(UUID organizationId) {
        return users.findByOrganizationIdOrderByName(organizationId).stream()
                .collect(LinkedHashMap::new, (map, user) -> map.put(user.getId(), user), Map::putAll);
    }

    private static void addUserNode(Map<String, KnowledgeGraphNodeResponse> nodes, Map<UUID, AppUser> userById, UUID userId) {
        AppUser user = userById.get(userId);
        String userNodeId = userNodeId(userId);
        nodes.putIfAbsent(userNodeId, new KnowledgeGraphNodeResponse(
                userNodeId,
                user == null ? "Unknown user" : user.getName(),
                GraphNodeKind.USER,
                user == null ? "Asset contributor" : user.getEmail(),
                null,
                null,
                null,
                1
        ));
    }

    private static void addRelatedAssetEdges(
            Map<String, KnowledgeGraphEdgeResponse> edges,
            Map<String, List<String>> groups,
            GraphEdgeKind kind,
            String label,
            int weight
    ) {
        for (List<String> assetNodeIds : groups.values()) {
            List<String> sorted = assetNodeIds.stream().distinct().sorted().toList();
            int limit = Math.min(sorted.size(), 6);
            for (int index = 0; index < limit - 1; index++) {
                addUndirectedEdge(edges, sorted.get(index), sorted.get(index + 1), kind, label, weight);
            }
        }
    }

    private static KnowledgeGraphResponse localGraph(
            Map<String, KnowledgeGraphNodeResponse> nodes,
            Map<String, KnowledgeGraphEdgeResponse> edges,
            String focusNodeId,
            int depth
    ) {
        Map<String, List<KnowledgeGraphEdgeResponse>> adjacency = new LinkedHashMap<>();
        for (KnowledgeGraphEdgeResponse edge : edges.values()) {
            adjacency.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
            adjacency.computeIfAbsent(edge.target(), ignored -> new ArrayList<>()).add(edge);
        }

        Set<String> included = new LinkedHashSet<>();
        ArrayDeque<GraphVisit> queue = new ArrayDeque<>();
        included.add(focusNodeId);
        queue.add(new GraphVisit(focusNodeId, 0));
        while (!queue.isEmpty()) {
            GraphVisit visit = queue.removeFirst();
            if (visit.depth() >= depth) {
                continue;
            }
            for (KnowledgeGraphEdgeResponse edge : adjacency.getOrDefault(visit.nodeId(), List.of())) {
                String nextNodeId = edge.source().equals(visit.nodeId()) ? edge.target() : edge.source();
                if (included.add(nextNodeId)) {
                    queue.addLast(new GraphVisit(nextNodeId, visit.depth() + 1));
                }
            }
        }

        List<KnowledgeGraphNodeResponse> localNodes = included.stream()
                .map(nodes::get)
                .filter(node -> node != null)
                .sorted(Comparator.comparing(KnowledgeGraphNodeResponse::kind).thenComparing(KnowledgeGraphNodeResponse::label))
                .toList();
        List<KnowledgeGraphEdgeResponse> localEdges = edges.values().stream()
                .filter(edge -> included.contains(edge.source()) && included.contains(edge.target()))
                .toList();
        return new KnowledgeGraphResponse(localNodes, localEdges, focusNodeId, depth);
    }

    private static void addEdge(
            Map<String, KnowledgeGraphEdgeResponse> edges,
            String source,
            String target,
            GraphEdgeKind kind,
            String label,
            int weight
    ) {
        String id = kind.name() + ":" + source + "->" + target;
        edges.putIfAbsent(id, new KnowledgeGraphEdgeResponse(id, source, target, kind, label, weight));
    }

    private static void addUndirectedEdge(
            Map<String, KnowledgeGraphEdgeResponse> edges,
            String source,
            String target,
            GraphEdgeKind kind,
            String label,
            int weight
    ) {
        if (source.equals(target)) {
            return;
        }
        String first = source.compareTo(target) <= 0 ? source : target;
        String second = source.compareTo(target) <= 0 ? target : source;
        addEdge(edges, first, second, kind, label, weight);
    }

    private static List<String> splitTags(String tagNames) {
        if (tagNames == null || tagNames.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(tagNames.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String readableName(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
            }
        }
        return String.join(" ", words);
    }

    private static String assetNodeId(UUID assetId) {
        return "asset:" + assetId;
    }

    private static String typeNodeId(String assetType) {
        return "type:" + assetType;
    }

    private static String departmentNodeId(UUID departmentId) {
        return "department:" + departmentId;
    }

    private static String userNodeId(UUID userId) {
        return "user:" + userId;
    }

    private static String tagNodeId(String tag) {
        return "tag:" + tag.replaceAll("[^a-z0-9-]+", "-");
    }

    private record GraphVisit(String nodeId, int depth) {
    }
}
