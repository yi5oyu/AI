package com.mcp.server.config;

import com.mcp.server.tool.TestTool;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {
    @Bean
    public List<ToolCallback> setTools(TestTool testTool) {
        return List.of(ToolCallbacks.from(testTool));
    }
}