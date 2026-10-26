package com.enterprise.brain.task;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 统一任务中心服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.enterprise.brain"})
@EnableDiscoveryClient
@MapperScan({"com.enterprise.brain.task.mapper", "com.enterprise.brain.task.wage.mapper", "com.enterprise.brain.task.wms.mapper", "com.enterprise.brain.task.wms.picktask.mapper"})
public class TaskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskServiceApplication.class, args);
    }
}
