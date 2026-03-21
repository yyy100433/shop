package com.chinahitech.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.*;

/*异步线程池配置（用于Redis延迟双删）*/
@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolExecutor asyncDeleteCacheExecutor() {
        // 核心线程数：CPU核心数
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        // 最大线程数
        int maxPoolSize = corePoolSize * 2;
        // 空闲线程存活时间
        long keepAliveTime = 60L;
        // 任务队列
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(100);
        // 线程命名前缀
        ThreadFactory threadFactory = new ThreadFactory() {
            private int count = 1;
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("async-delete-cache-thread-" + count++);
                return thread;
            }
        };
        // 拒绝策略：直接抛出异常
        RejectedExecutionHandler handler = new ThreadPoolExecutor.AbortPolicy();

        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                handler
        );
    }
}