package com.dmitry.myai.infinix;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;

import java.util.Locale;

public class MainActivity extends Activity {
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
        findViewById(R.id.homeButton).setOnClickListener(v -> testHome());
        findViewById(R.id.backButton).setOnClickListener(v -> testBack());
        findViewById(R.id.scrollButton).setOnClickListener(v -> testScroll());
        findViewById(R.id.refreshButton).setOnClickListener(v -> refresh());
        findViewById(R.id.sendCommandButton).setOnClickListener(v -> submitCommand());

        appendChat("MyAI: Готов. Напиши команду.");
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

        String command = raw.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();

        if (containsAny(command, "домой", "на главный экран", "рабочий стол")) {
            append("COMMAND RECOGNIZED: HOME");
            executeBridgeCommand("HOME", MyAiAccessibilityService::home,
                    "Готово: открыл рабочий стол.");
            return;
        }
        if (containsAny(command, "назад", "вернись назад", "вернуться назад")) {
            append("COMMAND RECOGNIZED: BACK");
            executeBridgeCommand("BACK", MyAiAccessibilityService::back,
                    "Готово: выполнил команду «Назад».");
            return;
        }
        if (containsAny(command, "прокрути вниз", "прокрутка вниз", "пролистай вниз", "вниз")) {
            append("COMMAND RECOGNIZED: SCROLL_DOWN");
            executeBridgeCommand("SCROLL_DOWN", MyAiAccessibilityService::scrollDown,
                    "Готово: прокрутил экран вниз.");
            return;
        }

        String app = extractApp(command);
        if (app != null) {
            append("COMMAND RECOGNIZED: OPEN_APP " + app);
            openKnownApp(app);
            return;
        }

        if (containsAny(command, "настройки", "открой настройки")) {
            append("COMMAND RECOGNIZED: OPEN_SETTINGS");
            openExternalIntent(new Intent(Settings.ACTION_SETTINGS), "OPEN_SETTINGS",
                    "Готово: открыл настройки Android.");
            return;
        }

        appendChat("MyAI: Я пока не знаю эту команду.");
        appendChat("MyAI: Попробуй «Домой», «Назад», «Прокрути вниз», «Открой YouTube» или «Открой настройки».");
        append("COMMAND UNKNOWN");
    }

    private String extractApp(String command) {
        String[] prefixes = {"открой ", "запусти ", "открыть ", "запустить "};
        for (String prefix : prefixes) {
            if (command.startsWith(prefix)) {
                String value = command.substring(prefix.length()).trim();
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    private void openKnownApp(String app) {
        if (app.contains("youtube") || app.contains("ютуб")) {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
            if (intent != null) {
                openExternalIntent(intent, "OPEN_YOUTUBE", "Готово: открыл YouTube.");
            } else {
                appendChat("MyAI: YouTube не установлен. Интернет для этой команды не использую.");
                append("OPEN_YOUTUBE: FAILED (APP NOT INSTALLED)");
            }
            return;
        }
        if (app.contains("камер")) {
            openExternalIntent(new Intent("android.media.action.IMAGE_CAPTURE"),
                    "OPEN_CAMERA", "Готово: открыл камеру.");
            return;
        }
        if (app.contains("галере") || app.contains("фото")) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setType("image/*");
            openExternalIntent(intent, "OPEN_GALLERY", "Готово: открыл галерею.");
            return;
        }
        if (app.contains("настрой")) {
            openExternalIntent(new Intent(Settings.ACTION_SETTINGS), "OPEN_SETTINGS",
                    "Готово: открыл настройки Android.");
            return;
        }
        appendChat("MyAI: пока не умею открывать приложение «" + app + "».");
        append("OPEN_APP FAILED: unknown app");
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

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private void appendChat(String message) {
        chatText.append(message + "\n");
        chatText.post(() -> ((ScrollView) findViewById(R.id.chatScroll))
                .fullScroll(View.FOCUS_DOWN));
    }

    private void append(String message) {
        logText.append(message + "\n");
        logText.post(() -> ((ScrollView) findViewById(R.id.logScroll))
                .fullScroll(View.FOCUS_DOWN));
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
