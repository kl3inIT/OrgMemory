package com.orgmemory.api.ai;

import com.orgmemory.core.capability.AssetType;
import com.orgmemory.core.capability.CapabilityAsset;
import com.orgmemory.core.capability.CapabilityAssetService;
import com.orgmemory.core.capability.RiskLevel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/ai")
@SuppressWarnings("UnknownHttpHeader")
class AssetAssistantController {

    private static final String UI_MESSAGE_STREAM_HEADER = "x-vercel-ai-ui-message-stream";
    private static final String TEXT_PART_ID = "0";
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "there", "their", "they", "are", "any", "about", "related", "relation", "to", "for", "from",
            "with", "and", "or", "that", "this", "these", "those", "have", "has", "had", "does", "do", "did",
            "can", "could", "would", "should", "what", "which", "one", "use", "first", "best", "please", "show",
            "cho", "toi", "co", "khong", "nao", "cai", "ve", "lien", "quan", "dung", "duoc");
    private static final Set<String> VISUAL_TERMS = Set.of(
            "image", "images", "photo", "photos", "picture", "pictures", "visual", "visuals", "creative",
            "brand", "logo", "thumbnail", "composition", "midjourney", "dalle", "ideogram", "runway");

    private final ObjectProvider<ChatClient.Builder> chatBuilders;
    private final ObjectMapper objectMapper;
    private final CapabilityAssetService assets;

    AssetAssistantController(ObjectProvider<ChatClient.Builder> chatBuilders, ObjectMapper objectMapper,
            CapabilityAssetService assets) {
        this.chatBuilders = chatBuilders;
        this.objectMapper = objectMapper;
        this.assets = assets;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chat(@RequestBody ChatRequest request, HttpServletResponse response) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }

        response.setHeader(UI_MESSAGE_STREAM_HEADER, "v1");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");

        SseEmitter emitter = new SseEmitter(120_000L);
        UiMessageStream.pipe(streamAssistantTurn(request.message()), emitter, objectMapper);
        return emitter;
    }

    @PostMapping("/assets/normalize")
    AiDraftResponse normalize(@Valid @RequestBody AiDraftRequest request) {
        AiDraftResponse fallback = fallbackDraft(request, "local-fallback",
                "Spring AI is disabled or unavailable, so OrgMemory used local normalization.");

        ChatClient client = buildChatClient();
        if (client == null) {
            return fallback;
        }

        try {
            String content = client.prompt()
                    .system("""
                            You turn rough employee AI know-how into an OrgMemory Capability Asset draft.
                            Return strict JSON only. No markdown.
                            Required keys: title, summary, assetType, useCase, businessProcess, aiTool, tagNames,
                            riskLevel, promptTemplate, workflowStepsJson, inputSchemaJson, outputSchemaJson,
                            exampleInput, exampleOutput.
                            assetType must be one of PROMPT_TEMPLATE, WORKFLOW_AUTOMATION, AI_AGENT, KNOWLEDGE_BOT,
                            ANALYTICS_BRIEF, CONTENT_GENERATOR, DATA_EXTRACTION, EVALUATION_CHECKLIST,
                            PLAYBOOK, HANDOVER_PACK, GOVERNANCE_GUARDRAIL, COPILOT.
                            riskLevel must be LOW, MEDIUM, or HIGH.
                            workflowStepsJson, inputSchemaJson, and outputSchemaJson must be JSON encoded as strings.
                            """)
                    .user("""
                            Preferred AI tool: %s
                            Business process hint: %s

                            Raw workflow:
                            %s
                            """.formatted(nullToUnset(request.aiTool()), nullToUnset(request.businessProcess()), request.rawText()))
                    .call()
                    .content();
            return parseAiDraft(content, fallback);
        } catch (Exception ignored) {
            return fallbackDraft(request, "local-fallback",
                    "Spring AI was enabled, but the model call failed; OrgMemory used local normalization.");
        }
    }

    private Flux<Part> streamAssistantTurn(String userMessage) {
        String registryAnswer = registryAnswer(userMessage);
        if (StringUtils.hasText(registryAnswer) && isRegistryQuestion(userMessage)) {
            return fallbackStream(registryAnswer);
        }

        ChatClient client = buildChatClient();
        if (client == null) {
            return fallbackStream(StringUtils.hasText(registryAnswer) ? registryAnswer : """
                    Spring AI is wired into OrgMemory, but chat is disabled right now.

                    Enable it with `ORGMEMORY_AI_MODEL_CHAT=openai` and `OPENAI_API_KEY`.

                    MVP flow: capture a raw workflow, normalize it into a Capability Asset, submit for review, approve it, reuse it, and track handover risk.
                    """);
        }

        Flux<Part> deltas = client.prompt()
                .system("""
                        You are OrgMemory's registry assistant. Help users convert AI prompts,
                        workflows, and agent operating knowledge into reusable Capability Assets.
                        Keep answers concrete and mention review/reuse/ownership when relevant.
                        If registry context is provided, answer only from that context.
                        """)
                .user("""
                        Registry context:
                        %s

                        User question:
                        %s
                        """.formatted(registryAnswer, userMessage))
                .stream()
                .content()
                .map(token -> new Part.TextDelta(TEXT_PART_ID, token));

        return Flux.concat(Flux.just(new Part.TextStart(TEXT_PART_ID)), deltas, Flux.just(new Part.TextEnd(TEXT_PART_ID)));
    }

    private String registryAnswer(String userMessage) {
        try {
            List<CapabilityAsset> ranked = rankedAssets(userMessage);
            if (ranked.isEmpty()) {
                return "";
            }

            boolean asksApproved = userMessage.toLowerCase(Locale.ROOT).contains("approved");
            List<CapabilityAsset> display = asksApproved
                    ? ranked.stream().filter(asset -> asset.getStatus().name().equals("APPROVED")).toList()
                    : ranked;
            if (display.isEmpty()) {
                display = ranked;
            }

            CapabilityAsset recommended = display.getFirst();

            StringBuilder answer = new StringBuilder();
            answer.append("Based on the live OrgMemory registry, the best matching capability assets are:\n\n");
            for (int index = 0; index < Math.min(3, display.size()); index++) {
                CapabilityAsset asset = display.get(index);
                answer.append(index + 1)
                        .append(". ")
                        .append(asset.getTitle())
                        .append(" — ")
                        .append(asset.getStatus())
                        .append(", ")
                        .append(asset.getAssetType())
                        .append(", ")
                        .append(assets.usageCount(asset.getId()))
                        .append(" uses. ")
                        .append(asset.getSummary())
                        .append("\n");
            }

            answer.append("\nUse first: ")
                    .append(recommended.getTitle())
                    .append(". It is the strongest starting point because it matches the request");
            if (recommended.getStatus().name().equals("APPROVED")) {
                answer.append(", is approved");
            }
            if (assets.usageCount(recommended.getId()) > 0) {
                answer.append(", and already has reuse history");
            }
            answer.append(". Open the asset detail, review its workflow, then click Use Asset to record reuse.");

            if (recommended.getBackupOwnerUserId() == null) {
                answer.append(" It is missing a backup owner, so assign one before relying on it operationally.");
            }
            return answer.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<CapabilityAsset> rankedAssets(String userMessage) {
        QueryTerms query = queryTerms(userMessage);
        boolean genericQuestion = query.tokens().isEmpty() && query.phrases().isEmpty() && query.typeIntents().isEmpty();

        return assets.search(null, null, null).stream()
                .map(asset -> new RankedAsset(asset, score(asset, query)))
                .filter(match -> genericQuestion || match.score() > 0)
                .sorted(Comparator.comparingInt(RankedAsset::score).reversed()
                        .thenComparing(match -> assets.usageCount(match.asset().getId()), Comparator.reverseOrder()))
                .map(RankedAsset::asset)
                .toList();
    }

    private int score(CapabilityAsset asset, QueryTerms query) {
        SearchDocument document = SearchDocument.from(asset);

        int score = 0;
        for (String phrase : query.phrases()) {
            if (containsSearchTerm(document.all(), phrase)) {
                score += 22;
            }
        }

        score += fieldScore(document.title(), query.tokens(), 10);
        score += fieldScore(document.tags(), query.tokens(), 8);
        score += fieldScore(document.tool(), query.tokens(), 8);
        score += fieldScore(document.useCase(), query.tokens(), 7);
        score += fieldScore(document.businessProcess(), query.tokens(), 6);
        score += fieldScore(document.summary(), query.tokens(), 4);
        score += fieldScore(document.type(), query.tokens(), 3);

        if (query.typeIntents().contains(asset.getAssetType())) {
            score += 8;
        }
        if (query.visualIntent()) {
            score += fieldScore(document.all(), VISUAL_TERMS, 3);
            if (asset.getAiTool() != null && isVisualTool(asset.getAiTool())) {
                score += 8;
            }
        }
        if (query.workflowIntent() && asset.getAssetType() == AssetType.WORKFLOW_AUTOMATION) {
            score += 5;
        }
        if (asset.getStatus().name().equals("APPROVED")) {
            score += query.approvedIntent() ? 10 : 2;
        } else if (query.approvedIntent()) {
            score -= 8;
        }
        if (asset.getBackupOwnerUserId() != null) {
            score += 1;
        }
        return score;
    }

    private QueryTerms queryTerms(String userMessage) {
        String normalized = normalizeSearchText(userMessage);
        Set<String> tokens = Arrays.stream(normalized.split("\\s+"))
                .filter(token -> token.length() > 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> phrases = new ArrayList<>();
        EnumSet<AssetType> typeIntents = EnumSet.noneOf(AssetType.class);

        boolean workflowIntent = containsAny(normalized, "workflow", "automation", "process", "flow", "quy trinh", "tu dong");
        if (workflowIntent) {
            typeIntents.add(AssetType.WORKFLOW_AUTOMATION);
        }
        if (containsAny(normalized, "agent", "codex", "claude code")) {
            typeIntents.add(AssetType.AI_AGENT);
        }
        if (containsAny(normalized, "bot", "rag", "knowledge", "kien thuc")) {
            typeIntents.add(AssetType.KNOWLEDGE_BOT);
        }
        if (containsAny(normalized, "slide", "deck", "presentation", "content", "copy", "thuyet trinh", "bai trinh bay")) {
            typeIntents.add(AssetType.CONTENT_GENERATOR);
            tokens.addAll(List.of("slide", "deck", "presentation", "canva", "gamma"));
        }

        boolean visualIntent = containsAny(normalized,
                "image", "images", "photo", "picture", "visual", "creative", "logo", "thumbnail",
                "midjourney", "dalle", "ideogram", "tao anh", "hinh anh", "anh");
        if (visualIntent) {
            tokens.addAll(List.of("image", "visual", "creative", "brand", "midjourney", "prompt", "photo"));
            phrases.addAll(List.of("image generation", "product image", "brand image", "visual direction", "negative prompt"));
        }
        if (containsAny(normalized, "video", "clip", "short", "podcast", "runway", "descript")) {
            tokens.addAll(List.of("video", "clip", "storyboard", "runway", "descript"));
            phrases.addAll(List.of("video generation", "short video", "clip plan"));
        }
        if (containsAny(normalized, "code", "repo", "repository", "pull request", "review code", "lap trinh")) {
            tokens.addAll(List.of("code", "repository", "codex", "claude", "engineering"));
            typeIntents.add(AssetType.AI_AGENT);
            typeIntents.add(AssetType.COPILOT);
        }
        if (normalized.contains("customer feedback")) {
            phrases.add("customer feedback");
        }

        boolean approvedIntent = containsAny(normalized, "approved", "approve", "duyet", "da duyet");
        return new QueryTerms(tokens, phrases.stream().map(AssetAssistantController::normalizeSearchText).toList(),
                typeIntents, workflowIntent, visualIntent, approvedIntent);
    }

    private static int fieldScore(String field, Set<String> tokens, int weight) {
        int score = 0;
        for (String token : tokens) {
            if (containsSearchTerm(field, token)) {
                score += weight;
            }
        }
        return score;
    }

    private static boolean containsSearchTerm(String field, String term) {
        return StringUtils.hasText(field) && StringUtils.hasText(term) && field.contains(term);
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVisualTool(String tool) {
        String normalized = normalizeSearchText(tool);
        return containsAny(normalized, "midjourney", "dalle", "ideogram", "runway", "canva", "openai");
    }

    private static String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        String stripped = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private boolean isRegistryQuestion(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("asset")
                || normalized.contains("workflow")
                || normalized.contains("capability")
                || normalized.contains("approved")
                || normalized.contains("registry")
                || normalized.contains("owner")
                || normalized.contains("use first")
                || normalized.contains("which one");
    }

    private record RankedAsset(CapabilityAsset asset, int score) {
    }

    private record QueryTerms(Set<String> tokens, List<String> phrases, Set<AssetType> typeIntents,
            boolean workflowIntent, boolean visualIntent, boolean approvedIntent) {
    }

    private record SearchDocument(String title, String summary, String useCase, String businessProcess,
            String tool, String tags, String type, String status, String all) {

        private static SearchDocument from(CapabilityAsset asset) {
            String title = normalizeSearchText(asset.getTitle());
            String summary = normalizeSearchText(asset.getSummary());
            String useCase = normalizeSearchText(asset.getUseCase());
            String businessProcess = normalizeSearchText(asset.getBusinessProcess());
            String tool = normalizeSearchText(asset.getAiTool());
            String tags = normalizeSearchText(asset.getTagNames());
            String type = normalizeSearchText(asset.getAssetType().name());
            String status = normalizeSearchText(asset.getStatus().name());
            String all = String.join(" ", title, summary, useCase, businessProcess, tool, tags, type, status);
            return new SearchDocument(title, summary, useCase, businessProcess, tool, tags, type, status, all);
        }
    }

    private Flux<Part> fallbackStream(String content) {
        return Flux.just(
                new Part.TextStart(TEXT_PART_ID),
                new Part.TextDelta(TEXT_PART_ID, content),
                new Part.TextEnd(TEXT_PART_ID));
    }

    private ChatClient buildChatClient() {
        ChatClient.Builder builder = chatBuilders.getIfAvailable();
        if (builder == null) {
            return null;
        }
        return builder.defaultSystem("You are OrgMemory's AI capability assistant.").build();
    }

    private AiDraftResponse parseAiDraft(String rawContent, AiDraftResponse fallback) {
        String json = extractJson(rawContent);
        return new AiDraftResponse(
                true,
                "spring-ai",
                "Draft generated by Spring AI.",
                firstNonBlank(jsonValue(json, "title"), fallback.title()),
                firstNonBlank(jsonValue(json, "summary"), fallback.summary()),
                parseAssetType(firstNonBlank(jsonValue(json, "assetType"), fallback.assetType().name())),
                firstNonBlank(jsonValue(json, "useCase"), fallback.useCase()),
                firstNonBlank(jsonValue(json, "businessProcess"), fallback.businessProcess()),
                firstNonBlank(jsonValue(json, "aiTool"), fallback.aiTool()),
                firstNonBlank(jsonValue(json, "tagNames"), fallback.tagNames()),
                parseRisk(firstNonBlank(jsonValue(json, "riskLevel"), fallback.riskLevel().name())),
                firstNonBlank(jsonValue(json, "promptTemplate"), fallback.promptTemplate()),
                firstNonBlank(jsonValue(json, "workflowStepsJson"), fallback.workflowStepsJson()),
                firstNonBlank(jsonValue(json, "inputSchemaJson"), fallback.inputSchemaJson()),
                firstNonBlank(jsonValue(json, "outputSchemaJson"), fallback.outputSchemaJson()),
                firstNonBlank(jsonValue(json, "exampleInput"), fallback.exampleInput()),
                firstNonBlank(jsonValue(json, "exampleOutput"), fallback.exampleOutput()));
    }

    private AiDraftResponse fallbackDraft(AiDraftRequest request, String source, String note) {
        String raw = request.rawText().replaceAll("\\s+", " ").trim();
        String title = summarizeTitle(raw);
        String process = firstNonBlank(request.businessProcess(), inferProcess(raw));
        RiskLevel risk = inferRisk(raw);

        return new AiDraftResponse(
                false,
                source,
                note,
                title,
                abbreviate(raw, 220),
                inferAssetType(raw),
                inferUseCase(raw),
                process,
                firstNonBlank(request.aiTool(), "ChatGPT / Claude"),
                inferTags(raw),
                risk,
                request.rawText(),
                "[{\"name\":\"Capture source material\"},{\"name\":\"Run the prompt or workflow\"},{\"name\":\"Review output\"},{\"name\":\"Publish approved asset\"}]",
                "{\"source\":\"string\",\"constraints\":\"string\"}",
                "{\"draft\":\"string\",\"nextAction\":\"string\"}",
                "Raw notes, prompt, transcript, or operating procedure.",
                "Reusable output with owner-visible assumptions and next action.");
    }

    private static String extractJson(String content) {
        if (content == null) {
            return "";
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return content;
        }
        return content.substring(start, end + 1);
    }

    private static String jsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim();
    }

    private static String summarizeTitle(String raw) {
        String cleaned = raw.replaceAll("^[#*\\-\\s]+", "").trim();
        if (cleaned.isBlank()) {
            return "Untitled AI capability";
        }
        int sentenceEnd = cleaned.indexOf('.');
        String title = sentenceEnd > 24 ? cleaned.substring(0, sentenceEnd) : cleaned;
        return abbreviate(title, 72);
    }

    private static String inferUseCase(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.contains("onboarding")) {
            return "Employee onboarding";
        }
        if (normalized.contains("offboarding") || normalized.contains("handover")) {
            return "Knowledge handover";
        }
        if (normalized.contains("email")) {
            return "Email drafting";
        }
        if (normalized.contains("meeting") || normalized.contains("transcript")) {
            return "Meeting follow-up";
        }
        return "AI workflow reuse";
    }

    private static AssetType inferAssetType(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.contains("agent") || normalized.contains("triage")) {
            return AssetType.AI_AGENT;
        }
        if (normalized.contains("bot") || normalized.contains("q&a") || normalized.contains("knowledge")) {
            return AssetType.KNOWLEDGE_BOT;
        }
        if (normalized.contains("checklist") || normalized.contains("rubric") || normalized.contains("evaluate")) {
            return AssetType.EVALUATION_CHECKLIST;
        }
        if (normalized.contains("handover") || normalized.contains("offboarding")) {
            return AssetType.HANDOVER_PACK;
        }
        if (normalized.contains("brief") || normalized.contains("report") || normalized.contains("forecast")) {
            return AssetType.ANALYTICS_BRIEF;
        }
        if (normalized.contains("extract") || normalized.contains("invoice") || normalized.contains("table")) {
            return AssetType.DATA_EXTRACTION;
        }
        if (normalized.contains("reply") || normalized.contains("copilot")) {
            return AssetType.COPILOT;
        }
        if (normalized.contains("policy") || normalized.contains("pii") || normalized.contains("guardrail")) {
            return AssetType.GOVERNANCE_GUARDRAIL;
        }
        if (normalized.contains("email") || normalized.contains("campaign") || normalized.contains("article")) {
            return AssetType.CONTENT_GENERATOR;
        }
        if (normalized.contains("prompt")) {
            return AssetType.PROMPT_TEMPLATE;
        }
        return AssetType.WORKFLOW_AUTOMATION;
    }

    private static String inferProcess(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.contains("sales") || normalized.contains("customer")) {
            return "Revenue operations";
        }
        if (normalized.contains("hr") || normalized.contains("onboarding")) {
            return "People operations";
        }
        if (normalized.contains("support") || normalized.contains("ticket")) {
            return "Customer support";
        }
        return "Team operations";
    }

    private static String inferTags(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.contains("sales") || normalized.contains("customer")) {
            return "sales, customer, workflow";
        }
        if (normalized.contains("onboarding")) {
            return "onboarding, training, handover";
        }
        if (normalized.contains("offboarding") || normalized.contains("handover")) {
            return "offboarding, ownership, risk";
        }
        return "ai-workflow, prompt, reusable";
    }

    private static RiskLevel inferRisk(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.contains("pii") || normalized.contains("legal") || normalized.contains("contract")
                || normalized.contains("salary") || normalized.contains("financial")) {
            return RiskLevel.HIGH;
        }
        if (normalized.contains("customer") || normalized.contains("email") || normalized.contains("crm")) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private static RiskLevel parseRisk(String value) {
        try {
            return RiskLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return RiskLevel.MEDIUM;
        }
    }

    private static AssetType parseAssetType(String value) {
        try {
            return AssetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return AssetType.WORKFLOW_AUTOMATION;
        }
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)).trim() + ".";
    }

    private static String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private static String nullToUnset(String value) {
        return StringUtils.hasText(value) ? value : "unset";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
