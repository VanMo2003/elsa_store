package com.example.elsa_store.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dịch qua lại giữa định dạng Anthropic mà trợ lý dùng và định dạng OpenAI mà Gemini nói. */
class OpenAiCompatTest {

    private final ObjectMapper json = new ObjectMapper();

    private ObjectNode request() throws Exception {
        return (ObjectNode) json.readTree(
                """
                {"model":"m","max_tokens":512,
                 "system":[{"type":"text","text":"Quy tắc A"},{"type":"text","text":"Hồ sơ B"}],
                 "tools":[{"name":"search_products","description":"Tìm","input_schema":{"type":"object"}}],
                 "messages":[{"role":"user","content":"xin chào"}]}
                """);
    }

    @Test
    void systemBlocksBecomeOneSystemMessageAndToolsBecomeFunctions() throws Exception {
        ObjectNode chat = OpenAiCompat.toChatRequest(request(), json);

        assertEquals("m", chat.path("model").asText());
        assertEquals("system", chat.path("messages").get(0).path("role").asText());
        assertEquals("Quy tắc A\n\nHồ sơ B", chat.path("messages").get(0).path("content").asText());
        assertEquals("user", chat.path("messages").get(1).path("role").asText());
        JsonNode tool = chat.path("tools").get(0);
        assertEquals("function", tool.path("type").asText());
        assertEquals("search_products", tool.path("function").path("name").asText());
        assertEquals("object", tool.path("function").path("parameters").path("type").asText());
    }

    @Test
    void toolCallResponseBecomesToolUseAndKeepsTheRawMessage() throws Exception {
        JsonNode response = json.readTree(
                """
                {"choices":[{"message":{"role":"assistant","content":null,
                  "tool_calls":[{"id":"call_1","type":"function",
                    "function":{"name":"search_products","arguments":"{\\"keywords\\":[\\"giay\\"]}"}}],
                  "extra_content":{"google":{"thought_signature":"sig"}}}}]}
                """);

        ObjectNode message = OpenAiCompat.toMessage(response, json);

        assertEquals("tool_use", message.path("stop_reason").asText());
        JsonNode use = message.path("content").get(0);
        assertEquals("tool_use", use.path("type").asText());
        assertEquals("search_products", use.path("name").asText());
        assertEquals("giay", use.path("input").path("keywords").get(0).asText());
        // Chữ ký suy nghĩ của Gemini 3 được giữ nguyên để gửi lại ở lượt sau.
        JsonNode raw = message.path("content").get(1);
        assertEquals(OpenAiCompat.RAW_BLOCK, raw.path("type").asText());
        assertEquals("sig", raw.path("message").path("extra_content").path("google").path("thought_signature").asText());
    }

    @Test
    void textResponseEndsTheTurn() throws Exception {
        JsonNode response =
                json.readTree("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\" Xin chào \"}}]}");

        ObjectNode message = OpenAiCompat.toMessage(response, json);

        assertEquals("end_turn", message.path("stop_reason").asText());
        assertEquals("Xin chào", AiClient.text(message));
    }

    @Test
    void missingChoicesIsAnError() throws Exception {
        ObjectNode message = OpenAiCompat.toMessage(json.readTree("{}"), json);

        assertEquals("error", message.path("stop_reason").asText());
    }

    @Test
    void assistantTurnWithToolCallIsSentBackVerbatimAndResultsBecomeToolMessages() throws Exception {
        ObjectNode request = request();
        ArrayNode messages = (ArrayNode) request.get("messages");
        ObjectNode assistant = messages.addObject().put("role", "assistant");
        ArrayNode content = assistant.putArray("content");
        content.addObject().put("type", "tool_use").put("id", "call_1").put("name", "search_products");
        ObjectNode raw = content.addObject().put("type", OpenAiCompat.RAW_BLOCK).putObject("message");
        raw.put("role", "assistant").putArray("tool_calls").addObject().put("id", "call_1");
        ArrayNode results = messages.addObject().put("role", "user").putArray("content");
        results.addObject()
                .put("type", "tool_result")
                .put("tool_use_id", "call_1")
                .put("content", "{\"found\":1}");
        results.addObject()
                .put("type", "tool_result")
                .put("tool_use_id", "call_2")
                .put("content", "mã sai")
                .put("is_error", true);

        ObjectNode chat = OpenAiCompat.toChatRequest(request, json);

        // system, user, assistant(raw), tool, tool
        assertEquals(5, chat.path("messages").size());
        assertEquals("call_1", chat.path("messages").get(2).path("tool_calls").get(0).path("id").asText());
        assertEquals("tool", chat.path("messages").get(3).path("role").asText());
        assertEquals("{\"found\":1}", chat.path("messages").get(3).path("content").asText());
        assertTrue(chat.path("messages").get(4).path("content").asText().startsWith("Lỗi: "));
    }
}
