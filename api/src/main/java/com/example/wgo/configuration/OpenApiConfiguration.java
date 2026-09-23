package com.example.wgo.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI wgoOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("World Events API")
                        .version("v1")
                        .description("Submit observations and retrieve clustered world events.")
                        .contact(new Contact().name("WGO")));
    }
}
