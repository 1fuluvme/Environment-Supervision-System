package com.neps.controller;

import com.neps.dto.RegisterRequest;
import com.neps.dto.UserResponse;
import com.neps.entity.User;
import com.neps.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "账号认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "公众注册")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request) {

        User user = userService.register(
                request.phone(),
                request.displayName(),
                request.password());

        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.getDisplayName(),
                user.getRole());
    }

    @Operation(summary = "获取CSRF令牌")
    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @Operation(summary = "查询当前登录用户")
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        User user = userService.lambdaQuery()
                .eq(User::getPhone, authentication.getName())
                .eq(User::getEnabled, 1)
                .one();

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "账号不存在或已停用");
        }

        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.getDisplayName(),
                user.getRole());
    }
}