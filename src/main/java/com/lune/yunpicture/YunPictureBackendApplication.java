package com.lune.yunpicture;

import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {ShardingSphereAutoConfiguration.class}) // 关闭分表配置,非大项目尽量不分表
@EnableAsync // 开启异步任务
@MapperScan("com.lune.yunpicture.infrastructure.mapper")
@EnableAspectJAutoProxy(exposeProxy = true) // 获取AOP代理的当前类对象
public class YunPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunPictureBackendApplication.class, args);
    }

}
