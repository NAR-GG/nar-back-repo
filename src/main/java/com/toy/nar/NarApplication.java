package com.toy.nar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableCaching
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class NarApplication {

	public static void main(String[] args) {
		SpringApplication.run(NarApplication.class, args);
	}

}
