package com.dmitry.myai.infinix;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import com.dmitry.myai.infinix.agent.AgentContracts;
import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;

public class MainActivity extends Activity {
    TextView status, log, chat;
    EditText commandInput;
    AgentContracts.ToolAgent agent;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private boolean destroyed = false;
    private final MyAiAccessibilityService.ConnectionListener connectionListener =
            connected -> statusHandler.post(this::updateBridgeStatus);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        log = findViewById(R.id.log);
        chat = findViewById(R.id.chat);
        commandInput = findViewById(R.id.commandInput);
        agent = new AgentContracts.ToolAgent(this);

        findViewById(R.id.accessibility).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.sendCommand).setOnClickListener(v -> submitCommand());
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            submitCommand();
            return true;
        });
        findViewById(R.id.testHome).setOnClickListener(v -> runTool("{\"tool\":\"home\",\"args\":{}}"));
        findViewById(R.id.testBack).setOnClickListener(v -> runTool("{\"tool\":\"back\",\"args\":{}}"));
        findViewById(R.id.testScroll).setOnClickListener(v -> runTool("{\"tool\":\"scroll_down\",\"args\":{}}"));
        findViewById(R.id.testScrollUp).setOnClickListener(v -> runTool("{\"tool\":\"scroll_up\",\"args\":{}}"));
        findViewById(R.id.testScreen).setOnClickListener(v -> runTool("{\"tool\":\"get_screen_state\",\"args\":{}}"));
        findViewById(R.id.testTool).setOnClickListener(v -> runTool("{\"tool\":\"home\",\"args\":{}}"));
        MyAiAccessibilityService.addConnectionListener(connectionListener);
        updateBridgeStatus();
        addChat("MyAI", "Чат команд готов. Примеры: «назад», «домой», «прокрути вниз», «прочитай экран», «открой YouTube». Для управления телефоном включи службу специальных возможностей.");
        scheduleSmokeTool(getIntent());
    }

    private void scheduleSmokeTool(Intent intent) {
        if (intent == null) return;
        String smokeTool = intent.getStringExtra("myai_test_tool");
        if (smokeTool == null || smokeTool.trim().isEmpty()) return;
        statusHandler.postDelayed(() -> runTool(tool(smokeTool.trim(), "{}")), 1500);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        scheduleSmokeTool(intent);
    }

    private void submitCommand() {
        String raw = commandInput.getText().toString().trim();
        if (raw.isEmpty()) return;
        commandInput.setText("");
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(commandInput.getWindowToken(), 0);
        addChat("Вы", raw);
        String lower = raw.toLowerCase(Locale.ROOT);
        String json = null;
        if (containsAny(lower, "домой", "на главный экран", "главный экран")) {
            json = tool("home", "{}");
        } else if (containsAny(lower, "назад", "вернись", "вернуться")) {
            json = tool("back", "{}");
        } else if (containsAny(lower, "прокрути вниз", "прокрутка вниз", "листай вниз", "скроль вниз", "вниз")) {
            json = tool("scroll_down", "{}");
        } else if (containsAny(lower, "прокрути вверх", "прокрутка вверх", "листай вверх", "скроль вверх", "вверх")) {
            json = tool("scroll_up", "{}");
        } else if (containsAny(lower, "прочитай экран", "прочти экран", "что на экране", "состояние экрана")) {
            json = tool("get_screen_state", "{}");
        } else if (lower.contains("открой youtube") || lower.contains("запусти youtube") || lower.contains("открой ютуб")) {
            json = tool("open_app", "{\"package\":\"com.google.android.youtube\"}");
        } else if (lower.contains("открой chrome") || lower.contains("запусти chrome") || lower.contains("открой браузер")) {
            json = tool("open_app", "{\"package\":\"com.android.chrome\"}");
        } else if (lower.contains("открой настройки") || lower.contains("открой settings")) {
            json = tool("open_app", "{\"package\":\"com.android.settings\"}");
        } else if (lower.contains("открой галерею")) {
            addChat("MyAI", "Пока не могу надёжно определить пакет галереи на этом телефоне. Следующим шагом добавим выбор приложений по установленному списку.");
            return;
        } else {
            addChat("MyAI", "Пока не распознал команду. Сейчас поддерживаются: «домой», «назад», «прокрути вниз/вверх», «прочитай экран», «открой YouTube», «открой Chrome», «открой настройки». Это базовый командный маршрутизатор, не подключённая языковая модель.");
            return;
        }
        final String request = json;
        commandExecutor.execute(() -> {
            try {
                var result = agent.dispatchJson(request);
                String message = result.tool + ": " + (result.success ? "OK — " : "Ошибка — ") + result.message;
                addChat("MyAI", message);
                append(message);
                android.util.Log.i("MyAI-Infinix", "CHAT_COMMAND " + message);
                if ("get_screen_state".equals(result.tool) && !result.dataJson.equals("{}")) {
                    addChat("Экран", summarizeScreen(result.dataJson));
                }
            } catch (Exception e) {
                String message = "Ошибка команды: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                addChat("MyAI", message);
                append(message);
            }
        });
    }

    private static String tool(String name, String args) {
        try { return new JSONObject().put("tool", name).put("args", new JSONObject(args)).toString(); }
        catch (Exception e) { return "{\"tool\":\"" + name + "\",\"args\":{}}"; }
    }
    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
    private String summarizeScreen(String data) {
        try {
            org.json.JSONArray elements = new JSONObject(data).optJSONArray("elements");
            if (elements == null || elements.length() == 0) return "Экран прочитан, текстовых элементов не найдено.";
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < elements.length() && i < 12; i++) {
                JSONObject item = elements.optJSONObject(i);
                if (item == null) continue;
                String text = item.optString("text", "");
                String desc = item.optString("description", "");
                String label = !text.isEmpty() ? text : desc;
                if (!label.isEmpty()) {
                    if (out.length() > 0) out.append(" · ");
                    out.append(label);
                }
            }
            return out.length() == 0 ? "Экран прочитан; видимые элементы без текста." : out.toString();
        } catch (Exception e) { return "Экран получен, но результат не удалось разобрать."; }
    }
    private void addChat(String who, String text) {
        runOnUiThread(() -> { if (chat != null && !destroyed) chat.append(who + ": " + text + "\n\n"); });
    }
    void runTool(String json) {
        commandExecutor.execute(() -> {
            try {
                var r = agent.dispatchJson(json);
                String result = r.tool + ": " + (r.success ? "OK " : "FAIL ") + r.message;
                append(result);
                addChat("MyAI", result);
                android.util.Log.i("MyAI-Infinix", "TOOL_RESULT " + result);
            } catch (Exception e) { append("TOOL ERROR: " + e.getMessage()); }
        });
    }
    void append(String s) {
        runOnUiThread(() -> {
            if (log == null || destroyed) return;
            log.append(s + "\n");
            log.post(() -> ((ScrollView)findViewById(R.id.logScroll)).fullScroll(View.FOCUS_DOWN));
        });
    }
    private void updateBridgeStatus() {
        if (destroyed || status == null) return;
        String text = MyAiAccessibilityService.isConnected()
                ? "Control Bridge: ПОДКЛЮЧЕН" : "Control Bridge: НЕ ПОДКЛЮЧЕН";
        status.setText(text);
        status.setContentDescription(text);
    }
    @Override protected void onResume() { super.onResume(); updateBridgeStatus(); }
    @Override protected void onPause() { updateBridgeStatus(); super.onPause(); }
    @Override protected void onDestroy() {
        destroyed = true;
        MyAiAccessibilityService.removeConnectionListener(connectionListener);
        commandExecutor.shutdownNow();
        super.onDestroy();
    }
}