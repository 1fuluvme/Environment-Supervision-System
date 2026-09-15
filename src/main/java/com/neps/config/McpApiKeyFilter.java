package com.neps.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class McpApiKeyFilter
        extends OncePerRequestFilter {

    public static final String API_KEY_HEADER =
            "X-MCP-API-Key";

    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String expectedApiKey;
    private final String actorPhone;

    public McpApiKeyFilter(
            UserDetailsService userDetailsService,
            ObjectMapper objectMapper,
            @Value("${app.mcp.enabled:false}")
            boolean enabled,
            @Value("${app.mcp.api-key:}")
            String expectedApiKey,
            @Value("${app.mcp.actor-phone:}")
            String actorPhone) {

        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.expectedApiKey =
                expectedApiKey == null
                        ? ""
                        : expectedApiKey.trim();
        this.actorPhone =
                actorPhone == null
                        ? ""
                        : actorPhone.trim();
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request) {

        String path = request.getRequestURI();

        String contextPath =
                request.getContextPath();

        if (!contextPath.isEmpty()
                && path.startsWith(contextPath)) {

            path = path.substring(
                    contextPath.length());
        }

        return !"/mcp".equals(path)
                && !path.startsWith("/mcp/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            writeProblem(
                    request,
                    response,
                    HttpStatus.NOT_FOUND,
                    "MCP服务未启用");
            return;
        }

        if (expectedApiKey.length() < 32
                || actorPhone.isBlank()) {

            writeProblem(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MCP服务配置不完整");
            return;
        }

        String suppliedApiKey =
                request.getHeader(API_KEY_HEADER);

        if (!matches(suppliedApiKey)) {
            writeProblem(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "MCP访问密钥不正确");
            return;
        }

        UserDetails actor;

        try {
            actor =
                    userDetailsService
                            .loadUserByUsername(
                                    actorPhone);
        } catch (UsernameNotFoundException exception) {
            writeProblem(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "MCP服务账号不存在");
            return;
        }

        boolean allowedRole =
                actor.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                "ROLE_ADMIN".equals(
                                        authority.getAuthority())
                                        || "ROLE_DECISION".equals(
                                        authority.getAuthority()));

        if (!actor.isEnabled()
                || !allowedRole) {

            writeProblem(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "MCP服务账号无权使用环保分析工具");
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken
                        .authenticated(
                                actor,
                                null,
                                actor.getAuthorities());

        authentication.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request));

        SecurityContext securityContext =
                SecurityContextHolder
                        .createEmptyContext();

        securityContext.setAuthentication(
                authentication);

        SecurityContextHolder.setContext(
                securityContext);

        try {
            filterChain.doFilter(
                    request,
                    response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean matches(
            String suppliedApiKey) {

        if (suppliedApiKey == null
                || suppliedApiKey.isBlank()) {
            return false;
        }

        byte[] expected =
                expectedApiKey.getBytes(
                        StandardCharsets.UTF_8);

        byte[] supplied =
                suppliedApiKey.trim()
                        .getBytes(
                                StandardCharsets.UTF_8);

        return MessageDigest.isEqual(
                expected,
                supplied);
    }

    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String detail)
            throws IOException {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        status,
                        detail);

        problem.setInstance(
                URI.create(
                        request.getRequestURI()));

        response.setStatus(
                status.value());

        response.setContentType(
                "application/problem+json");

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name());

        objectMapper.writeValue(
                response.getOutputStream(),
                problem);
    }
}
