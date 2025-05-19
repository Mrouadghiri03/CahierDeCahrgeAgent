package com.example.agenttest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration // This tells Spring Boot this is a configuration class
public class WebConfig implements WebMvcConfigurer { // This interface provides methods to customize MVC config

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // This method is where you define your CORS rules

        registry.addMapping("/api/v1/diagrams/**") // Apply CORS rules to all paths starting with /api/v1/diagrams/
                .allowedOrigins("http://localhost:5174") // ONLY allow requests from your Vite frontend URL
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Allow these HTTP methods
                .allowedHeaders("*") // Allow all types of headers in the request
                .allowCredentials(true) // If your frontend sends cookies or auth headers, this is needed
                .maxAge(3600); // How long the browser can cache the pre-flight response (in seconds)

        // For maximum debug flexibility (but NOT for production):
        // registry.addMapping("/**") // Apply to ALL endpoints in your application
        //         .allowedOrigins("*") // Allow requests from ANY origin
        //         .allowedMethods("*") // Allow all methods
        //         .allowedHeaders("*"); // Allow all headers
    }
}