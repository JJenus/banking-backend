package com.jjenus.banking.sse.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends periodic SSE heartbeat comments to all connected clients.
 *
 * <p>Nginx and most load balancers close idle connections after 60 seconds.
 * A comment (`:heartbeat\n\n`) every 30 seconds keeps the connection alive
 * without triggering an actual browser event.
 */
@Component
public class SseHeartbeatScheduler {

    private final SseEmitterRegistry registry;

    public SseHeartbeatScheduler(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        registry.sendHeartbeat();
    }
}
