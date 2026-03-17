package com.bot.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class AccountingBotApplication {

    public static void main(String[] args) {
        // System.out.println("Starting AccountingBotApplication..."+ new java.util.Date());
        SpringApplication.run(AccountingBotApplication.class, args);
    }
}
