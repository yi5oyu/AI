package com.mcp.client.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final ChatClient chatClient;

//    public TestController(ChatClient.Builder chatClientBuilder,
//        ToolCallbackProvider tools) {
//        this.chatClient = chatClientBuilder
//            .defaultTools(tools)
//            .build();
//    }

    @GetMapping("/{mcpAiWord}")
    String TransMcpAiWord(@PathVariable String mcpAiWord) {
        PromptTemplate pt = new PromptTemplate("""
                {mcpAiWord} 단어를 해석해주고 한국어로 답변하세요.
                """);
        Prompt p = pt.create(Map.of("mcpAiWord", mcpAiWord));
        return this.chatClient.prompt(p)
            .call()
            .content();
    }

}