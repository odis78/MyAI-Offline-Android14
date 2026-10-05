package com.dmitry.myai.infinix.bridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class MyAiAccessibilityService extends AccessibilityService {
    private static MyAiAccessibilityService instance;

    public static boolean isConnected() { return instance != null; }

    @Override public void onServiceConnected() {
        instance = this;
        log("SERVICE CONNECTED — INFINIX ALPHA");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Events are intentionally observed by the bridge; command execution is explicit.
    }

    @Override public void onInterrupt() { log("SERVICE INTERRUPTED"); }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean home() {
        if (instance == null) throw new IllegalStateException("Control Bridge not connected");
        boolean ok = instance.performGlobalAction(GLOBAL_ACTION_HOME);
        log("HOME result=" + ok);
        return ok;
    }

    public static boolean back() {
        if (instance == null) throw new IllegalStateException("Control Bridge not connected");
        boolean ok = instance.performGlobalAction(GLOBAL_ACTION_BACK);
        if (ok) {
            log("BACK global=success");
            return true;
        }
        boolean dispatched = instance.dispatchBackSwipe();
        log("BACK global=false swipe-fallback dispatched=" + dispatched);
        return dispatched;
    }

    public static boolean scrollDown() {
        if (instance == null) throw new IllegalStateException("Control Bridge not connected");

        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo target = null;
            try {
                target = findBestScrollableNode(root);
                if (target != null) {
                    boolean forward = target.performAction(
                            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                    if (forward) {
                        log("SCROLL node-action=forward-success");
                        return true;
                    }
                    boolean backward = target.performAction(
                            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                    if (backward) {
                        log("SCROLL node-action=backward-success");
                        return true;
                    }
                    log("SCROLL node-action=failed");
                }
            } finally {
                if (target != null && target != root) target.recycle();
                root.recycle();
            }
        }

        boolean dispatched = instance.dispatchScrollGesture();
        log("SCROLL gesture-fallback dispatched=" + dispatched);
        return dispatched;
    }

    private static AccessibilityNodeInfo findBestScrollableNode(AccessibilityNodeInfo root) {
        WindowManager wm = (WindowManager) instance.getSystemService(WINDOW_SERVICE);
        if (wm == null) return null;
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return findScrollable(root, dm.widthPixels / 2, dm.heightPixels / 2);
    }

    private static AccessibilityNodeInfo findScrollable(
            AccessibilityNodeInfo node, int centerX, int centerY) {
        AccessibilityNodeInfo best = null;
        long bestArea = 0;

        if (node.isScrollable()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (!bounds.isEmpty() && bounds.contains(centerX, centerY)) {
                best = node;
                bestArea = (long) bounds.width() * bounds.height();
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo candidate = findScrollable(child, centerX, centerY);
            if (candidate != null) {
                Rect bounds = new Rect();
                candidate.getBoundsInScreen(bounds);
                long area = (long) bounds.width() * bounds.height();
                if (best == null || area > bestArea) {
                    best = candidate;
                    bestArea = area;
                }
            }
        }
        return best;
    }

    private boolean dispatchScrollGesture() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return false;
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);

        float x = dm.widthPixels * 0.5f;
        float startY = dm.heightPixels * 0.80f;
        float endY = dm.heightPixels * 0.25f;

        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 450))
                .build();

        return dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                log("SCROLL gesture=completed");
            }
            @Override public void onCancelled(GestureDescription gestureDescription) {
                log("SCROLL gesture=cancelled");
            }
        }, null);
    }

    private boolean dispatchBackSwipe() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return false;
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);

        float startX = 2f;
        float endX = Math.min(dm.widthPixels * 0.45f, 360f);
        float y = dm.heightPixels * 0.5f;

        Path path = new Path();
        path.moveTo(startX, y);
        path.lineTo(endX, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 350))
                .build();

        return dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                log("BACK swipe=completed");
            }
            @Override public void onCancelled(GestureDescription gestureDescription) {
                log("BACK swipe=cancelled");
            }
        }, null);
    }

    public static void log(String s) {
        android.util.Log.i("MyAI-Infinix", s);
    }
}
