
build.gradle

ext {
set('springAiVersion', "1.0.0-M7")
}

dependencies {
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.ai:spring-ai-starter-mcp-client-webflux'
implementation 'org.springframework.ai:spring-ai-starter-model-ollama'
implementation 'org.springframework.ai:spring-ai-starter-model-openai'
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