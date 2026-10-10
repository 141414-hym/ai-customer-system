package com.aiagent.session;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Serializes executions that map to the same session stripe.
 *
 * <p>The fixed stripe count keeps lock memory bounded. Hash collisions can serialize unrelated
 * sessions, but never weaken the isolation of the same session.
 */
@Component
public final class SessionExecutionGate {

    private static final int STRIPE_COUNT = 256;

    private final Semaphore[] stripes = createStripes();

    public <T> Flux<T> serialize(SessionKey sessionKey, Supplier<Flux<T>> execution) {
        Semaphore semaphore = stripeFor(sessionKey);

        Mono<Semaphore> acquire = Mono.fromCallable(() -> {
                    semaphore.acquire();
                    return semaphore;
                })
                .subscribeOn(Schedulers.boundedElastic());

        return Flux.usingWhen(
                acquire,
                ignored -> Flux.defer(execution),
                ignored -> release(semaphore),
                (ignored, error) -> release(semaphore),
                ignored -> release(semaphore)
        );
    }

    private Semaphore stripeFor(SessionKey sessionKey) {
        int index = Math.floorMod(sessionKey.hashCode(), stripes.length);
        return stripes[index];
    }

    private static Semaphore[] createStripes() {
        Semaphore[] result = new Semaphore[STRIPE_COUNT];
        for (int i = 0; i < result.length; i++) {
            result[i] = new Semaphore(1, true);
        }
        return result;
    }

    private static Mono<Void> release(Semaphore semaphore) {
        return Mono.fromRunnable(semaphore::release);
    }
}
