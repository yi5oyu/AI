package com.mcp.server.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TestTool {
    @Tool(description = "mcpai 단어를 해석합니다.")
    public String mcpWord(@ToolParam(description = "mcpai 단어") String word) {
        return "m" +word+ word.charAt(0)*word.length() + "." + "ai";
    }
}
