package com.neps.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neps.entity.User;
import com.neps.service.UserService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            UserService userService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    // 第一部分：告诉 Spring Security 如何根据手机号读取账号
    @Bean
    public UserDetailsService userDetailsService() {
        return phone -> {
            User user = userService.lambdaQuery()
                    .eq(User::getPhone, phone)
                    .one();

            if (user == null) {
                throw new UsernameNotFoundException("账号不存在");
            }

            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getPhone())
                    .password(user.getPasswordHash())
                    .roles(user.getRole())
                    .disabled(!Byte.valueOf((byte) 1).equals(user.getEnabled()))
                    .build();
        };
    }

    // 第二部分：配置请求访问、登录和退出规则
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .csrf(Customizer.withDefaults())
                .requestCache(cache -> cache.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/health",
                                "/api/grids",
                                "/api/grids/*",
                                "/api/auth/csrf",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST, "/api/auth/register")
                        .permitAll()

                        .anyRequest().access((authentication, context) -> {
                            var current = authentication.get();

                            if (current == null
                                    || !current.isAuthenticated()
                                    || current instanceof AnonymousAuthenticationToken) {
                                return new AuthorizationDecision(false);
                            }

                            // ponytail: 每次受保护请求查库，出现性能瓶颈后再考虑可失效缓存。
                            User user = userService.lambdaQuery()
                                    .eq(User::getPhone, current.getName())
                                    .one();

                            boolean allowed = user != null
                                    && Byte.valueOf((byte) 1).equals(user.getEnabled())
                                    && current.getAuthorities().contains(
                                    new SimpleGrantedAuthority(
                                            "ROLE_" + user.getRole()));

                            return new AuthorizationDecision(allowed);
                        }))

                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("phone")
                        .passwordParameter("password")

                        .authenticationDetailsSource(request -> {
                            String phone = request.getParameter("phone");
                            String password = request.getParameter("password");

                            if (phone == null
                                    || !phone.matches("1[3-9][0-9]{9}")
                                    || password == null
                                    || password.isBlank()
                                    || password.length() < 8
                                    || password.length() > 64
                                    || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                                throw new BadCredentialsException("登录参数不正确");
                            }

                            return new WebAuthenticationDetails(request);
                        })

                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))

                        .failureHandler((request, response, exception) ->
                                writeProblem(request, response,
                                        HttpStatus.UNAUTHORIZED,
                                        "手机号或密码错误，或账号已停用"))
                        .permitAll())

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .permitAll())

                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(request, response,
                                        HttpStatus.UNAUTHORIZED,
                                        "请先登录"))

                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(request, response,
                                        HttpStatus.FORBIDDEN,
                                        "请求被拒绝，请检查账号状态、权限和CSRF令牌")));

        return http.build();
    }

    // 第三部分：将安全检查错误输出为 JSON
    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String detail) throws IOException {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
