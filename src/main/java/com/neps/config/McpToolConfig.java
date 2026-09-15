package com.neps.config;

import com.neps.ai.EnvironmentalQueryTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "app.mcp",
        name = "enabled",
        havingValue = "true")
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider environmentalMcpTools(
            EnvironmentalQueryTools tools) {

        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
