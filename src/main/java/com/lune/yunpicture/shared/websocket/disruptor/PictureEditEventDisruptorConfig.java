package com.lune.yunpicture.shared.websocket.disruptor;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * 图片编辑事件 disruptor 配置类 （将消费者注册进来并定义生产者写入事件进入队列）
 */
@Configuration
public class PictureEditEventDisruptorConfig {

    @Resource
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;

    @Bean("pictureEditEventDisruptor")
    public Disruptor<PictureEditEvent> messageModelRingBuffer() {
        // 定义 ringBuffer 的大小
        int bufferSize = 1024 * 256;
        // 创建 Disruptor
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
                PictureEditEvent::new,
                bufferSize,
                // 线程工厂,创建线程
                ThreadFactoryBuilder.create().setNamePrefix("pictureEditEventDisruptor").build()
        );

        // 设置消费者
        disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);
        // 启动disruptor
        disruptor.start();
        return disruptor;
    }
}
