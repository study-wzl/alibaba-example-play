package com.example.springalibabaexample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        BatchAutoConfiguration.class
})
@ComponentScan(basePackages = {"com.example"})
public class SpringAlibabaExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAlibabaExampleApplication.class, args);
    }

}
