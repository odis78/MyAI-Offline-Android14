package com.dmitry.myai.infinix.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strict, dependency-free MVP parser for the local tool protocol. */
public final class ToolProtocol {
    private ToolProtocol() {}
    public static ToolCall parse(String json) {
        if (json == null) throw new IllegalArgumentException("null command");
        String s=json.trim();
        if(!s.startsWith("{")||!s.endsWith("}")) throw new IllegalArgumentException("command must be JSON object");
        String tool=stringValue(s,"tool");
        if(tool.isEmpty()) throw new IllegalArgumentException("missing tool");
        Map<String,String> args=new LinkedHashMap<>();
        int argsStart=s.indexOf("\"args\"");
        if(argsStart>=0){
            int open=s.indexOf('{',argsStart), close=s.lastIndexOf('}');
            if(open<0||close<=open) throw new IllegalArgumentException("invalid args object");
            String body=s.substring(open+1,close);
            if(!body.trim().isEmpty()) for(String pair:splitPairs(body)){
                int colon=pair.indexOf(':'); if(colon<=0) throw new IllegalArgumentException("invalid argument");
                String k=unquote(pair.substring(0,colon).trim()), v=unquote(pair.substring(colon+1).trim());
                if(k.isEmpty()) throw new IllegalArgumentException("empty argument name"); args.put(k,v);
            }
        }
        return new ToolCall(tool,args);
    }
    private static String stringValue(String s,String key){
        String needle="\""+key+"\""; int i=s.indexOf(needle); if(i<0)return "";
        int colon=s.indexOf(':',i+needle.length()); if(colon<0)return "";
        return unquote(s.substring(colon+1).trim().split(",",2)[0].trim());
    }
    private static String unquote(String v){
        if(v.length()>=2&&v.charAt(0)=='\"'&&v.charAt(v.length()-1)=='\"') return v.substring(1,v.length()-1).replace("\\\"","\"").replace("\\\\","\\");
        throw new IllegalArgumentException("only JSON strings are accepted in MVP args");
    }
    private static java.util.List<String> splitPairs(String body){
        java.util.ArrayList<String> out=new java.util.ArrayList<>(); boolean quoted=false,escaped=false; int start=0;
        for(int i=0;i<body.length();i++){char c=body.charAt(i); if(escaped){escaped=false;continue;} if(c=='\\'&&quoted){escaped=true;continue;} if(c=='\"'){quoted=!quoted;continue;} if(c==','&&!quoted){out.add(body.substring(start,i));start=i+1;}}
        out.add(body.substring(start)); return out;
    }
}