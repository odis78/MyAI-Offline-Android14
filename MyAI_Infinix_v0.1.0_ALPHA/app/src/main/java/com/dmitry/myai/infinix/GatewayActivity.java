package com.dmitry.myai.infinix;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

import com.dmitry.myai.infinix.agent.AgentCommandParser;
import com.dmitry.myai.infinix.agent.AgentContracts;
import com.dmitry.myai.infinix.agent.AgentResultCodec;
import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;
import com.dmitry.myai.infinix.online.HttpOnlineGateway;

import java.util.UUID;

public final class GatewayActivity extends Activity {
    private static final String PREFS = "myai_settings";
    private static final String PREF_GATEWAY = "gateway_endpoint";
    private static final String PREF_SESSION = "gateway_session";

    private EditText endpoint;
    private EditText message;
    private TextView chat;
    private TextView log;
    private HttpOnlineGateway gateway;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_gateway);

        endpoint = findViewById(R.id.gatewayEndpoint);
        message = findViewById(R.id.gatewayMessage);
        chat = findViewById(R.id.gatewayChat);
        log = findViewById(R.id.gatewayLog);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        endpoint.setText(prefs.getString(PREF_GATEWAY, ""));
        findViewById(R.id.gatewaySendButton).setOnClickListener(v -> send());
        findViewById(R.id.gatewayBackButton).setOnClickListener(v -> finish());

        appendChat("Gateway: готов.");
        appendChat("Gateway: локальные команды MyAI не зависят от этого экрана.");
    }

    private void send() {
        String url = endpoint.getText().toString().trim();
        String text = message.getText().toString().trim();

        if (url.isEmpty()) {
            appendChat("MyAI: укажи URL Gateway.");
            return;
        }
        if (text.isEmpty()) {
            appendChat("MyAI: введи сообщение.");
            return;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_GATEWAY, url).apply();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String session = prefs.getString(PREF_SESSION, null);
        if (session == null || session.isBlank()) {
            session = "infinix-" + UUID.randomUUID();
            prefs.edit().putString(PREF_SESSION, session).apply();
        }

        appendChat("Вы: " + text);
        append("REQUEST session=" + session);
        message.setText("");

        if (gateway != null) gateway.shutdown();
        gateway = new HttpOnlineGateway(url);
        gateway.send(session, text, new HttpOnlineGateway.Callback() {
            @Override public void onSuccess(String assistantText, String toolCallJson) {
                runOnUiThread(() -> handleResponse(assistantText, toolCallJson));
            }
            @Override public void onFailure(String error) {
                runOnUiThread(() -> {
                    appendChat("MyAI: Gateway ошибка: " + error);
                    append("GATEWAY FAILED: " + error);
                });
            }
        });
    }

    private void handleResponse(String assistantText, String toolCallJson) {
        if (assistantText != null && !assistantText.isBlank()) appendChat("AI: " + assistantText);
        append("RESPONSE: HTTP 2xx");

        if (toolCallJson == null || toolCallJson.isBlank()) {
            append("TOOL_CALL: none");
            return;
        }

        append("TOOL_CALL: " + toolCallJson);
        AgentContracts.AgentCommand command = AgentCommandParser.fromModelJson(toolCallJson);
        if (command.action() == AgentContracts.Action.NONE) {
            appendChat("MyAI: команда Gateway отклонена.");
            append("TOOL_CALL REJECTED requestId=" + command.requestId());
            return;
        }

        appendChat("MyAI: выполняю " + command.action().name());
        execute(command);
    }

    private void execute(AgentContracts.AgentCommand command) {
        boolean ok = false;
        String message;

        try {
            switch (command.action()) {
                case HOME -> {
                    ok = MyAiAccessibilityService.isConnected()
                            && MyAiAccessibilityService.home();
                    message = ok ? "Готово: рабочий стол." : "Control Bridge не выполнил Home.";
                }
                case BACK -> {
                    ok = MyAiAccessibilityService.isConnected()
                            && MyAiAccessibilityService.back();
                    message = ok ? "Готово: назад." : "Control Bridge не выполнил Back.";
                }
                case SCROLL_DOWN -> {
                    ok = MyAiAccessibilityService.isConnected()
                            && MyAiAccessibilityService.scrollDown();
                    message = ok ? "Готово: прокрутил вниз." : "Control Bridge не выполнил Scroll.";
                }
                case OPEN_SETTINGS -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                        ok = true;
                    }
                    message = ok ? "Готово: открыл настройки." : "Настройки недоступны.";
                }
                case OPEN_APP -> {
                    Intent intent = getPackageManager()
                            .getLaunchIntentForPackage(command.payload());
                    if (intent != null) {
                        startActivity(intent);
                        ok = true;
                    }
                    message = ok ? "Готово: приложение запущено." :
                            "Пакет приложения не найден: " + command.payload();
                }
                default -> message = "Неизвестное действие.";
            }
        } catch (Exception e) {
            message = "Ошибка: " + e.getClass().getSimpleName();
        }

        AgentContracts.AgentResult result =
                new AgentContracts.AgentResult(command.requestId(), command.action(), ok, message);
        appendChat("MyAI: " + message);
        append("RESULT: " + AgentResultCodec.toJson(result));
    }

    private void appendChat(String value) {
        chat.append(value + "\n");
        chat.post(() -> ((ScrollView) findViewById(R.id.gatewayChatScroll))
                .fullScroll(View.FOCUS_DOWN));
    }

    private void append(String value) {
        log.append(value + "\n");
        log.post(() -> ((ScrollView) findViewById(R.id.gatewayLogScroll))
                .fullScroll(View.FOCUS_DOWN));
    }

    @Override protected void onDestroy() {
        if (gateway != null) gateway.shutdown();
        super.onDestroy();
    }
}
