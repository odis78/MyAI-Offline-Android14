package com.dmitry.myai.infinix.tools;

public final class ToolResult {
    public final boolean success;
    public final String tool;
    public final String message;
    public final String dataJson;
    private ToolResult(boolean success,String tool,String message,String dataJson){this.success=success;this.tool=tool;this.message=message;this.dataJson=dataJson==null?"{}":dataJson;}
    public static ToolResult ok(String tool,String message,String dataJson){return new ToolResult(true,tool,message,dataJson);}
    public static ToolResult fail(String tool,String message){return new ToolResult(false,tool,message,"{}");}
}