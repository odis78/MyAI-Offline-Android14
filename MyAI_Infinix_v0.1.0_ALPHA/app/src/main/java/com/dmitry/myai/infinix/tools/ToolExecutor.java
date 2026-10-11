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
    private final Context context;
    private final PolicyGate policy;
    private final VerificationEngine verification = new VerificationEngine();

    public ToolExecutor(Context context, PolicyGate policy) {
        this.context = context.getApplicationContext();
        this.policy = policy;
    }

    public ToolResult execute(ToolCall call) {
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
            return verification.accepted(result)
                ? result : ToolResult.fail(call.tool, "verification failed");
        } catch (Exception e) {
            return ToolResult.fail(call.tool,
                e.getMessage() == null ? "execution error" : e.getMessage());
        }
    }

    private ToolResult screenState() {
        AccessibilityNodeInfo root = MyAiAccessibilityService.root();
        if (root == null) return ToolResult.fail("get_screen_state", "no active accessibility root");
        try {
            List<String> nodes = new ArrayList<>();
            collect(root, nodes, 0);
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

    private void collect(AccessibilityNodeInfo node, List<String> out, int depth) {
        if (node == null || depth > 8 || out.size() >= 80) return;
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (text != null || description != null || node.isClickable() || node.isEditable()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            out.add("{\"text\":" + JSONObject.quote(text == null ? "" : text.toString())
                + ",\"description\":" + JSONObject.quote(
                    description == null ? "" : description.toString())
                + ",\"clickable\":" + node.isClickable()
                + ",\"editable\":" + node.isEditable()
                + ",\"bounds\":" + JSONObject.quote(bounds.toShortString()) + "}");
        }

        for (int i = 0; i < node.getChildCount() && out.size() < 80; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                collect(child, out, depth + 1);
            } finally {
                child.recycle();
            }
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
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
