package com.poker;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = {"com.poker.mapper", "com.poker.repository"})
public class PokerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PokerApplication.class, args);
    }
}
