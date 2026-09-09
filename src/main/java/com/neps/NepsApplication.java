package com.neps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan("com.neps.mapper")
@SpringBootApplication
public class NepsApplication {
    public static void main(String[] args) {

        SpringApplication.run(NepsApplication.class, args);
    }
}
