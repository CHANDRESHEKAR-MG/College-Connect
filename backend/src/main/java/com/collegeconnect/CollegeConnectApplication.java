package com.collegeconnect;

import com.collegeconnect.notification.WhatsAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(WhatsAppProperties.class)
public class CollegeConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollegeConnectApplication.class, args);
    }
}
