package com.dmitry.myai.infinix.online;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HttpOnlineGateway implements OnlineGateway {
    private final String endpoint;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public HttpOnlineGateway(String endpoint) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
    }

    @Override
    public boolean isAvailable() {
        return !endpoint.isEmpty()
                && (endpoint.startsWith("https://") || endpoint.startsWith("http://"));
    }

    @Override
    public void send(String sessionId, String userText, Callback callback) {
        try {
            postJson(buildRequest(sessionId, userText), callback, true);
        } catch (Exception e) {
            callback.onFailure("request build failed: " + safeMessage(e.getMessage()));
        }
    }

    public void sendToolResult(String sessionId, String resultJson, Callback callback) {
        if (resultJson == null || resultJson.isBlank()) {
            callback.onFailure("empty tool result");
            return;
        }
        try {
            JSONObject result = new JSONObject(resultJson);
            JSONObject request = new JSONObject();
            request.put("session_id", sessionId == null ? "default" : sessionId);
            request.put("type", "tool_result");
            request.put("tool_result", result);
            postJson(request, callback, false);
        } catch (Exception e) {
            callback.onFailure("invalid tool result: " + e.getClass().getSimpleName());
        }
    }

    private JSONObject buildRequest(String sessionId, String userText) {
        JSONObject request = new JSONObject();
        request.put("session_id", sessionId == null ? "default" : sessionId);
        request.put("content", userText == null ? "" : userText);
        return request;
    }

    private void postJson(JSONObject request, Callback callback, boolean expectAssistantResponse) {
        if (!isAvailable()) {
            callback.onFailure("AI Gateway is not configured");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");

                byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String response = readAll(stream);

                if (code < 200 || code >= 300) {
                    callback.onFailure("AI Gateway HTTP " + code);
                    return;
                }

                if (!expectAssistantResponse) {
                    callback.onSuccess("", "");
                    return;
                }

                JSONObject json = new JSONObject(response);
                String assistantText = json.optString("content", "");
                String toolCallJson = json.optString("tool_call", "");
                callback.onSuccess(assistantText, toolCallJson);
            } catch (Exception e) {
                callback.onFailure(e.getClass().getSimpleName() + ": " + safeMessage(e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static String safeMessage(String value) {
        return value == null || value.isBlank() ? "network error" : value;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
