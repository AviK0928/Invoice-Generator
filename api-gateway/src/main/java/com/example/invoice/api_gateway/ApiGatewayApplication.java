package com.example.invoice.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
		"com.example.invoice.api_gateway",
		// AuthController, AuthService and AuthProperties live here now. The
		// default scan is this class's package, so naming it is not optional.
		"com.example.invoice.common.auth" })
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
