package com.kade.AIAssistant.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * SSE 스트리밍 전송(send) 오프로딩을 위한 전용 실행 환경.
 *
 * <p>SseEmitter.send()는 블로킹 I/O가 될 수 있으므로, Reactor/Netty 이벤트루프 등
 * 핵심 스레드에서 실행되지 않도록 별도 Scheduler로 전환합니다.</p>
 */
@Configuration
public class SseStreamingConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService sseStreamingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "dispose")
    public Scheduler sseStreamingScheduler(ExecutorService sseStreamingExecutor) {
        return Schedulers.fromExecutorService(sseStreamingExecutor);
    }
}
