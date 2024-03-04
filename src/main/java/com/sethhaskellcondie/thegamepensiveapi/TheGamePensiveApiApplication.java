package com.sethhaskellcondie.thegamepensiveapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// @SpringBootApplication is the same as @Configuration, @EnableAutoConfiguration, and @ComponentScan
// There should only be one @SpringBootApplication annotation used in a project
@SpringBootApplication
public class TheGamePensiveApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TheGamePensiveApiApplication.class, args);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins("http://localhost:4200");
            }
        };
    }
}
