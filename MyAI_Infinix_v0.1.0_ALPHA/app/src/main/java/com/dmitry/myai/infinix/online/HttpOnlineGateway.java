package com.dmitry.myai.infinix.online;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HttpOnlineGateway implements OnlineGateway {
    private static final int MAX_REQUEST_TEXT = 16 * 1024;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final String endpoint;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public HttpOnlineGateway(String endpoint) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
    }

    @Override
    public boolean isAvailable() {
        if (endpoint.isEmpty() || endpoint.length() > 2048) {
            return false;
        }
        try {
            URL url = new URL(endpoint);
            String protocol = url.getProtocol();
            return ("https".equalsIgnoreCase(protocol) || "http".equalsIgnoreCase(protocol))
                    && url.getHost() != null
                    && !url.getHost().isBlank()
                    && url.getUserInfo() == null
                    && url.getRef() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void send(String sessionId, String userText, Callback callback) {
        if (userText != null && userText.length() > MAX_REQUEST_TEXT) {
            callback.onFailure("message too long (max " + MAX_REQUEST_TEXT + " characters)");
            return;
        }
        try {
            postJson(buildRequest(sessionId, userText), callback);
        } catch (Exception e) {
            callback.onFailure("request build failed: " + safeMessage(e.getMessage()));
        }
    }

    public void sendToolResult(String sessionId, String resultJson, Callback callback) {
        if (resultJson == null || resultJson.isBlank()) {
            callback.onFailure("empty tool result");
            return;
        }
        if (resultJson.length() > MAX_REQUEST_TEXT) {
            callback.onFailure("tool result too large");
            return;
        }
        try {
            JSONObject result = new JSONObject(resultJson);
            JSONObject request = new JSONObject();
            request.put("session_id", sessionId == null ? "default" : sessionId);
            request.put("type", "tool_result");
            request.put("tool_result", result);
            postJson(request, callback);
        } catch (Exception e) {
            callback.onFailure("invalid tool result: " + e.getClass().getSimpleName());
        }
    }

    private JSONObject buildRequest(String sessionId, String userText) throws Exception {
        JSONObject request = new JSONObject();
        request.put("session_id", sessionId == null ? "default" : sessionId);
        request.put("content", userText == null ? "" : userText);
        return request;
    }

    private void postJson(JSONObject request, Callback callback) {
        if (!isAvailable()) {
            callback.onFailure("AI Gateway URL is invalid or not configured");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");

                byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
                if (body.length > MAX_REQUEST_TEXT * 4) {
                    callback.onFailure("request payload too large");
                    return;
                }

                try (java.io.OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String response = readAll(stream, MAX_RESPONSE_BYTES);

                if (code < 200 || code >= 300) {
                    callback.onFailure("AI Gateway HTTP " + code);
                    return;
                }

                if (response.isBlank()) {
                    callback.onSuccess("", "");
                    return;
                }

                JSONObject json = new JSONObject(response);
                String assistantText = json.optString("content", "");
                String toolCallJson = json.optString("tool_call", "");

                if (assistantText.length() > MAX_RESPONSE_BYTES
                        || toolCallJson.length() > MAX_REQUEST_TEXT) {
                    callback.onFailure("AI Gateway response is too large");
                    return;
                }

                callback.onSuccess(assistantText, toolCallJson);
            } catch (Exception e) {
                callback.onFailure(e.getClass().getSimpleName() + ": " + safeMessage(e.getMessage()));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static String readAll(InputStream stream, int maxBytes) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        int total = 0;
        char[] buffer = new char[4096];
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            int count;
            while ((count = reader.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) {
                    throw new IllegalStateException("response too large");
                }
                out.append(buffer, 0, count);
            }
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
