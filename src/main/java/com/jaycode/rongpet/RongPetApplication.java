package com.jaycode.rongpet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("com.jaycode.rongpet.**.mapper")
public class RongPetApplication {

    public static void main(String[] args) {
        SpringApplication.run(RongPetApplication.class, args);
    }
}
