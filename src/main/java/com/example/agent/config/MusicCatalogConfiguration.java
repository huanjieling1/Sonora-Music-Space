package com.example.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class MusicCatalogConfiguration {
    @Bean(name = "musicProviderExecutor", destroyMethod = "shutdown")
    ExecutorService musicProviderExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(8, task -> {
            Thread thread = new Thread(task, "music-provider-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
