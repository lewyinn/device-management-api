package com.device.management_api;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class Application {
    private static final String API_PREFIX = "/api/v1";

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Device Management API")
                        .version("1.0.0")
                        .description("API untuk mengelola perangkat IoT."))
                .servers(List.of(new Server().url("http://localhost:8080/api/v1")));
    }

    @Bean
    public OpenApiCustomizer removeApiPrefixFromSwaggerPaths() {
        return openApi -> {
            Paths paths = new Paths();
            openApi.getPaths().forEach((path, pathItem) -> {
                String cleanPath = path.startsWith(API_PREFIX)
                        ? path.substring(API_PREFIX.length())
                        : path;
                paths.addPathItem(cleanPath, pathItem);
            });
            openApi.setPaths(paths);
        };
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(
                                "http://localhost:5173"
                        )
                        .allowedMethods(
                                "GET",
                                "POST",
                                "PUT",
                                "PATCH",
                                "DELETE",
                                "OPTIONS"
                        )
                        .allowedHeaders("*");
            }
        };
    }
}
