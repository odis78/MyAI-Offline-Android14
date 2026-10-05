package com.dmitry.myai.infinix.bridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class MyAiAccessibilityService extends AccessibilityService {
    private static MyAiAccessibilityService instance;

    public static boolean isConnected() {
        return instance != null;
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        log("SERVICE CONNECTED — INFINIX ALPHA");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Events are intentionally observed by the bridge; command execution is explicit.
    }

    @Override
    public void onInterrupt() {
        log("SERVICE INTERRUPTED");
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    public static boolean home() {
        if (instance == null) {
            throw new IllegalStateException("Control Bridge not connected");
        }
        boolean ok = instance.performGlobalAction(GLOBAL_ACTION_HOME);
        log("HOME result=" + ok);
        return ok;
    }

    public static boolean back() {
        if (instance == null) {
            throw new IllegalStateException("Control Bridge not connected");
        }
        boolean ok = instance.performGlobalAction(GLOBAL_ACTION_BACK);
        log("BACK result=" + ok);
        return ok;
    }

    public static String readScreenText() {
        if (instance == null) {
            throw new IllegalStateException("Control Bridge not connected");
        }
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "";
        StringBuilder out = new StringBuilder();
        appendNodeText(root, out);
        String text = out.toString().trim();
        log("READ_SCREEN chars=" + text.length());
        return text;
    }

    private static void appendNodeText(AccessibilityNodeInfo node, StringBuilder out) {
        if (node == null) return;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && text.length() > 0) appendUniqueLine(out, text.toString());
        if (desc != null && desc.length() > 0) appendUniqueLine(out, desc.toString());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) appendNodeText(child, out);
        }
    }

    private static void appendUniqueLine(StringBuilder out, String value) {
        String line = value.replaceAll("\\s+", " ").trim();
        if (line.isEmpty()) return;
        if (out.length() == 0) {
            out.append(line);
            return;
        }
        String[] lines = out.toString().split("\\n");
        for (String existing : lines) {
            if (existing.equals(line)) return;
        }
        out.append("\n").append(line);
    }

    public static boolean scrollDown() {
        if (instance == null) {
            throw new IllegalStateException("Control Bridge not connected");
        }

        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root != null) {
            if (performScrollOnTree(root)) {
                log("SCROLL node-action=success");
                return true;
            }
        }

        boolean dispatched = instance.dispatchSwipeGesture();
        log("SCROLL gesture-dispatched=" + dispatched);
        return dispatched;
    }

    private static boolean performScrollOnTree(AccessibilityNodeInfo node) {
        if (node.isScrollable() &&
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return true;
        }

        List<AccessibilityNodeInfo> children = node.getChildCount() > 0
                ? getChildren(node)
                : null;

        if (children != null) {
            for (AccessibilityNodeInfo child : children) {
                if (child != null && performScrollOnTree(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<AccessibilityNodeInfo> getChildren(AccessibilityNodeInfo node) {
        java.util.ArrayList<AccessibilityNodeInfo> result = new java.util.ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                result.add(child);
            }
        }
        return result;
    }

    private boolean dispatchSwipeGesture() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) {
            return false;
        }

        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);

        float x = dm.widthPixels * 0.5f;
        float startY = dm.heightPixels * 0.78f;
        float endY = dm.heightPixels * 0.28f;

        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 550))
                .build();

        return dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                log("SCROLL gesture=completed");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                log("SCROLL gesture=cancelled");
            }
        }, null);
    }

    public static void log(String s) {
        android.util.Log.i("MyAI-Infinix", s);
    }
}
