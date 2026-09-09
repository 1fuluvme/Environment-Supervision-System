package com.neps.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.entity.User;
import com.neps.mapper.UserMapper;
import com.neps.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class UserServiceImpl
        extends ServiceImpl<UserMapper, User>
        implements UserService {

    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    private User saveAccount(
            String phone, String displayName, String password, String role) {

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "密码UTF-8编码不能超过72字节");
        }

        User user = new User();
        user.setPhone(phone);
        user.setDisplayName(displayName.strip());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled((byte) 1);

        try {
            if (!save(user)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "注册失败，请稍后重试");
            }
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该手机号已注册");
        }

        return user;
    }

    @Override
    public User register(
            String phone, String displayName, String password) {

        return saveAccount(phone, displayName, password, "PUBLIC");
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public User createManagedUser(
            String phone, String displayName, String password, String role) {

        if (!"GRID".equals(role) && !"DECISION".equals(role)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "只能创建网格员或决策者");
        }

        return saveAccount(phone, displayName, password, role);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public User findByPhoneForAdmin(String phone) {
        if (phone == null || !phone.matches("1[3-9][0-9]{9}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "请输入格式正确的11位手机号");
        }

        User user = lambdaQuery()
                .eq(User::getPhone, phone)
                .one();

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "账号不存在");
        }

        return user;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public User changeEnabled(Long id, boolean enabled) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户 ID 必须是正整数");
        }

        User user = getById(id);

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "账号不存在");
        }

        if ("ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "本接口不允许修改管理员账号状态");
        }

        byte value = (byte) (enabled ? 1 : 0);

        if (Byte.valueOf(value).equals(user.getEnabled())) {
            return user;
        }

        boolean updated = lambdaUpdate()
                .eq(User::getId, id)
                .ne(User::getRole, "ADMIN")
                .set(User::getEnabled, value)
                .update();

        if (!updated) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "账号状态已发生变化，请刷新后重试");
        }

        user.setEnabled(value);
        return user;
    }
}
