package com.device.management_api;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@ConfigurationPropertiesScan
@SpringBootApplication
public class ManagementApiApplication {

	private static final String API_PREFIX = "/api/v1";

	public static void main(String[] args) {
		SpringApplication.run(ManagementApiApplication.class, args);
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
			addCleanPath(openApi, paths, API_PREFIX + "/devices");
			addCleanPath(openApi, paths, API_PREFIX + "/devices/{device_id}");
			addCleanPath(openApi, paths, API_PREFIX + "/devices/{device_id}/telemetry");
			addCleanPath(openApi, paths, API_PREFIX + "/devices/{device_id}/telemetry/latest");
			openApi.getPaths().forEach((path, pathItem) -> {
				String cleanPath = path.startsWith(API_PREFIX)
						? path.substring(API_PREFIX.length())
						: path;
				if (!paths.containsKey(cleanPath)) {
					paths.addPathItem(cleanPath, pathItem);
				}
			});
			openApi.setPaths(paths);
		};
	}

	private void addCleanPath(OpenAPI openApi, Paths paths, String path) {
		if (openApi.getPaths().containsKey(path)) {
			paths.addPathItem(path.substring(API_PREFIX.length()), openApi.getPaths().get(path));
		}
	}

}
