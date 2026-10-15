package com.dmitry.myai.infinix.tools;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;
import com.dmitry.myai.infinix.safety.PolicyGate;
import com.dmitry.myai.infinix.verification.VerificationEngine;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ToolExecutor {
    private static final int SCREEN_MAX_DEPTH = 15;
    private static final int SCREEN_MAX_VISITED_NODES = 300;
    private static final int SCREEN_MAX_OUTPUT_NODES = 80;
    private static final long SCREEN_MAX_TRAVERSAL_MS = 1000L;

    private final Context context;
    private final PolicyGate policy;
    private final VerificationEngine verification = new VerificationEngine();

    public ToolExecutor(Context context, PolicyGate policy) {
        // Keep the Activity context so app launches join the caller's task stack.
        // ToolExecutor is owned by MainActivity/ToolAgent and is not a process singleton.
        this.context = context;
        this.policy = policy;
    }

    public ToolResult execute(ToolCall call) {
        android.util.Log.i("MyAI-Infinix", "GET_SCREEN_STATE_TRACE A execute-enter tool="
            + (call == null ? "null" : call.tool) + " thread=" + Thread.currentThread().getName());
        if (call == null) return ToolResult.fail("", "null tool call");
        if (!policy.allowed(call)) return ToolResult.fail(call.tool, "blocked by policy");
        try {
            ToolResult result;
            switch (call.tool) {
                case "get_screen_state": result = screenState(); break;
                case "find_node": result = findNode(call.args.get("text")); break;
                case "click": result = click(call.args); break;
                case "type_text": result = typeText(call.args.get("text")); break;
                case "wait_for": result = waitFor(call.args.get("text")); break;
                case "open_app": result = openApp(call.args.get("package")); break;
                case "scroll_down":
                    MyAiAccessibilityService.scrollDown();
                    result = ToolResult.ok("scroll_down", "scroll dispatched", "{}");
                    break;
                case "scroll_up":
                    MyAiAccessibilityService.scrollUp();
                    result = ToolResult.ok("scroll_up", "scroll dispatched", "{}");
                    break;
                case "back":
                    MyAiAccessibilityService.back();
                    result = ToolResult.ok("back", "dispatched", "{}");
                    break;
                case "home":
                    MyAiAccessibilityService.home();
                    result = ToolResult.ok("home", "dispatched", "{}");
                    break;
                case "recents":
                    MyAiAccessibilityService.recents();
                    result = ToolResult.ok("recents", "dispatched", "{}");
                    break;
                default: result = ToolResult.fail(call.tool, "unknown tool");
            }
            android.util.Log.i("MyAI-Infinix", "GET_SCREEN_STATE_TRACE D execute-result tool="
                + call.tool + " success=" + result.success + " message=" + result.message);
            return verification.accepted(result)
                ? result : ToolResult.fail(call.tool, "verification failed");
        } catch (Exception e) {
            android.util.Log.e("MyAI-Infinix",
                "GET_SCREEN_STATE_TRACE E execute-exception tool=" + call.tool
                    + " thread=" + Thread.currentThread().getName()
                    + " type=" + e.getClass().getName()
                    + " message=" + e.getMessage(), e);
            return ToolResult.fail(call.tool,
                e.getMessage() == null ? "execution error" : e.getMessage());
        }
    }

    private ToolResult screenState() {
        long traceStart = android.os.SystemClock.elapsedRealtime();
        android.util.Log.i("MyAI-Infinix", "GET_SCREEN_STATE_TRACE B root-request-begin thread="
            + Thread.currentThread().getName());
        AccessibilityNodeInfo root = MyAiAccessibilityService.root();
        android.util.Log.i("MyAI-Infinix", "GET_SCREEN_STATE_TRACE B root-request-end rootNull="
            + (root == null) + " elapsedMs=" + (android.os.SystemClock.elapsedRealtime() - traceStart));
        if (root == null) return ToolResult.fail("get_screen_state", "no active accessibility root");
        try {
            List<String> nodes = new ArrayList<>();
            int[] visitedNodes = new int[] {0};
            int[] limitFlags = new int[] {0};
            long traversalStart = android.os.SystemClock.elapsedRealtime();
            long traversalDeadline = traversalStart + SCREEN_MAX_TRAVERSAL_MS;
            android.util.Log.i("MyAI-Infinix", "GET_SCREEN_STATE_TRACE C traversal-begin"
                + " maxDepth=" + SCREEN_MAX_DEPTH
                + " maxVisitedNodes=" + SCREEN_MAX_VISITED_NODES
                + " maxTraversalMs=" + SCREEN_MAX_TRAVERSAL_MS);
            collect(root, nodes, 0, visitedNodes, traversalDeadline, limitFlags);
            if (limitFlags[0] != 0) {
                android.util.Log.w("MyAI-Infinix", "SCREEN_TRAVERSAL_LIMIT_REACHED reason="
                    + limitReason(limitFlags[0]) + " outputNodeCount=" + nodes.size()
                    + " visitedNodeCount=" + visitedNodes[0]
                    + " traversalElapsedMs="
                    + (android.os.SystemClock.elapsedRealtime() - traversalStart));
            }
            android.util.Log.i("MyAI-Infinix", "GET_SCREEN_STATE_TRACE C traversal-end outputNodeCount="
                + nodes.size() + " visitedNodeCount=" + visitedNodes[0]
                + " traversalElapsedMs=" + (android.os.SystemClock.elapsedRealtime() - traversalStart)
                + " elapsedMs=" + (android.os.SystemClock.elapsedRealtime() - traceStart));
            StringBuilder json = new StringBuilder("{\"elements\":[");
            for (int i = 0; i < nodes.size(); i++) {
                if (i > 0) json.append(',');
                json.append(nodes.get(i));
            }
            json.append("]}");
            return ToolResult.ok("get_screen_state", "screen captured", json.toString());
        } finally {
            root.recycle();
        }
    }

    private void collect(AccessibilityNodeInfo node, List<String> out, int depth,
                         int[] visitedNodes, long deadlineMs, int[] limitFlags) {
        if (node == null) return;
        if (depth > SCREEN_MAX_DEPTH) {
            if (limitFlags[0] == 0) limitFlags[0] = 1;
            return;
        }
        if (visitedNodes[0] >= SCREEN_MAX_VISITED_NODES) {
            if (limitFlags[0] == 0) limitFlags[0] = 2;
            return;
        }
        if (android.os.SystemClock.elapsedRealtime() >= deadlineMs) {
            if (limitFlags[0] == 0) limitFlags[0] = 3;
            return;
        }
        if (out.size() >= SCREEN_MAX_OUTPUT_NODES) return;

        visitedNodes[0]++;
        final long nodeStart = android.os.SystemClock.elapsedRealtime();
        long t = android.os.SystemClock.elapsedRealtime();
        CharSequence text = node.getText();
        long textMs = android.os.SystemClock.elapsedRealtime() - t;
        t = android.os.SystemClock.elapsedRealtime();
        CharSequence description = node.getContentDescription();
        long descriptionMs = android.os.SystemClock.elapsedRealtime() - t;
        t = android.os.SystemClock.elapsedRealtime();
        boolean clickable = node.isClickable();
        boolean editable = node.isEditable();
        long flagsMs = android.os.SystemClock.elapsedRealtime() - t;
        if (text != null || description != null || clickable || editable) {
            Rect bounds = new Rect();
            t = android.os.SystemClock.elapsedRealtime();
            node.getBoundsInScreen(bounds);
            long boundsMs = android.os.SystemClock.elapsedRealtime() - t;
            out.add("{\"text\":" + JSONObject.quote(text == null ? "" : text.toString())
                + ",\"description\":" + JSONObject.quote(
                    description == null ? "" : description.toString())
                + ",\"clickable\":" + clickable
                + ",\"editable\":" + editable
                + ",\"bounds\":" + JSONObject.quote(bounds.toShortString()) + "}");
            if (textMs >= 50 || descriptionMs >= 50 || flagsMs >= 50 || boundsMs >= 50) {
                android.util.Log.w("MyAI-Infinix", "SCREEN_NODE_SLOW depth=" + depth
                    + " textMs=" + textMs + " descriptionMs=" + descriptionMs
                    + " flagsMs=" + flagsMs + " boundsMs=" + boundsMs);
            }
        }

        if (android.os.SystemClock.elapsedRealtime() >= deadlineMs) {
            if (limitFlags[0] == 0) limitFlags[0] = 3;
            return;
        }
        t = android.os.SystemClock.elapsedRealtime();
        int childCount = node.getChildCount();
        long childCountMs = android.os.SystemClock.elapsedRealtime() - t;
        if (childCountMs >= 50) {
            android.util.Log.w("MyAI-Infinix", "SCREEN_CHILD_COUNT_SLOW depth=" + depth
                + " elapsedMs=" + childCountMs + " childCount=" + childCount);
        }
        for (int i = 0; i < childCount && out.size() < SCREEN_MAX_OUTPUT_NODES; i++) {
            if (visitedNodes[0] >= SCREEN_MAX_VISITED_NODES) {
                if (limitFlags[0] == 0) limitFlags[0] = 2;
                break;
            }
            if (android.os.SystemClock.elapsedRealtime() >= deadlineMs) {
                if (limitFlags[0] == 0) limitFlags[0] = 3;
                break;
            }
            t = android.os.SystemClock.elapsedRealtime();
            AccessibilityNodeInfo child = node.getChild(i);
            long getChildMs = android.os.SystemClock.elapsedRealtime() - t;
            if (getChildMs >= 50) {
                android.util.Log.w("MyAI-Infinix", "SCREEN_GET_CHILD_SLOW depth=" + depth
                    + " index=" + i + " elapsedMs=" + getChildMs);
            }
            if (child == null) continue;
            try {
                collect(child, out, depth + 1, visitedNodes, deadlineMs, limitFlags);
            } finally {
                child.recycle();
            }
        }
        long nodeMs = android.os.SystemClock.elapsedRealtime() - nodeStart;
        if (nodeMs >= 100) {
            android.util.Log.w("MyAI-Infinix", "SCREEN_NODE_TOTAL_SLOW depth=" + depth
                + " elapsedMs=" + nodeMs + " children=" + childCount);
        }
    }

    private String limitReason(int flag) {
        switch (flag) {
            case 1: return "max_depth";
            case 2: return "max_visited_nodes";
            case 3: return "time_budget";
            default: return "unknown";
        }
    }

    private ToolResult findNode(String text) {
        if (text == null || text.isEmpty()) return ToolResult.fail("find_node", "missing text");
        AccessibilityNodeInfo root = MyAiAccessibilityService.root();
        if (root == null) return ToolResult.fail("find_node", "no active accessibility root");
        List<AccessibilityNodeInfo> matches = null;
        try {
            matches = root.findAccessibilityNodeInfosByText(text);
            return matches == null || matches.isEmpty()
                ? ToolResult.fail("find_node", "node not found: " + text)
                : ToolResult.ok("find_node", "node found", "{\"count\":" + matches.size() + "}");
        } finally {
            recycleAll(matches);
            root.recycle();
        }
    }

    private ToolResult click(Map<String, String> args) {
        String text = args.get("text");
        if (text == null || text.isEmpty()) return ToolResult.fail("click", "missing text");
        AccessibilityNodeInfo root = MyAiAccessibilityService.root();
        if (root == null) return ToolResult.fail("click", "no active accessibility root");
        List<AccessibilityNodeInfo> matches = null;
        try {
            matches = root.findAccessibilityNodeInfosByText(text);
            if (matches != null) {
                for (AccessibilityNodeInfo node : matches) {
                    if (node == null) continue;
                    if (node.isClickable()
                        && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return ToolResult.ok("click", "clicked", "{}");
                    }
                    AccessibilityNodeInfo parent = node.getParent();
                    if (parent != null) {
                        try {
                            if (parent.isClickable()
                                && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                return ToolResult.ok("click", "clicked parent", "{}");
                            }
                        } finally {
                            parent.recycle();
                        }
                    }
                }
            }
            return ToolResult.fail("click", "clickable node not found: " + text);
        } finally {
            recycleAll(matches);
            root.recycle();
        }
    }

    private ToolResult typeText(String text) {
        if (text == null) return ToolResult.fail("type_text", "missing text");
        AccessibilityNodeInfo focused = MyAiAccessibilityService.focusedInput();
        if (focused == null) return ToolResult.fail("type_text", "no focused input");
        try {
            Bundle args = new Bundle();
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                ? ToolResult.ok("type_text", "text entered", "{}")
                : ToolResult.fail("type_text", "set text failed");
        } finally {
            focused.recycle();
        }
    }

    private ToolResult waitFor(String text) {
        if (text == null || text.isEmpty()) return ToolResult.fail("wait_for", "missing text");
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            AccessibilityNodeInfo root = MyAiAccessibilityService.root();
            if (root != null) {
                List<AccessibilityNodeInfo> matches = null;
                try {
                    matches = root.findAccessibilityNodeInfosByText(text);
                    if (matches != null && !matches.isEmpty()) {
                        return ToolResult.ok("wait_for", "condition met", "{}");
                    }
                } finally {
                    recycleAll(matches);
                    root.recycle();
                }
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.fail("wait_for", "interrupted");
            }
        }
        return ToolResult.fail("wait_for", "timeout: " + text);
    }

    private ToolResult openApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return ToolResult.fail("open_app", "missing package");
        }
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) return ToolResult.fail("open_app", "package not found: " + packageName);
        // Launch from the caller Activity without NEW_TASK. This preserves the
        // expected MyAI -> target app -> MyAI Back stack instead of creating or
        // foregrounding a separate task based on package task affinity.
        // Keep this behavior covered by the isolated Run 15 regression smoke test.
        context.startActivity(intent);
        return ToolResult.ok("open_app", "launch dispatched",
            "{\"package\":" + JSONObject.quote(packageName) + "}");
    }

    private static void recycleAll(List<AccessibilityNodeInfo> nodes) {
        if (nodes == null) return;
        for (AccessibilityNodeInfo node : nodes) {
            if (node != null) node.recycle();
        }
    }
}
