package com.jjenus.banking.sse.api;

import com.jjenus.banking.sse.application.SseEmitterRegistry;
import com.jjenus.banking.shared.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE stream endpoint.
 *
 * <p>Base path: {@code /api/v1/events/stream}
 *
 * <p>The Nuxt frontend connects using the browser's {@code EventSource} API:
 * <pre>
 * const source = new EventSource('/api/v1/events/stream', {
 *   headers: { Authorization: 'Bearer ' + token }
 * });
 * source.addEventListener('TRANSFER_COMPLETED', (e) => { ... });
 * source.addEventListener('BALANCE_UPDATED',    (e) => { ... });
 * </pre>
 *
 * <p>Note: the browser's native {@code EventSource} does not support custom
 * headers. Use the {@code eventsource} npm package or pass the token as a
 * query parameter ({@code ?token=...}) and handle it in a custom
 * {@code HandshakeInterceptor} if needed. The recommended approach for Nuxt 4
 * is using the {@code @microsoft/fetch-event-source} library which supports
 * headers properly.
 *
 * <p>Timeout: 5 minutes. Clients reconnect automatically via EventSource's
 * built-in retry mechanism.
 *
 * <p>Heartbeat: a comment is sent every 30 seconds by
 * {@link com.jjenus.banking.sse.application.SseHeartbeatScheduler} to keep
 * the connection alive through Nginx and load balancers.
 */
@RestController
@RequestMapping("/v1/events")
@Tag(name = "Real-time Events", description = "Server-Sent Events stream for live updates")
@SecurityRequirement(name = "bearer-key")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    /**
     * 5-minute timeout. EventSource clients reconnect automatically after
     * the connection closes, so a reasonable timeout keeps server resources
     * free without breaking the real-time experience.
     */
    private static final long TIMEOUT_MS = 5 * 60 * 1_000L;

    private final SseEmitterRegistry registry;

    public SseController(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Open an SSE stream to receive real-time account and transfer events",
        description = """
            Opens a persistent HTTP connection. The server pushes typed events as they occur.

            Event types:
            - TRANSACTION_CREATED   — new deposit, withdrawal, or transfer credit
            - BALANCE_UPDATED       — balance changed (fetch updated balance after receiving)
            - TRANSFER_COMPLETED    — a transfer you sent or received has settled
            - TRANSFER_REVERSED     — a transfer has been reversed
            - ACCOUNT_STATUS_CHANGED — your account was frozen, activated, or suspended
            - KYC_STATUS_CHANGED    — KYC approved, rejected, or submitted
            - FEE_CHARGED           — a fee was deducted from your account

            Each event's `data` field contains a JSON object. The structure varies by event type.
            Use `source.addEventListener('EVENT_TYPE', handler)` to subscribe.
            """
    )
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream() {
        String ownerId = CurrentUser.id();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        registry.register(ownerId, emitter);

        // Clean up on disconnect, timeout, or completion
        emitter.onTimeout(() -> {
            registry.remove(ownerId, emitter);
            log.debug("SSE: emitter timed out for user {}", ownerId);
        });
        emitter.onError(e -> {
            registry.remove(ownerId, emitter);
            log.debug("SSE: emitter error for user {}: {}", ownerId, e.getMessage());
        });
        emitter.onCompletion(() -> {
            registry.remove(ownerId, emitter);
            log.debug("SSE: emitter completed for user {}", ownerId);
        });

        // Send a CONNECTED event immediately so the client knows the stream is live
        try {
            emitter.send(SseEmitter.event()
                .name("CONNECTED")
                .data("{\"status\":\"connected\",\"userId\":\"" + ownerId + "\"}"));
        } catch (Exception e) {
            log.warn("SSE: failed to send CONNECTED event to user {}: {}", ownerId, e.getMessage());
            registry.remove(ownerId, emitter);
        }

        log.info("SSE: new connection for user {} (total connections: {})",
            ownerId, registry.totalEmitterCount());

        return emitter;
    }

    @GetMapping("/stats")
    @Operation(summary = "SSE connection stats — connected users and total emitters (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public SseStats stats() {
        return new SseStats(registry.connectedUserCount(), registry.totalEmitterCount());
    }

    public record SseStats(int connectedUsers, int totalEmitters) {}
}
