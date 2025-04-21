build.gradle

repositories {
mavenCentral()
}

ext {
set('springAiVersion', "1.0.0-M7")
}

dependencies {
implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

dependencyManagement {
imports {
mavenBom "org.springframework.ai:spring-ai-bom:${springAiVersion}"
}
}

application.yml

spring:
  application:
    name: server
  ai.mcp.server:
    name: mcp-server
    version: 1.0.0
    enabled: true

server.port: 8086

https://github.com/modelcontextprotocol/inspector

npx @modelcontextprotocol/inspector node build/index.js

netstat -ano | findstr :6274
taskkill /PID /[PID번호] /F

