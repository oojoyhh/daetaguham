package com.daetaguham;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DaetaguhamBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(DaetaguhamBackendApplication.class, args);
	}

}
