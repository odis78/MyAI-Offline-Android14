package com.dmitry.myai.infinix;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import com.dmitry.myai.infinix.agent.AgentContracts;
import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;

public class MainActivity extends Activity {
    TextView status,log;
    AgentContracts.ToolAgent agent;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private boolean activityResumed = false;
    private final Runnable statusUpdater = new Runnable() {
        @Override public void run() {
            if (!activityResumed) return;
            updateBridgeStatus();
            statusHandler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        status=findViewById(R.id.status); log=findViewById(R.id.log); agent=new AgentContracts.ToolAgent(this);
        findViewById(R.id.accessibility).setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.testHome).setOnClickListener(v->run(()->MyAiAccessibilityService.home()));
        findViewById(R.id.testBack).setOnClickListener(v->run(()->MyAiAccessibilityService.back()));
        findViewById(R.id.testScroll).setOnClickListener(v->run(()->MyAiAccessibilityService.scrollDown()));
        findViewById(R.id.testScreen).setOnClickListener(v->runTool("{\"tool\":\"get_screen_state\",\"args\":{}}"));
        findViewById(R.id.testTool).setOnClickListener(v->runTool("{\"tool\":\"home\",\"args\":{}}"));
        updateBridgeStatus();
    }
    void run(Runnable r){try{r.run();append("TEST dispatched");}catch(Exception e){append("ERROR: "+e.getMessage());}}
    void runTool(String json){
        var r=agent.dispatchJson(json);
        String result = r.tool + ": " + (r.success ? "OK " : "FAIL ") + r.message;
        append(result);
        android.util.Log.i("MyAI-Infinix", "TOOL_RESULT " + result);
        if(!r.dataJson.equals("{}")) append(r.dataJson);
    }
    private void updateBridgeStatus(){
        if (status != null) status.setText(MyAiAccessibilityService.isConnected()?"Control Bridge: ПОДКЛЮЧЕН":"Control Bridge: НЕ ПОДКЛЮЧЕН");
    }
    void append(String s){log.append(s+"\n");log.post(()->((ScrollView)findViewById(R.id.logScroll)).fullScroll(View.FOCUS_DOWN));}
    @Override protected void onResume(){
        super.onResume();
        activityResumed = true;
        statusHandler.removeCallbacks(statusUpdater);
        statusUpdater.run();
    }
    @Override protected void onPause(){
        activityResumed = false;
        statusHandler.removeCallbacks(statusUpdater);
        super.onPause();
    }
}