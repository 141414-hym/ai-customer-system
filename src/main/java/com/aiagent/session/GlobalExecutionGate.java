package com.aiagent.session;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class GlobalExecutionGate {
    private final Semaphore permits = new Semaphore(64, true);

    public <T> Flux<T> execute(Supplier<Flux<T>> task) {
        Mono<Boolean> acquire = Mono.fromCallable(() -> permits.tryAcquire(300, java.util.concurrent.TimeUnit.MILLISECONDS))
                .subscribeOn(Schedulers.boundedElastic());
        return Flux.usingWhen(acquire,
                ok -> Boolean.TRUE.equals(ok) ? Flux.defer(task) : Flux.error(new IllegalStateException("系统繁忙，请稍后重试")),
                ok -> release(ok), (ok, error) -> release(ok), this::release);
    }

    private Mono<Void> release(Boolean acquired) {
        return Mono.fromRunnable(() -> { if (Boolean.TRUE.equals(acquired)) permits.release(); });
    }
}
