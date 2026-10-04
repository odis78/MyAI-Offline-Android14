package com.dmitry.myai.infinix;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import com.dmitry.myai.infinix.agent.AgentContracts;
import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;

public class MainActivity extends Activity {
    TextView status,log;
    AgentContracts.ToolAgent agent;
    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        status=findViewById(R.id.status); log=findViewById(R.id.log); agent=new AgentContracts.ToolAgent(this);
        findViewById(R.id.accessibility).setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.testHome).setOnClickListener(v->run(()->MyAiAccessibilityService.home()));
        findViewById(R.id.testBack).setOnClickListener(v->run(()->MyAiAccessibilityService.back()));
        findViewById(R.id.testScroll).setOnClickListener(v->run(()->MyAiAccessibilityService.scrollDown()));
        findViewById(R.id.testScreen).setOnClickListener(v->runTool("{\"tool\":\"get_screen_state\",\"args\":{}}"));
        findViewById(R.id.testTool).setOnClickListener(v->runTool("{\"tool\":\"home\",\"args\":{}}"));
        refresh();
    }
    void run(Runnable r){try{r.run();append("TEST dispatched");}catch(Exception e){append("ERROR: "+e.getMessage());}}
    void runTool(String json){var r=agent.dispatchJson(json); append(r.tool+": "+(r.success?"OK ":"FAIL ")+r.message); if(!r.dataJson.equals("{}")) append(r.dataJson);}
    void refresh(){status.setText(MyAiAccessibilityService.isConnected()?"Control Bridge: ПОДКЛЮЧЕН":"Control Bridge: НЕ ПОДКЛЮЧЕН");append(status.getText().toString());}
    void append(String s){log.append(s+"\n");log.post(()->((ScrollView)findViewById(R.id.logScroll)).fullScroll(View.FOCUS_DOWN));}
    @Override protected void onResume(){super.onResume();refresh();}
}