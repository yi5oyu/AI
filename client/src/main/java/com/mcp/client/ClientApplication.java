package com.mcp.client;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClientApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().systemProperties().load();

		SpringApplication.run(ClientApplication.class, args);
	}

}
