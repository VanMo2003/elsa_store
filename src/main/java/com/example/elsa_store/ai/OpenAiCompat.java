package com.example.elsa_store.ai;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Translates between the Anthropic Messages shape the AI services build (system blocks, {@code tool_use} /
 * {@code tool_result} content blocks) and the OpenAI Chat Completions shape that Gemini's compatibility endpoint,
 * Groq, OpenRouter, Ollama… speak. The services never see the difference.
 *
 * <p>Gemini 3 needs its "thought signatures" back on the turn after a function call. Rather than depend on where a
 * provider hides them, the assistant message that asked for tools is carried through untouched in a private
 * {@value #RAW_BLOCK} content block and sent back verbatim.
 */
final class OpenAiCompat {

    static final String RAW_BLOCK = "_openai_raw";

    private OpenAiCompat() {}

    /** Anthropic-shaped request → {@code POST /chat/completions} body. */
    static ObjectNode toChatRequest(JsonNode request, ObjectMapper json) {
        ObjectNode out = json.createObjectNode();
        out.put("model", request.path("model").asText());
        out.put("max_tokens", request.path("max_tokens").asInt(1024));
        ArrayNode messages = out.putArray("messages");

        StringBuilder system = new StringBuilder();
        for (JsonNode block : request.path("system")) {
            if (!system.isEmpty()) {
                system.append("\n\n");
            }
            system.append(block.path("text").asText());
        }
        if (!system.isEmpty()) {
            messages.addObject().put("role", "system").put("content", system.toString());
        }

        for (JsonNode message : request.path("messages")) {
            String role = message.path("role").asText();
            JsonNode content = message.path("content");
            if (content.isTextual()) {
                messages.addObject().put("role", role).put("content", content.asText());
            } else if ("assistant".equals(role)) {
                messages.add(assistantMessage(content, json));
            } else {
                for (JsonNode block : content) {
                    switch (block.path("type").asText()) {
                        case "tool_result" -> {
                            String text = block.path("content").asText();
                            messages.addObject()
                                    .put("role", "tool")
                                    .put(
                                            "tool_call_id",
                                            block.path("tool_use_id").asText())
                                    .put("content", block.path("is_error").asBoolean() ? "Lỗi: " + text : text);
                        }
                        case "text" -> messages.addObject()
                                .put("role", "user")
                                .put("content", block.path("text").asText());
                        default -> {
                            // nothing else is ever sent by the services
                        }
                    }
                }
            }
        }

        if (request.has("tools")) {
            ArrayNode tools = out.putArray("tools");
            for (JsonNode tool : request.path("tools")) {
                ObjectNode function = tools.addObject().put("type", "function").putObject("function");
                function.put("name", tool.path("name").asText());
                function.put("description", tool.path("description").asText());
                function.set("parameters", tool.path("input_schema"));
            }
        }
        // A forced tool call: only ever used with a single tool, so "required" means exactly that tool.
        if ("tool".equals(request.path("tool_choice").path("type").asText())) {
            out.put("tool_choice", "required");
        }
        return out;
    }

    /** Chat Completions response → Anthropic-shaped {@code {content: [...], stop_reason}}. */
    static ObjectNode toMessage(JsonNode response, ObjectMapper json) {
        JsonNode message = response.path("choices").path(0).path("message");
        ObjectNode out = json.createObjectNode();
        ArrayNode content = out.putArray("content");
        if (!message.isObject()) {
            out.put("stop_reason", "error");
            return out;
        }

        String text = text(message.path("content"));
        if (!text.isBlank()) {
            content.addObject().put("type", "text").put("text", text);
        }

        ArrayNode calls = json.createArrayNode();
        for (JsonNode call : message.path("tool_calls")) {
            ObjectNode copy = call.deepCopy();
            if (copy.path("id").asText().isBlank()) {
                copy.put(
                        "id",
                        "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            }
            calls.add(copy);
            ObjectNode block = content.addObject();
            block.put("type", "tool_use");
            block.put("id", copy.path("id").asText());
            block.put("name", copy.path("function").path("name").asText());
            block.set("input", arguments(copy.path("function").path("arguments"), json));
        }

        if (!calls.isEmpty()) {
            // Keep only what the next request may carry: provider-specific siblings like "reasoning" can be rejected.
            ObjectNode raw = json.createObjectNode();
            raw.put("role", "assistant");
            raw.set("content", message.path("content").isMissingNode() ? json.nullNode() : message.get("content"));
            raw.set("tool_calls", calls);
            if (message.has("extra_content")) {
                raw.set("extra_content", message.get("extra_content"));
            }
            content.addObject().put("type", RAW_BLOCK).set("message", raw);
        }
        out.put("stop_reason", calls.isEmpty() ? "end_turn" : "tool_use");
        return out;
    }

    private static ObjectNode assistantMessage(JsonNode blocks, ObjectMapper json) {
        for (JsonNode block : blocks) {
            if (RAW_BLOCK.equals(block.path("type").asText())) {
                return block.get("message").deepCopy();
            }
        }
        ObjectNode message = json.createObjectNode().put("role", "assistant");
        StringBuilder text = new StringBuilder();
        ArrayNode calls = json.createArrayNode();
        for (JsonNode block : blocks) {
            switch (block.path("type").asText()) {
                case "text" -> text.append(block.path("text").asText());
                case "tool_use" -> {
                    ObjectNode call = calls.addObject()
                            .put("id", block.path("id").asText())
                            .put("type", "function");
                    call.putObject("function")
                            .put("name", block.path("name").asText())
                            .put("arguments", block.path("input").toString());
                }
                default -> {
                    // no other block types exist on this path
                }
            }
        }
        if (text.isEmpty()) {
            message.putNull("content");
        } else {
            message.put("content", text.toString());
        }
        if (!calls.isEmpty()) {
            message.set("tool_calls", calls);
        }
        return message;
    }

    /** Message content is normally a string; some providers send a list of {@code {type:text,text}} parts. */
    private static String text(JsonNode content) {
        if (content.isTextual()) {
            return content.asText().strip();
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content) {
            if ("text".equals(part.path("type").asText())) {
                text.append(part.path("text").asText());
            }
        }
        return text.toString().strip();
    }

    /** Tool arguments arrive as a JSON string (OpenAI) or an object (some compatible servers). */
    private static JsonNode arguments(JsonNode arguments, ObjectMapper json) {
        if (arguments.isObject()) {
            return arguments;
        }
        try {
            JsonNode parsed = json.readTree(arguments.asText("{}"));
            return parsed != null && parsed.isObject() ? parsed : json.createObjectNode();
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }
}
