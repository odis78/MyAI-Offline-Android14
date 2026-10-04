package com.dmitry.myai.infinix;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;

public class MainActivity extends Activity {
    private TextView statusText;
    private TextView logText;
    private final Handler handler = new Handler();

    private interface TestAction {
        boolean run();
    }

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
        if (!MyAiAccessibilityService.isConnected()) {
            append("ERROR: Control Bridge not connected");
            return;
        }

        append("BACK test: opening Android Settings...");
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            handler.postDelayed(() -> runTest(
                    () -> MyAiAccessibilityService.back(),
                    "BACK on Settings"), 1200);
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
            handler.postDelayed(() -> runTest(
                    () -> MyAiAccessibilityService.scrollDown(),
                    "SCROLL on Settings"), 1200);
        } catch (Exception e) {
            append("ERROR opening Settings: " + e.getMessage());
        }
    }

    private void runTest(TestAction action, String name) {
        try {
            boolean ok = action.run();
            append("TEST " + name + ": " + (ok ? "SUCCESS" : "FAILED"));
        } catch (Exception e) {
            append("ERROR " + name + ": " + e.getMessage());
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

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
