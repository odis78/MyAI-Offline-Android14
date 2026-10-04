package com.dmitry.myai.infinix;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;

public class MainActivity extends Activity {
    private TextView statusText;
    private TextView logText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);

        findViewById(R.id.openAccessibilityButton).setOnClickListener(
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.homeButton).setOnClickListener(v -> testHome());
        findViewById(R.id.backButton).setOnClickListener(v -> testBack());
        findViewById(R.id.scrollButton).setOnClickListener(v -> testScroll());
        findViewById(R.id.refreshButton).setOnClickListener(v -> refresh());

        refresh();
    }

    private void testHome() {
        runTest(() -> MyAiAccessibilityService.home(), "HOME");
    }

    private void testBack() {
        runTest(() -> MyAiAccessibilityService.back(), "BACK");
    }

    private void testScroll() {
        runTest(() -> MyAiAccessibilityService.scrollDown(), "SCROLL");
    }

    private void runTest(Runnable action, String name) {
        try {
            action.run();
            append("TEST dispatched: " + name);
        } catch (Exception e) {
            append("ERROR: " + e.getMessage());
        }
    }

    private void refresh() {
        String status = MyAiAccessibilityService.isConnected()
                ? "Control Bridge: ПОДКЛЮЧЕН"
                : "Control Bridge: НЕ ПОДКЛЮЧЕН";
        statusText.setText(status);
        append(status);
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
}
