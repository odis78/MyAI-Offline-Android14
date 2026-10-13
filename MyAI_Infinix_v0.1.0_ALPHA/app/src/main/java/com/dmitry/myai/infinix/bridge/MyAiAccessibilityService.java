package com.dmitry.myai.infinix.bridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.Intent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import android.view.accessibility.AccessibilityEvent;

public class MyAiAccessibilityService extends AccessibilityService {
    public interface ConnectionListener { void onConnectionChanged(boolean connected); }
    private static volatile MyAiAccessibilityService instance;
    private static final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();
    private static final AtomicInteger connectCount = new AtomicInteger();
    // Keep the most recently visible non-MyAI app so Back from the command UI can return to it.
    private static volatile String lastExternalPackage;
    public static boolean isConnected(){ return instance != null; }
    public static void addConnectionListener(ConnectionListener listener){
        listeners.add(listener);
        listener.onConnectionChanged(instance != null);
    }
    public static void removeConnectionListener(ConnectionListener listener){ listeners.remove(listener); }
    private static void publish(){
        boolean connected = instance != null;
        for (ConnectionListener listener : listeners) listener.onConnectionChanged(connected);
    }
    @Override public void onServiceConnected(){
        instance=this;
        int count = connectCount.incrementAndGet();
        log(count == 1
                ? "SERVICE CONNECTED — INFINIX ALPHA"
                : "SERVICE REBOUND — INFINIX ALPHA count=" + count);
        publish();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence packageName = e.getPackageName();
        if (packageName == null) return;
        String candidate = packageName.toString();
        if (candidate.isEmpty() || candidate.equals(getPackageName())) return;
        lastExternalPackage = candidate;
        log("FOREGROUND_APP_TRACKED package=" + candidate);
    }
    @Override public void onInterrupt(){ log("SERVICE INTERRUPTED"); }
    @Override public boolean onUnbind(Intent intent){
        if(instance==this) instance=null;
        log("SERVICE UNBOUND");
        publish();
        return super.onUnbind(intent);
    }
    @Override public void onDestroy(){
        if(instance==this) instance=null;
        log("SERVICE DESTROYED");
        publish();
        super.onDestroy();
    }
    public static AccessibilityNodeInfo root(){ return instance==null?null:instance.getRootInActiveWindow(); }
    public static AccessibilityNodeInfo focusedInput(){
        if(instance==null) return null;
        AccessibilityNodeInfo root=instance.getRootInActiveWindow();
        return root==null?null:root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
    }
    public static void home(){ performGlobalActionChecked(GLOBAL_ACTION_HOME, "Home"); }
    public static void back() {
        MyAiAccessibilityService service = require();
        String previousPackage = lastExternalPackage;
        if (previousPackage != null && !previousPackage.isEmpty()
                && !previousPackage.equals(service.getPackageName())) {
            Intent launch = service.getPackageManager().getLaunchIntentForPackage(previousPackage);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    service.startActivity(launch);
                    log("BACK_RETURN_TO_PREVIOUS_APP package=" + previousPackage);
                    return;
                } catch (RuntimeException e) {
                    log("BACK_PREVIOUS_APP_FAILED package=" + previousPackage
                            + " error=" + e.getClass().getSimpleName());
                }
            } else {
                log("BACK_PREVIOUS_APP_NO_LAUNCH_INTENT package=" + previousPackage);
            }
        }
        // No known external app: preserve Android's native Back behavior.
        performGlobalActionChecked(GLOBAL_ACTION_BACK, "Back");
    }
    public static void recents(){ performGlobalActionChecked(GLOBAL_ACTION_RECENTS, "Recents"); }

    private static void performGlobalActionChecked(int action, String label) {
        MyAiAccessibilityService service = require();
        if (!service.performGlobalAction(action)) {
            throw new IllegalStateException(label + " global action was rejected by Android");
        }
    }
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
    public static void scrollUp(){
        MyAiAccessibilityService s=require();
        AccessibilityNodeInfo root=s.getRootInActiveWindow();
        boolean scrolled=false;
        List<AccessibilityNodeInfo> candidates=new ArrayList<>();
        if(root!=null){
            try{
                collectScrollable(root,candidates,0);
                candidates.sort(Comparator.comparingLong(MyAiAccessibilityService::area).reversed());
                for(AccessibilityNodeInfo node:candidates){
                    if(node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)){scrolled=true;break;}
                }
            }finally{
                for(AccessibilityNodeInfo node:candidates) if(node!=root) node.recycle();
                root.recycle();
            }
        }
        if(scrolled)return;
        DisplayMetrics dm=s.getResources().getDisplayMetrics();
        int x=Math.max(1,dm.widthPixels/2);
        int startY=Math.max(1,(int)(dm.heightPixels*0.28f));
        int endY=Math.max(1,(int)(dm.heightPixels*0.78f));
        Path p=new Path(); p.moveTo(x,startY); p.lineTo(x,endY);
        GestureDescription g=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p,0,350)).build();
        if(!s.dispatchGesture(g,null,null)) throw new IllegalStateException("Scroll-up gesture could not be dispatched");
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