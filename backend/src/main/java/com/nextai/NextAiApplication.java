package com.nextai;

import com.nextai.document.config.MinioProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(MinioProperties.class)
public class NextAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NextAiApplication.class, args);
    }
}
