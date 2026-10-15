package com.example.elsa_store.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Máy chủ giả của endpoint Chat Completions kiểu OpenAI (cách Gemini được gọi): trả lần lượt các phản hồi đã soạn và
 * ghi lại mọi request, để kiểm thử vòng gọi công cụ của trợ lý mà không cần khoá API thật.
 */
final class StubModelServer implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final Deque<Reply> script = new ArrayDeque<>();
    final List<JsonNode> requests = new CopyOnWriteArrayList<>();

    record Reply(int status, String body) {}

    StubModelServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requests.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            Reply reply;
            synchronized (script) {
                reply = script.isEmpty() ? new Reply(500, "{}") : script.poll();
            }
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Mô hình gọi một công cụ. */
    StubModelServer toolCall(String name, String argumentsJson) {
        String escaped = argumentsJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return then(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":"
                + "[{\"id\":\"call_" + requestsQueued() + "\",\"type\":\"function\",\"function\":{\"name\":\"" + name
                + "\",\"arguments\":\"" + escaped + "\"}}]}}]}");
    }

    /** Mô hình trả lời bằng chữ (kết thúc lượt). */
    StubModelServer text(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return then(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"}}]}");
    }

    StubModelServer then(int status, String body) {
        synchronized (script) {
            script.add(new Reply(status, body));
        }
        return this;
    }

    private int requestsQueued() {
        synchronized (script) {
            return script.size() + requests.size();
        }
    }

    AiClient client() {
        return new AiClient(JSON, "openai", "test-key", "", "", baseUrl(), "stub-model", "", "none", "none", 10);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
