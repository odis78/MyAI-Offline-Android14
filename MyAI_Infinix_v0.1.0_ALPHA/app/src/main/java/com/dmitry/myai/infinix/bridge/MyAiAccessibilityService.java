package com.dmitry.myai.infinix.bridge;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityWindowInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class MyAiAccessibilityService extends AccessibilityService {
    private static final String OWN_PACKAGE = "com.dmitry.myai.infinix";
    private static final String CHAT_SCROLL_ID = OWN_PACKAGE + ":id/chatScroll";
    private static final String LOG_SCROLL_ID = OWN_PACKAGE + ":id/logScroll";

    private static MyAiAccessibilityService instance;

    public static boolean isConnected() { return instance != null; }

    @Override public void onServiceConnected() {
        instance = this;
        log("SERVICE CONNECTED — INFINIX ALPHA");
    }

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // Commands are explicit; events are used only to keep window content accessible.
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

    /**
     * Scroll the main content of the currently visible application.
     * Never uses getRootInActiveWindow(), because with the keyboard open
     * that can point at the IME window instead of the application.
     */
    public static boolean scrollDown() {
        if (instance == null) throw new IllegalStateException("Control Bridge not connected");

        AccessibilityNodeInfo root = instance.findVisibleApplicationRoot();
        if (root != null) {
            try {
                AccessibilityNodeInfo target = findBestScrollableNode(root);
                if (target != null) {
                    try {
                        if (target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                            log("SCROLL app=forward-success");
                            return true;
                        }
                    } finally {
                        if (target != root) target.recycle();
                    }
                }
            } finally {
                root.recycle();
            }
        }

        boolean dispatched = instance.dispatchScrollGesture();
        log("SCROLL app=gesture-fallback dispatched=" + dispatched);
        return dispatched;
    }

    public static boolean scrollChat() {
        return instance != null && instance.scrollOwnView(CHAT_SCROLL_ID, "chat");
    }

    public static boolean scrollLog() {
        return instance != null && instance.scrollOwnView(LOG_SCROLL_ID, "log");
    }

    private boolean scrollOwnView(String viewId, String name) {
        AccessibilityNodeInfo root = findOwnApplicationRoot();
        if (root == null) {
            log("SCROLL own=" + name + " failed: app window not found");
            return false;
        }
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
            if (nodes == null || nodes.isEmpty()) {
                log("SCROLL own=" + name + " failed: view not found");
                return false;
            }
            for (AccessibilityNodeInfo node : nodes) {
                if (node == null) continue;
                try {
                    if (node.isScrollable() &&
                            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                        log("SCROLL own=" + name + " success");
                        return true;
                    }
                } finally {
                    node.recycle();
                }
            }
            log("SCROLL own=" + name + " failed: action rejected");
            return false;
        } finally {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findOwnApplicationRoot() {
        return findWindowRoot(true);
    }

    private AccessibilityNodeInfo findVisibleApplicationRoot() {
        return findWindowRoot(false);
    }

    /**
     * Select an application window only. This deliberately excludes TYPE_INPUT_METHOD,
     * which is the keyboard window and was the reason scrolling became unreliable.
     */
    private AccessibilityNodeInfo findWindowRoot(boolean ownOnly) {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) return null;

        AccessibilityNodeInfo active = null;
        AccessibilityNodeInfo focused = null;
        AccessibilityNodeInfo fallback = null;

        for (AccessibilityWindowInfo window : windows) {
            if (window == null) continue;
            try {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;

                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;

                CharSequence pkg = root.getPackageName();
                boolean isOwn = pkg != null && OWN_PACKAGE.contentEquals(pkg);
                if (ownOnly && !isOwn) {
                    root.recycle();
                    continue;
                }

                if (ownOnly) {
                    if (fallback == null) fallback = root;
                    else root.recycle();
                    continue;
                }

                if (window.isActive()) {
                    if (active == null) active = root;
                    else root.recycle();
                } else if (window.isFocused()) {
                    if (focused == null) focused = root;
                    else root.recycle();
                } else if (fallback == null) {
                    fallback = root;
                } else {
                    root.recycle();
                }
            } finally {
                window.recycle();
            }
        }

        if (active != null) {
            if (focused != null) focused.recycle();
            if (fallback != null) fallback.recycle();
            return active;
        }
        if (focused != null) {
            if (fallback != null) fallback.recycle();
            return focused;
        }
        return fallback;
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
            child.recycle();

            if (candidate == null) continue;

            Rect bounds = new Rect();
            candidate.getBoundsInScreen(bounds);
            long area = (long) bounds.width() * bounds.height();

            if (best == null || area > bestArea) {
                if (best != null && best != node) best.recycle();
                best = candidate;
                bestArea = area;
            } else if (candidate != node) {
                candidate.recycle();
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