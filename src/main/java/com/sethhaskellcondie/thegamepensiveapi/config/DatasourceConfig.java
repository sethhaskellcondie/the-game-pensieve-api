package com.sethhaskellcondie.thegamepensiveapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

// Configuration tells the system that this object is a source of bean definitions
@Configuration
public class DatasourceConfig {
    // Bean methods are used in a configuration class will instantiate, configure, and initialize new objects, to be managed by Spring
    @Bean
    // Primary is used because you are allowed to create multiple configurations with tech like HikariCP
    @Primary
    // The data source builder expects some properties, the properties in application.properties that start with the prefix "db" will be used for this build method
    @ConfigurationProperties(prefix = "db")
    public HikariDataSource hikariDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    public JdbcTemplate getJdbcTemplate(HikariDataSource hikariDataSource) {
        return new JdbcTemplate(hikariDataSource);
    }
}

