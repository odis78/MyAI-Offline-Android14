package com.dmitry.myai.infinix;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;

import java.util.List;
import java.util.Locale;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.dmitry.myai.infinix.agent.AgentCommandParser;
import com.dmitry.myai.infinix.agent.AgentContracts;
import com.dmitry.myai.infinix.agent.AgentResultCodec;

public class MainActivity extends Activity {
    private static final String CHATGPT_PACKAGE = "com.openai.chatgpt";

    private TextView statusText;
    private TextView chatText;
    private TextView logText;
    private EditText commandInput;
    private final Handler handler = new Handler();
    private String lastStatus = null;

    private interface TestAction { boolean run(); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        chatText = findViewById(R.id.chatText);
        logText = findViewById(R.id.logText);
        commandInput = findViewById(R.id.commandInput);

        findViewById(R.id.openAccessibilityButton).setOnClickListener(
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.chatgptButton).setOnClickListener(v -> openChatGPT());
        findViewById(R.id.gatewayButton).setOnClickListener(v -> startActivity(new Intent(this, GatewayActivity.class)));
        findViewById(R.id.homeButton).setOnClickListener(v -> testHome());
        findViewById(R.id.backButton).setOnClickListener(v -> testBack());
        findViewById(R.id.scrollButton).setOnClickListener(v -> testScroll());
        findViewById(R.id.refreshButton).setOnClickListener(v -> refresh());
        findViewById(R.id.sendCommandButton).setOnClickListener(v -> submitCommand());

        appendChat("MyAI: Готов. Напиши команду.");
        appendChat("MyAI: ChatGPT подключается через установленное приложение; Control Bridge остаётся отдельным слоем управления.");
        refresh();
    }

    private void submitCommand() {
        String raw = commandInput.getText().toString().trim();
        if (raw.isEmpty()) {
            appendChat("MyAI: Введи команду.");
            append("COMMAND EMPTY");
            return;
        }

        appendChat("Вы: " + raw);
        commandInput.setText("");

        AgentContracts.AgentCommand command;
        if (raw.startsWith("{")) {
            command = AgentCommandParser.fromModelJson(raw);
            append("AGENT INPUT: MODEL TOOL PROTOCOL");
        } else {
            command = AgentCommandParser.fromNaturalLanguage(raw);
            append("AGENT INPUT: NATURAL LANGUAGE");
        }

        if (command.action() == AgentContracts.Action.NONE) {
            appendChat("MyAI: Я пока не знаю эту команду.");
            appendChat("MyAI: Попробуй «Домой», «Назад», «Прокрути вниз», «Открой YouTube», «Открой ChatGPT» или «Открой настройки».");
            append("COMMAND UNKNOWN requestId=" + command.requestId());
            return;
        }

        executeAgentCommand(command);
    }

    private void executeAgentCommand(AgentContracts.AgentCommand command) {
        switch (command.action()) {
            case HOME -> {
                append("AGENT TOOL: HOME requestId=" + command.requestId());
                executeAgentBridgeCommand(command, MyAiAccessibilityService::home,
                        "Готово: открыл рабочий стол.");
            }
            case BACK -> {
                append("AGENT TOOL: BACK requestId=" + command.requestId());
                hideKeyboard();
                handler.postDelayed(() -> executeAgentBridgeCommand(command,
                        MyAiAccessibilityService::back,
                        "Готово: выполнил команду «Назад»."), 250);
            }
            case SCROLL_DOWN -> {
                append("AGENT TOOL: SCROLL_DOWN requestId=" + command.requestId());
                hideKeyboard();
                executeAgentBridgeCommand(command, MyAiAccessibilityService::scrollDown,
                        "Готово: прокрутил экран вниз.");
            }
            case READ_SCREEN -> {
                append("AGENT TOOL: READ_SCREEN requestId=" + command.requestId());
                readScreenForAgent(command);
            }
            case OPEN_APP -> {
                append("AGENT TOOL: OPEN_APP payload=" + command.payload() +
                        " requestId=" + command.requestId());
                openKnownApp(command.payload(), command.requestId());
            }
            case OPEN_SETTINGS -> {
                append("AGENT TOOL: OPEN_SETTINGS requestId=" + command.requestId());
                openExternalIntent(new Intent(Settings.ACTION_SETTINGS), "OPEN_SETTINGS",
                        "Готово: открыл настройки Android.");
            }
            case NONE -> {
                append("AGENT TOOL: NONE");
            }
        }
    }

    private void openChatGPT() {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(CHATGPT_PACKAGE);

        if (intent == null) {
            intent = findLaunchIntentByName("chatgpt");
        }

        if (intent == null) {
            appendChat("MyAI: приложение ChatGPT не найдено.");
            appendChat("MyAI: Установи официальное приложение ChatGPT от OpenAI, затем повтори команду.");
            append("OPEN_CHATGPT: FAILED (APP NOT FOUND)");
            return;
        }

        openExternalIntent(intent, "OPEN_CHATGPT", "Готово: открыл ChatGPT.");
    }

    private void openKnownApp(String app) {
        openKnownApp(app, "local");
    }

    private void openKnownApp(String app, String requestId) {
        String normalized = normalizeAppName(app);

        if (matchesApp(normalized, "chatgpt", "чатgпт", "чатgpt", "чатгпт", "чатджпт")) {
            openChatGPT();
            return;
        }

        // Explicit aliases for the most common/system apps.
        if (matchesApp(normalized, "youtube", "ютуб", "ютюб", "youtub")) {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
            if (intent != null) {
                openExternalIntent(intent, "OPEN_YOUTUBE", "Готово: открыл YouTube.");
            } else {
                Intent fallback = findLaunchIntentByName("youtube");
                if (fallback == null) {
                    fallback = findLaunchIntentByName(normalized);
                }
                if (fallback != null) {
                    String label = fallback.getStringExtra("myai.label");
                    if (label == null || label.isEmpty()) label = "YouTube";
                    openExternalIntent(fallback, "OPEN_YOUTUBE", "Готово: открыл " + label + ".");
                } else {
                    appendChat("MyAI: YouTube не найден среди установленных приложений.");
                    append("OPEN_YOUTUBE: FAILED (APP NOT FOUND)");
                }
            }
            return;
        }
        if (matchesApp(normalized, "камера", "camera")) {
            openExternalIntent(new Intent("android.media.action.IMAGE_CAPTURE"),
                    "OPEN_CAMERA", "Готово: открыл камеру.");
            return;
        }
        if (matchesApp(normalized, "галерея", "gallery", "фото", "photos")) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setType("image/*");
            openExternalIntent(intent, "OPEN_GALLERY", "Готово: открыл галерею.");
            return;
        }
        if (matchesApp(normalized, "настройки", "settings")) {
            openExternalIntent(new Intent(Settings.ACTION_SETTINGS), "OPEN_SETTINGS",
                    "Готово: открыл настройки Android.");
            return;
        }

        Intent launch = findLaunchIntentByName(normalized);
        if (launch != null) {
            String label = launch.getStringExtra("myai.label");
            if (label == null || label.isEmpty()) label = app;
            openExternalIntent(launch, "OPEN_APP " + label,
                    "Готово: открыл " + label + ".");
            return;
        }

        appendChat("MyAI: не нашёл приложение «" + app + "».");
        appendChat("MyAI: Попробуй точное название приложения.");
        append("OPEN_APP FAILED: app not found");
    }

    private Intent findLaunchIntentByName(String requested) {
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(launcher, 0);

        ResolveInfo best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguousBest = false;

        for (ResolveInfo info : apps) {
            if (info.activityInfo == null) continue;
            CharSequence labelCs = info.loadLabel(pm);
            if (labelCs == null) continue;
            String label = normalizeAppName(labelCs.toString());
            if (label.isEmpty()) continue;

            if (label.equals(requested)) {
                best = info;
                bestScore = 0;
                ambiguousBest = false;
                break;
            }

            int distance = levenshtein(requested, label);
            int maxLen = Math.max(requested.length(), label.length());
            int allowed = maxLen <= 5 ? 1 : (maxLen <= 9 ? 2 : 3);
            if (distance <= allowed) {
                if (distance < bestScore) {
                    best = info;
                    bestScore = distance;
                    ambiguousBest = false;
                } else if (distance == bestScore) {
                    ambiguousBest = true;
                }
            }
        }

        if (best == null || ambiguousBest) {
            if (ambiguousBest) append("OPEN_APP: ambiguous fuzzy match for " + requested);
            return null;
        }

        Intent result = new Intent(launcher);
        result.setClassName(best.activityInfo.packageName, best.activityInfo.name);
        CharSequence label = best.loadLabel(pm);
        result.putExtra("myai.label", label == null ? requested : label.toString());
        return result;
    }

    private String normalizeAppName(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private boolean matchesApp(String value, String... aliases) {
        for (String alias : aliases) {
            String normalizedAlias = normalizeAppName(alias);
            if (value.equals(normalizedAlias)) return true;
            int maxLen = Math.max(value.length(), normalizedAlias.length());
            int allowed = maxLen <= 5 ? 1 : (maxLen <= 9 ? 2 : 3);
            if (levenshtein(value, normalizedAlias) <= allowed) return true;
        }
        return false;
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }

    private void openExternalIntent(Intent intent, String action, String successMessage) {
        try {
            if (intent.resolveActivity(getPackageManager()) == null) {
                appendChat("MyAI: не найдено приложение для команды «" + action + "».");
                append(action + ": FAILED");
                return;
            }
            startActivity(intent);
            appendChat(successMessage);
            append(action + ": SUCCESS");
        } catch (Exception e) {
            appendChat("MyAI: ошибка запуска " + action + ".");
            append(action + ": FAILED: " + e.getClass().getSimpleName());
        }
    }

    private void executeBridgeCommand(String name, TestAction action, String successMessage) {
        if (!MyAiAccessibilityService.isConnected()) {
            appendChat("MyAI: Control Bridge не подключен.");
            append(name + ": FAILED (BRIDGE OFF)");
            return;
        }
        try {
            boolean ok = action.run();
            appendChat(ok ? successMessage : "MyAI: команда «" + name + "» не выполнена.");
            append(name + ": " + (ok ? "SUCCESS" : "FAILED"));
        } catch (Exception e) {
            appendChat("MyAI: ошибка выполнения команды «" + name + "».");
            append(name + ": FAILED: " + e.getClass().getSimpleName());
        }
    }

    private void executeAgentBridgeCommand(AgentContracts.AgentCommand command,
                                      TestAction action, String successMessage) {
        AgentContracts.AgentResult result;
        if (!MyAiAccessibilityService.isConnected()) {
            result = new AgentContracts.AgentResult(
                    command.requestId(), command.action(), false, "Control Bridge not connected");
            appendChat("MyAI: Control Bridge не подключен.");
            append("AGENT RESULT: " + AgentResultCodec.toJson(result));
            return;
        }
        try {
            boolean ok = action.run();
            String message = ok ? successMessage : "Команда не выполнена.";
            result = new AgentContracts.AgentResult(
                    command.requestId(), command.action(), ok, message);
            appendChat(ok ? successMessage : "MyAI: команда не выполнена.");
            append("AGENT RESULT: " + AgentResultCodec.toJson(result));
        } catch (Exception e) {
            result = new AgentContracts.AgentResult(
                    command.requestId(), command.action(), false, e.getClass().getSimpleName());
            appendChat("MyAI: ошибка выполнения команды.");
            append("AGENT RESULT: " + AgentResultCodec.toJson(result));
        }
    }

    private void readScreenForAgent(AgentContracts.AgentCommand command) {
        AgentContracts.AgentResult result;
        if (!MyAiAccessibilityService.isConnected()) {
            result = new AgentContracts.AgentResult(command.requestId(), command.action(), false,
                    "Control Bridge not connected");
            appendChat("MyAI: Control Bridge не подключен.");
            append("AGENT RESULT: " + AgentResultCodec.toJson(result));
            return;
        }
        try {
            String screen = MyAiAccessibilityService.readScreenText();
            boolean ok = screen != null && !screen.isBlank();
            String message = ok ? screen : "На текущем экране текст не найден.";
            result = new AgentContracts.AgentResult(command.requestId(), command.action(), ok, message);
            appendChat(ok ? "MyAI: Содержимое экрана:\\n" + screen : "MyAI: " + message);
            append("AGENT RESULT: " + AgentResultCodec.toJson(result));
        } catch (Exception e) {
            result = new AgentContracts.AgentResult(command.requestId(), command.action(), false,
                    e.getClass().getSimpleName());
            appendChat("MyAI: ошибка чтения экрана.");
            append("AGENT RESULT: " + AgentResultCodec.toJson(result));
        }
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }

    private void testHome() {
        executeBridgeCommand("HOME TEST", MyAiAccessibilityService::home,
                "Готово: тест Home выполнен.");
    }

    private void testBack() {
        if (!MyAiAccessibilityService.isConnected()) {
            append("ERROR: Control Bridge not connected");
            return;
        }
        append("BACK test: opening Android Settings...");
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            handler.postDelayed(() -> executeBridgeCommand("BACK TEST",
                    MyAiAccessibilityService::back,
                    "Готово: тест Back выполнен."), 1200);
        } catch (Exception e) {
            append("ERROR opening Settings: " + e.getMessage());
        }
    }

    private void testScroll() {
        if (!MyAiAccessibilityService.isConnected()) {
            append("ERROR: Control Bridge not connected");
            return;
        }
        append("SCROLL test: opening Android Settings...");
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            handler.postDelayed(() -> executeBridgeCommand("SCROLL TEST",
                    MyAiAccessibilityService::scrollDown,
                    "Готово: тест прокрутки выполнен."), 1200);
        } catch (Exception e) {
            append("ERROR opening Settings: " + e.getMessage());
        }
    }

    private void refresh() {
        String status = MyAiAccessibilityService.isConnected()
                ? "Control Bridge: ПОДКЛЮЧЕН"
                : "Control Bridge: НЕ ПОДКЛЮЧЕН";
        statusText.setText(status);
        if (!status.equals(lastStatus)) {
            append(status);
            lastStatus = status;
        }
    }

    private void appendChat(String message) {
        chatText.append(message + "\n");
    }

    private void append(String message) {
        logText.append(message + "\n");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
