package com.dmitry.myai.infinix.bridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;

public class MyAiAccessibilityService extends AccessibilityService {
    private static volatile MyAiAccessibilityService instance;
    public static boolean isConnected(){ return instance != null; }
    @Override public void onServiceConnected(){ instance=this; log("SERVICE CONNECTED — INFINIX ALPHA"); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){ log("SERVICE INTERRUPTED"); }
    @Override public void onDestroy(){ if(instance==this) instance=null; super.onDestroy(); }
    public static AccessibilityNodeInfo root(){ return instance==null?null:instance.getRootInActiveWindow(); }
    public static AccessibilityNodeInfo focusedInput(){
        if(instance==null) return null;
        AccessibilityNodeInfo root=instance.getRootInActiveWindow();
        return root==null?null:root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
    }
    public static void home(){ require().performGlobalAction(GLOBAL_ACTION_HOME); }
    public static void back(){ require().performGlobalAction(GLOBAL_ACTION_BACK); }
    public static void recents(){ require().performGlobalAction(GLOBAL_ACTION_RECENTS); }
    public static void scrollDown(){
        MyAiAccessibilityService s=require(); AccessibilityNodeInfo r=s.getRootInActiveWindow();
        if(r!=null && r.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return;
        Path p=new Path(); p.moveTo(540,1500); p.lineTo(540,500);
        GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,500)).build();
        s.dispatchGesture(g,null,null);
    }
    private static MyAiAccessibilityService require(){ if(instance==null) throw new IllegalStateException("Control Bridge not connected"); return instance; }
    public static void log(String s){ android.util.Log.i("MyAI-Infinix",s); }
}