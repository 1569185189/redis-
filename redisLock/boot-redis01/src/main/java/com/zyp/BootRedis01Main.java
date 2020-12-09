package com.zyp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * create by
 *
 * @author zouyuanpeng
 * @date 2020/12/6 21:16
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class BootRedis01Main {
    public static void main(String[] args) {
        SpringApplication.run(BootRedis01Main.class,args);
    }
}
