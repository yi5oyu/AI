package com.mcp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServerApplication.class, args);
	}

//	@Bean
//	public ToolCallbackProvider tools(TestTool testTool) {
//		return MethodToolCallbackProvider.builder()
//			.toolObjects(testTool)
//			.build();
//	}

}
