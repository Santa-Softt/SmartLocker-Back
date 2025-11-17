package com.smartlockr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class SmartLockrApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartLockrApplication.class, args);
	}

}
