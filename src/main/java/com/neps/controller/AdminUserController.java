package com.neps.controller;

import com.neps.dto.CreateUserRequest;
import com.neps.dto.UserResponse;
import com.neps.entity.User;
import com.neps.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.neps.dto.AdminUserResponse;
import com.neps.dto.UpdateUserEnabledRequest;

@Tag(name = "管理员账号管理")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "创建网格员或决策者")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(
            @Valid @RequestBody CreateUserRequest request) {

        User user = userService.createManagedUser(
                request.phone(),
                request.displayName(),
                request.password(),
                request.role());

        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.getDisplayName(),
                user.getRole());
    }

    @Operation(summary = "按手机号查询账号")
    @GetMapping
    public AdminUserResponse findByPhone(
            @RequestParam("phone") String phone) {

        return toAdminResponse(userService.findByPhoneForAdmin(phone));
    }

    @Operation(summary = "启用或停用账号")
    @PostMapping("/{id}/enabled")
    public AdminUserResponse changeEnabled(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserEnabledRequest request) {

        return toAdminResponse(
                userService.changeEnabled(id, request.enabled()));
    }

    private AdminUserResponse toAdminResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getPhone(),
                user.getDisplayName(),
                user.getRole(),
                Byte.valueOf((byte) 1).equals(user.getEnabled()));
    }
}
