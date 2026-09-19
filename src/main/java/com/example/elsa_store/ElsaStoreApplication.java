package com.example.elsa_store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ElsaStoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(ElsaStoreApplication.class, args);
	}

}
