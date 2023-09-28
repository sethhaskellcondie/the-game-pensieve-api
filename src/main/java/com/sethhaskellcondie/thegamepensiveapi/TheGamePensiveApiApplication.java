package com.sethhaskellcondie.thegamepensiveapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication is the same as @Configuration, @EnableAutoConfiguration, and @ComponentScan
// There should only be one @SpringBootApplication annotation used in a project
@SpringBootApplication
public class TheGamePensiveApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TheGamePensiveApiApplication.class, args);
	}

}
