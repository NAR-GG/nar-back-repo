package com.toy.nar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@EnableJpaRepositories(basePackages = "com.toy.nar", excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.toy\\.nar\\.domain\\.search\\.repository\\..*"))
@EnableElasticsearchRepositories(basePackages = "com.toy.nar.domain.search.repository")
public class NarApplication {

	public static void main(String[] args) {
		SpringApplication.run(NarApplication.class, args);
	}

}
