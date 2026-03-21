package com.historyboard.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebResourceConfig implements WebMvcConfigurer {

    @Value("${historyboard.frontend-dir:../frontend}")
    private String frontendDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String frontendRoot = Path.of(frontendDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/assets/**").addResourceLocations(frontendRoot + "/");
    }
}
