package com.neps;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public class CodeGenerator {

    public static void main(String[] args) {
        String projectDir = Path.of("").toAbsolutePath().toString();

        String username = Objects.requireNonNull(
                System.getenv("DB_USERNAME"), "请配置 DB_USERNAME");
        String password = Objects.requireNonNull(
                System.getenv("DB_PASSWORD"), "请配置 DB_PASSWORD");

        String url = "jdbc:mysql://localhost:3306/neps"
                + "?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";

        FastAutoGenerator.create(url, username, password)
                .globalConfig(builder -> builder
                        .author("neps")
                        .outputDir(projectDir + "/src/main/java")
                        .disableOpenDir())
                .packageConfig(builder -> builder
                        .parent("com.neps")
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .pathInfo(Map.of(
                                OutputFile.xml,
                                projectDir + "/src/main/resources/mapper")))
                .strategyConfig(builder -> {
                    builder.addInclude("biz_measurement")
                            .addTablePrefix("biz_");

                    builder.serviceBuilder()
                            .formatServiceFileName("%sService");

                    builder.controllerBuilder().disable();
                })
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}
