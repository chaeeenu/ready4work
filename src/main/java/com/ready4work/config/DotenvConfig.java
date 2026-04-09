package com.ready4work.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.stream.Collectors;

public class DotenvConfig implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        Map<String, Object> dotenvMap = dotenv.entries().stream()
                .collect(Collectors.toMap(
                        e -> "env." + e.getKey(),
                        e -> e.getValue()
                ));

        environment.getPropertySources()
                .addLast(new MapPropertySource("dotenv", dotenvMap));
    }
}
