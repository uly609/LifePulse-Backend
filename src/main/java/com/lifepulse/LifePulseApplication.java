package com.lifepulse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.lifepulse.mapper")
@SpringBootApplication
public class LifePulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifePulseApplication.class, args);
    }
}
