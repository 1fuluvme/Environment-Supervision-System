package com.neps.config;

import com.neps.ai.EnvironmentalQueryTools;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@ConditionalOnProperty(
        prefix = "app.mcp",
        name = "enabled",
        havingValue = "true")
public class McpToolConfig {

    @Bean
    public List<McpStatelessServerFeatures.SyncToolSpecification>
    environmentalMcpTools(
            EnvironmentalQueryTools tools) {

        var provider =
                MethodToolCallbackProvider.builder()
                        .toolObjects(tools)
                        .build();

        return Arrays.stream(
                        provider.getToolCallbacks())
                .map(callback -> {
                    var specification =
                            McpToolUtils
                                    .toStatelessSyncToolSpecification(
                                            callback,
                                            null);

                    var tool =
                            specification.tool();

                    var annotations =
                            new McpSchema.ToolAnnotations(
                                    tool.title() == null
                                            ? tool.name()
                                            : tool.title(),
                                    true,
                                    false,
                                    true,
                                    false,
                                    false);

                    var annotatedTool =
                            new McpSchema.Tool(
                                    tool.name(),
                                    tool.title(),
                                    tool.description(),
                                    tool.inputSchema(),
                                    tool.outputSchema(),
                                    annotations,
                                    tool.meta());

                    return new McpStatelessServerFeatures
                            .SyncToolSpecification(
                            annotatedTool,
                            specification.callHandler());
                })
                .toList();
    }
}
