package com.neps;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseConnectionCheck {

    public static void main(String[] args) {
        try (var context = new SpringApplicationBuilder(NepsApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String database = jdbc.queryForObject(
                    "SELECT DATABASE()", String.class);

            if (!"neps".equals(database)) {
                throw new IllegalStateException(
                        "连接的数据库不正确：" + database);
            }

            System.out.println("数据库连接成功，当前数据库：" + database);
        }
    }
}