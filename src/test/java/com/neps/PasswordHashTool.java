package com.neps;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class PasswordHashTool {
    public static void main(String[] args) {
        String password = Objects.requireNonNull(System.getenv("ADMIN_PASSWORD"),
                "请在运行配置中设置 ADMIN_PASSWORD");

        if (password.length() < 8
                || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "密码需为8～64个字符，且UTF-8编码不超过72字节");
        }

        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        String hash = encoder.encode(password);

        if(!encoder.matches(password, hash)||encoder.matches("", hash)){
            throw new IllegalArgumentException();
        }

        System.out.println("密码验证检查通过");
        System.out.println("请将下面完整的哈希保存到 password_hash：");
        System.out.println(hash);
    }

}
