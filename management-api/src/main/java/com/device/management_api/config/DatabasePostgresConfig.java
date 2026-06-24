package com.device.management_api.config;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabasePostgresConfig {
    @Bean
    public ApplicationRunner checkPostgresConnection(DataSource dataSource) {
        return arguments -> {
            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement("SELECT 1")) {
                statement.execute();
                System.out.println("PostgreSQL connection established");
            }
        };
    }
}
