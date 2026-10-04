package com.dmitry.myai.infinix.tools;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import com.dmitry.myai.infinix.bridge.MyAiAccessibilityService;
import com.dmitry.myai.infinix.safety.PolicyGate;
import com.dmitry.myai.infinix.verification.VerificationEngine;
import java.util.ArrayList;
import java.util.List;

public final class ToolExecutor {
    private final Context context;
    private final PolicyGate policy;
    private final VerificationEngine verification=new VerificationEngine();
    public ToolExecutor(Context context,PolicyGate policy){this.context=context.getApplicationContext();this.policy=policy;}
    public ToolResult execute(ToolCall call){
        if(call==null)return ToolResult.fail("","null tool call");
        if(!policy.allowed(call))return ToolResult.fail(call.tool,"blocked by policy");
        try{
            ToolResult result;
            switch(call.tool){
                case "get_screen_state": result=screenState(); break;
                case "find_node": result=findNode(call.args.get("text")); break;
                case "click": result=click(call.args); break;
                case "type_text": result=typeText(call.args.get("text")); break;
                case "wait_for": result=waitFor(call.args.get("text")); break;
                case "open_app": result=openApp(call.args.get("package")); break;
                case "back": MyAiAccessibilityService.back(); result=ToolResult.ok("back","dispatched","{}"); break;
                case "home": MyAiAccessibilityService.home(); result=ToolResult.ok("home","dispatched","{}"); break;
                case "recents": MyAiAccessibilityService.recents(); result=ToolResult.ok("recents","dispatched","{}"); break;
                default: result=ToolResult.fail(call.tool,"unknown tool");
            }
            return verification.accepted(result)?result:ToolResult.fail(call.tool,"verification failed");
        }catch(Exception e){return ToolResult.fail(call.tool,e.getMessage()==null?"execution error":e.getMessage());}
    }
    private ToolResult screenState(){
        AccessibilityNodeInfo root=MyAiAccessibilityService.root(); if(root==null)return ToolResult.fail("get_screen_state","no active accessibility root");
        List<String> nodes=new ArrayList<>(); collect(root,nodes,0);
        StringBuilder json=new StringBuilder("{\"elements\":[");
        for(int i=0;i<nodes.size();i++){if(i>0)json.append(',');json.append(nodes.get(i));} json.append("]}");
        return ToolResult.ok("get_screen_state","screen captured",json.toString());
    }
    private void collect(AccessibilityNodeInfo n,List<String> out,int depth){
        if(n==null||depth>8||out.size()>=80)return;
        CharSequence text=n.getText(),desc=n.getContentDescription();
        if(text!=null||desc!=null||n.isClickable()||n.isEditable()){
            Rect b=new Rect();n.getBoundsInScreen(b);
            out.add("{\"text\":"+q(text)+",\"description\":"+q(desc)+",\"clickable\":"+n.isClickable()+",\"editable\":"+n.isEditable()+",\"bounds\":\""+b+"\"}");
        }
        for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),out,depth+1);
    }
    private ToolResult findNode(String text){
        if(text==null||text.isEmpty())return ToolResult.fail("find_node","missing text");
        AccessibilityNodeInfo root=MyAiAccessibilityService.root();if(root==null)return ToolResult.fail("find_node","no active accessibility root");
        List<AccessibilityNodeInfo> matches=root.findAccessibilityNodeInfosByText(text);
        return matches.isEmpty()?ToolResult.fail("find_node","node not found: "+text):ToolResult.ok("find_node","node found","{\"count\":"+matches.size()+"}");
    }
    private ToolResult click(java.util.Map<String,String> args){
        String text=args.get("text");if(text==null||text.isEmpty())return ToolResult.fail("click","missing text");
        AccessibilityNodeInfo root=MyAiAccessibilityService.root();if(root==null)return ToolResult.fail("click","no active accessibility root");
        for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(text)){
            if(n.isClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return ToolResult.ok("click","clicked","{}");
            AccessibilityNodeInfo p=n.getParent();if(p!=null&&p.isClickable()&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK))return ToolResult.ok("click","clicked parent","{}");
        }
        return ToolResult.fail("click","clickable node not found: "+text);
    }
    private ToolResult typeText(String text){
        if(text==null)return ToolResult.fail("type_text","missing text");
        AccessibilityNodeInfo focus=MyAiAccessibilityService.focusedInput();if(focus==null)return ToolResult.fail("type_text","no focused input");
        android.os.Bundle b=new android.os.Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b)?ToolResult.ok("type_text","text entered","{}"):ToolResult.fail("type_text","set text failed");
    }
    private ToolResult waitFor(String text){
        if(text==null||text.isEmpty())return ToolResult.fail("wait_for","missing text");
        long deadline=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<deadline){
            AccessibilityNodeInfo root=MyAiAccessibilityService.root();
            if(root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty())return ToolResult.ok("wait_for","condition met","{}");
            try{Thread.sleep(150);}catch(InterruptedException e){Thread.currentThread().interrupt();return ToolResult.fail("wait_for","interrupted");}
        }
        return ToolResult.fail("wait_for","timeout: "+text);
    }
    private ToolResult openApp(String pkg){
        if(pkg==null||pkg.isEmpty())return ToolResult.fail("open_app","missing package");
        Intent i=context.getPackageManager().getLaunchIntentForPackage(pkg);if(i==null)return ToolResult.fail("open_app","package not found: "+pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);return ToolResult.ok("open_app","launch dispatched","{\"package\":\""+escape(pkg)+"\"}");
    }
    private static String q(CharSequence s){return "\""+escape(s==null?"":s.toString())+"\"";}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
}