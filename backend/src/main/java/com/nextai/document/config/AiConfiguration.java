package com.nextai.document.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfiguration {

    @Bean
    ChatClient documentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
