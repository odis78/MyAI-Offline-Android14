package com.dmitry.myai.infinix.bridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        MyAiAccessibilityService s=require();
        AccessibilityNodeInfo root=s.getRootInActiveWindow();
        boolean scrolled=false;
        List<AccessibilityNodeInfo> candidates=new ArrayList<>();
        if(root!=null){
            try{
                collectScrollable(root,candidates,0);
                candidates.sort(Comparator.comparingLong(MyAiAccessibilityService::area).reversed());
                for(AccessibilityNodeInfo node:candidates){
                    if(node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)){scrolled=true;break;}
                }
            }finally{
                for(AccessibilityNodeInfo node:candidates) if(node!=root) node.recycle();
                root.recycle();
            }
        }
        if(scrolled)return;
        DisplayMetrics dm=s.getResources().getDisplayMetrics();
        int x=Math.max(1,dm.widthPixels/2);
        int startY=Math.max(1,(int)(dm.heightPixels*0.78f));
        int endY=Math.max(1,(int)(dm.heightPixels*0.28f));
        Path p=new Path(); p.moveTo(x,startY); p.lineTo(x,endY);
        GestureDescription g=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p,0,350)).build();
        if(!s.dispatchGesture(g,null,null)) throw new IllegalStateException("Scroll gesture could not be dispatched");
    }
    private static void collectScrollable(AccessibilityNodeInfo node,List<AccessibilityNodeInfo> out,int depth){
        if(node==null||depth>30)return;
        if(node.isScrollable())out.add(node);
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo child=node.getChild(i);
            if(child==null)continue;
            collectScrollable(child,out,depth+1);
            if(!child.isScrollable())child.recycle();
        }
    }
    private static long area(AccessibilityNodeInfo node){
        Rect r=new Rect();node.getBoundsInScreen(r);
        return Math.max(0L,(long)r.width())*Math.max(0L,(long)r.height());
    }
    private static MyAiAccessibilityService require(){ if(instance==null) throw new IllegalStateException("Control Bridge not connected"); return instance; }
    public static void log(String s){ android.util.Log.i("MyAI-Infinix",s); }
}