package com.example.elsa_store.ai;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * One entry point for every model call, whichever provider is configured. The AI services always build requests
 * and read responses in the Anthropic Messages shape (system blocks, {@code tool_use}/{@code tool_result}); for
 * OpenAI-compatible providers - Google Gemini's compatibility endpoint, Groq, OpenRouter, Ollama - {@link
 * OpenAiCompat} translates on the way out and back. Plain {@link RestClient}, no SDK, so the JSON stays visible and
 * {@code AI_BASE_URL} can point at a local stub.
 *
 * <p>Provider: {@code AI_PROVIDER} = {@code anthropic} | {@code gemini} | {@code openai}; when unset it follows the
 * key that is present ({@code GEMINI_API_KEY} → gemini, {@code ANTHROPIC_API_KEY} → anthropic). Without any key
 * every AI feature is off ({@link #isEnabled()}), the same way push is off without Firebase.
 */
@Slf4j
@Component
public class AiClient {

    public enum Provider {
        ANTHROPIC(
                "https://api.anthropic.com",
                "claude-sonnet-5",
                "claude-haiku-4-5-20251001",
                List.of("claude-haiku-4-5-20251001"),
                List.of()),
        // Every model here has a free tier in Google AI Studio. The newest model is often "experiencing high
        // demand" (503) on the free tier, so older, steadier models back it up.
        GEMINI(
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "gemini-3.8-flash",
                "gemini-3.5-flash-lite",
                List.of("gemini-3.5-flash", "gemini-2.5-flash"),
                List.of("gemini-2.5-flash-lite")),
        // Any other Chat Completions server: set AI_BASE_URL and the models.
        OPENAI("https://api.openai.com/v1", "", "", List.of(), List.of());

        final String baseUrl;
        final String assistantModel;
        final String fastModel;
        final List<String> assistantFallbacks;
        final List<String> fastFallbacks;

        Provider(
                String baseUrl,
                String assistantModel,
                String fastModel,
                List<String> assistantFallbacks,
                List<String> fastFallbacks) {
            this.baseUrl = baseUrl;
            this.assistantModel = assistantModel;
            this.fastModel = fastModel;
            this.assistantFallbacks = assistantFallbacks;
            this.fastFallbacks = fastFallbacks;
        }
    }

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /** Waits before retrying a 5xx ("model overloaded") on the same model. */
    private static final long[] RETRY_DELAYS_MS = {1_000, 2_500};

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;

    @Getter
    private final Provider provider;

    @Getter
    private final String assistantModel;

    @Getter
    private final String fastModel;

    /** Primary model first, then the ones tried when it is overloaded or out of quota. */
    @Getter
    private final List<String> assistantModels;

    @Getter
    private final List<String> fastModels;

    public AiClient(
            ObjectMapper objectMapper,
            @Value("${ai.provider:}") String provider,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.gemini-api-key:}") String geminiApiKey,
            @Value("${ai.anthropic-api-key:}") String anthropicApiKey,
            @Value("${ai.base-url:}") String baseUrl,
            @Value("${ai.assistant-model:}") String assistantModel,
            @Value("${ai.fast-model:}") String fastModel,
            @Value("${ai.assistant-fallback-models:}") String assistantFallbacks,
            @Value("${ai.fast-fallback-models:}") String fastFallbacks,
            @Value("${ai.timeout-seconds:60}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.provider = resolveProvider(provider, geminiApiKey, anthropicApiKey);
        String providerKey =
                switch (this.provider) {
                    case GEMINI -> geminiApiKey;
                    case ANTHROPIC -> anthropicApiKey;
                    case OPENAI -> "";
                };
        this.apiKey = firstNonBlank(apiKey, providerKey);
        this.assistantModel = firstNonBlank(assistantModel, this.provider.assistantModel);
        this.fastModel = firstNonBlank(fastModel, this.provider.fastModel, this.assistantModel);
        this.assistantModels = chain(this.assistantModel, assistantFallbacks, this.provider.assistantFallbacks);
        this.fastModels = chain(this.fastModel, fastFallbacks, this.provider.fastFallbacks);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(stripSlash(firstNonBlank(baseUrl, this.provider.baseUrl)))
                .requestFactory(factory)
                .build();

        if (this.apiKey.isEmpty()) {
            log.info("No AI API key (GEMINI_API_KEY / ANTHROPIC_API_KEY / AI_API_KEY) - AI features are off");
        } else if (this.assistantModel.isEmpty()) {
            log.warn("AI_PROVIDER=openai needs AI_ASSISTANT_MODEL - AI features are off");
        } else {
            log.info(
                    "AI features on: provider={}, assistant={}, fast={}",
                    this.provider,
                    String.join(" > ", this.assistantModels),
                    String.join(" > ", this.fastModels));
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty() && !assistantModel.isEmpty();
    }

    public ObjectNode newRequest(String model, int maxTokens) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("max_tokens", maxTokens);
        request.putArray("messages");
        return request;
    }

    public ObjectMapper json() {
        return objectMapper;
    }

    /**
     * Runs {@code call} with each model of the chain in turn while the provider answers "overloaded" (5xx) or "out
     * of quota" (429) - quotas are per model, so the next model usually still has some. Any other failure, or the
     * last model failing, is thrown. A whole generation (tool rounds included) stays on one model: thought
     * signatures from one model aren't valid for another.
     */
    public <T> T withModelFallback(List<String> models, Function<String, T> call) {
        AppException last = new AppException(ErrorCode.AI_UNAVAILABLE);
        for (String model : models) {
            try {
                return call.apply(model);
            } catch (AppException e) {
                ErrorCode code = e.getErrorCode();
                if (code != ErrorCode.AI_OVERLOADED && code != ErrorCode.AI_QUOTA_EXCEEDED) {
                    throw e;
                }
                last = e;
                log.info("{} is unavailable ({}), trying the next model", model, code);
            }
        }
        throw last;
    }

    /**
     * Sends one request (Anthropic Messages shape) and returns the answer in the same shape, whatever the provider.
     * A 5xx from the provider is retried twice on the same model; failures become {@link ErrorCode#AI_OVERLOADED}
     * (5xx), {@link ErrorCode#AI_QUOTA_EXCEEDED} (429) or {@link ErrorCode#AI_UNAVAILABLE} (anything else).
     */
    public JsonNode createMessage(ObjectNode request) {
        if (!isEnabled()) {
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        }
        String model = request.path("model").asText();
        for (int attempt = 0; ; attempt++) {
            try {
                JsonNode response = provider == Provider.ANTHROPIC ? anthropic(request) : openAiCompatible(request);
                if (response == null
                        || !response.path("content").isArray()
                        || "error".equals(response.path("stop_reason").asText())) {
                    throw new AppException(ErrorCode.AI_UNAVAILABLE);
                }
                return response;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status >= 500 && attempt < RETRY_DELAYS_MS.length) {
                    log.info("{} {} answered {} - retrying", provider, model, status);
                    pause(RETRY_DELAYS_MS[attempt]);
                    continue;
                }
                log.warn("{} {} rejected a request ({}): {}", provider, model, status, e.getResponseBodyAsString());
                throw new AppException(
                        status == 429
                                ? ErrorCode.AI_QUOTA_EXCEEDED
                                : status >= 500 ? ErrorCode.AI_OVERLOADED : ErrorCode.AI_UNAVAILABLE);
            } catch (RestClientException | JsonProcessingException e) {
                log.warn("Cannot reach {}: {}", provider, e.getMessage());
                throw new AppException(ErrorCode.AI_UNAVAILABLE);
            }
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    /** Primary, then the configured fallbacks ("none" = no fallback), else the provider's; no duplicates. */
    private static List<String> chain(String primary, String configured, List<String> providerDefaults) {
        Set<String> models = new LinkedHashSet<>();
        if (!primary.isEmpty()) {
            models.add(primary);
        }
        if (configured != null && !configured.isBlank()) {
            if (!"none".equalsIgnoreCase(configured.strip())) {
                Arrays.stream(configured.split(","))
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .forEach(models::add);
            }
        } else {
            models.addAll(providerDefaults);
        }
        return List.copyOf(models);
    }

    private JsonNode anthropic(ObjectNode request) throws JsonProcessingException {
        return restClient
                .post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(request))
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode openAiCompatible(ObjectNode request) throws JsonProcessingException {
        ObjectNode chat = OpenAiCompat.toChatRequest(request, objectMapper);
        JsonNode response = restClient
                .post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(chat))
                .retrieve()
                .body(JsonNode.class);
        return response == null ? null : OpenAiCompat.toMessage(response, objectMapper);
    }

    /** Joins the text blocks of a response. */
    public static String text(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        return text.toString().strip();
    }

    /** First {@code tool_use} block calling {@code name}, or null. */
    public static JsonNode toolUse(JsonNode response, String name) {
        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText())
                    && name.equals(block.path("name").asText())) {
                return block;
            }
        }
        return null;
    }

    /**
     * Fallback when a model answered a forced tool call with plain JSON text instead (some compatible servers ignore
     * {@code tool_choice}): the first {@code {...}} object in the text, or null.
     */
    public JsonNode jsonObjectIn(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
            return node.isObject() ? node : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** A {@code {"type":"text","text":...}} block, optionally marked for prompt caching (Anthropic only). */
    public ObjectNode textBlock(String text, boolean cache) {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "text");
        block.put("text", text);
        if (cache) {
            block.putObject("cache_control").put("type", "ephemeral");
        }
        return block;
    }

    public ObjectNode userMessage(String text) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "user");
        message.put("content", text);
        return message;
    }

    public ArrayNode array() {
        return objectMapper.createArrayNode();
    }

    /** Explicit AI_PROVIDER wins; otherwise Claude only when its key alone is set, else the free Gemini default. */
    private static Provider resolveProvider(String configured, String geminiKey, String anthropicKey) {
        if (configured != null && !configured.isBlank()) {
            try {
                return Provider.valueOf(configured.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown AI_PROVIDER '{}' - expected anthropic, gemini or openai", configured);
            }
        }
        boolean onlyAnthropic =
                (geminiKey == null || geminiKey.isBlank()) && anthropicKey != null && !anthropicKey.isBlank();
        return onlyAnthropic ? Provider.ANTHROPIC : Provider.GEMINI;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
