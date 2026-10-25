package com.example.demo.entity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
public class Main {

    public static void main(String... args) {
        SpringApplication.run(Main.class, args);
    }

    @RestController
    @RequestMapping("/api")
    public static class CounterController {

        @GetMapping("/value")
        public long getServerTime() {
            // Отдаем точное время сервера в миллисекундах
            return System.currentTimeMillis();
        }
    }
}
