package com.dmitry.myai.infinix;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.dmitry.myai.infinix.agent.AgentContracts;
import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;

public class MainActivity extends Activity {
    TextView status,log;
    AgentContracts.ToolAgent agent;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    // Accessibility tree traversal and tool waits must not block Android's UI thread.
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private boolean destroyed = false;
    private final MyAiAccessibilityService.ConnectionListener connectionListener =
            connected -> statusHandler.post(this::updateBridgeStatus);

    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        status=findViewById(R.id.status); log=findViewById(R.id.log); agent=new AgentContracts.ToolAgent(this);
        findViewById(R.id.accessibility).setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.testHome).setOnClickListener(v->run(()->MyAiAccessibilityService.home()));
        findViewById(R.id.testBack).setOnClickListener(v->run(()->MyAiAccessibilityService.back()));
        findViewById(R.id.testScroll).setOnClickListener(v->run(()->MyAiAccessibilityService.scrollDown()));
        findViewById(R.id.testScrollUp).setOnClickListener(v->runTool("{\"tool\":\"scroll_up\",\"args\":{}}"));
        findViewById(R.id.testScreen).setOnClickListener(v->runTool("{\"tool\":\"get_screen_state\",\"args\":{}}"));
        findViewById(R.id.testTool).setOnClickListener(v->runTool("{\"tool\":\"home\",\"args\":{}}"));
        MyAiAccessibilityService.addConnectionListener(connectionListener);
        updateBridgeStatus();
    }
    void run(Runnable r){
        commandExecutor.execute(() -> {
            try { r.run(); append("TEST dispatched"); }
            catch(Exception e){ append("ERROR: "+e.getMessage()); }
        });
    }
    void runTool(String json){
        commandExecutor.execute(() -> {
            try {
                var r=agent.dispatchJson(json);
                String result = r.tool + ": " + (r.success ? "OK " : "FAIL ") + r.message;
                append(result);
                android.util.Log.i("MyAI-Infinix", "TOOL_RESULT " + result);
                if(!r.dataJson.equals("{}")) append(r.dataJson);
            } catch(Exception e) {
                String message = "TOOL ERROR: " + (e.getMessage()==null ? e.getClass().getSimpleName() : e.getMessage());
                append(message);
                android.util.Log.e("MyAI-Infinix", message, e);
            }
        });
    }
    private void updateBridgeStatus(){
        if (destroyed || status == null) return;
        String text = MyAiAccessibilityService.isConnected()
                ? "Control Bridge: ПОДКЛЮЧЕН"
                : "Control Bridge: НЕ ПОДКЛЮЧЕН";
        status.setText(text);
        status.setContentDescription(text);
    }
    void append(String s){runOnUiThread(() -> { if(log==null)return; log.append(s+"\n"); log.post(() -> ((ScrollView)findViewById(R.id.logScroll)).fullScroll(View.FOCUS_DOWN)); });}
    @Override protected void onResume(){
        super.onResume();
        updateBridgeStatus();
    }
    @Override protected void onPause(){
        updateBridgeStatus();
        super.onPause();
    }
    @Override protected void onDestroy(){
        destroyed = true;
        MyAiAccessibilityService.removeConnectionListener(connectionListener);
        commandExecutor.shutdownNow();
        super.onDestroy();
    }
}