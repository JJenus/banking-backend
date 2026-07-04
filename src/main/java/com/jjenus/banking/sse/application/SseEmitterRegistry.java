package com.jjenus.banking.sse.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry of active SSE connections, keyed by Keycloak owner ID.
 *
 * <p>Supports multiple concurrent connections per user (multiple browser tabs,
 * multiple devices). Each connection is represented by one {@link SseEmitter}.
 *
 * <p>Connections are automatically cleaned up on timeout, error, or completion
 * via callbacks registered at emitter creation time in {@link SseController}.
 *
 * <p>All send operations are fire-and-forget at the individual emitter level —
 * a failed send removes the dead emitter and continues to the next. The calling
 * listener never throws because of a disconnected client.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitterRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Registration ──────────────────────────────────────────────────────

    /**
     * Registers a new emitter for a user. Multiple emitters per user are allowed.
     */
    public void register(String ownerId, SseEmitter emitter) {
        emitters.computeIfAbsent(ownerId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        log.debug("SSE: registered emitter for user {} (total: {})",
            ownerId, emitters.get(ownerId).size());
    }

    /**
     * Removes a specific emitter for a user — called on timeout, error, or complete.
     */
    public void remove(String ownerId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(ownerId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(ownerId);
            }
        }
        log.debug("SSE: removed emitter for user {}", ownerId);
    }

    /** Returns the number of currently connected users. */
    public int connectedUserCount() {
        return emitters.size();
    }

    /** Returns the total number of active emitters across all users. */
    public int totalEmitterCount() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    // ── Send ──────────────────────────────────────────────────────────────

    /**
     * Sends a typed SSE event to all active connections for a user.
     *
     * <p>Dead connections are silently removed. If the user has no connections,
     * this is a no-op.
     *
     * @param ownerId   Keycloak owner ID
     * @param eventType the SSE event name (e.g. {@code TRANSFER_COMPLETED})
     * @param payload   any object — serialised to JSON
     */
    public void sendToUser(String ownerId, String eventType, Object payload) {
        List<SseEmitter> userEmitters = emitters.get(ownerId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(
                new SsePayload(eventType, payload, Instant.now().toString()));
        } catch (JsonProcessingException e) {
            log.error("SSE: failed to serialise payload for event {} to user {}: {}",
                eventType, ownerId, e.getMessage());
            return;
        }

        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(json)
                    .id(Instant.now().toEpochMilli() + "-" + ownerId));
            } catch (IOException | IllegalStateException e) {
                // Emitter is closed or timed out — mark for removal
                dead.add(emitter);
                log.debug("SSE: removed dead emitter for user {} ({})",
                    ownerId, e.getClass().getSimpleName());
            }
        }
        userEmitters.removeAll(dead);
    }

    /**
     * Sends a heartbeat comment (`:`) to all connected users to keep connections alive
     * through proxies and load balancers. Called on a schedule by {@link SseHeartbeatScheduler}.
     */
    public void sendHeartbeat() {
        emitters.forEach((ownerId, userEmitters) -> {
            List<SseEmitter> dead = new java.util.ArrayList<>();
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException e) {
                    dead.add(emitter);
                }
            }
            userEmitters.removeAll(dead);
        });
    }

    // ── Internal payload wrapper ──────────────────────────────────────────

    public record SsePayload(String type, Object data, String timestamp) {}
}
