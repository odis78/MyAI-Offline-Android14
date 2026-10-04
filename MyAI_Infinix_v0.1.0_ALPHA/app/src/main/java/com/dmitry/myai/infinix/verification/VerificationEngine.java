package com.dmitry.myai.infinix.verification;

import com.dmitry.myai.infinix.tools.ToolResult;

public final class VerificationEngine {
    public static final int MAX_RETRIES=2;
    public boolean accepted(ToolResult result){return result!=null&&result.success;}
}