/**
 * SSE (Server-Sent Events) module — real-time push to the Nuxt 4 frontend.
 *
 * <p>Provides a persistent HTTP streaming endpoint ({@code GET /v1/events/stream})
 * that the frontend connects to once and receives typed events as they occur.
 * No polling required.
 *
 * <p>All events originate from the domain event bus. {@link com.jjenus.banking.sse.application.SseEventDispatcher}
 * subscribes to domain events via {@code @ApplicationModuleListener} and routes
 * them to the correct user's active connections via {@link com.jjenus.banking.sse.application.SseEmitterRegistry}.
 *
 * <p>No new Maven dependencies — {@code SseEmitter} is built into Spring MVC.
 *
 * <p>Heartbeat comments are sent every 30 seconds by
 * {@link com.jjenus.banking.sse.application.SseHeartbeatScheduler} to keep
 * connections alive through Nginx and load balancers.
 *
 * <p>The module has no domain logic and no writes. It is a pure read/push
 * fan-out layer on top of the existing event bus.
 */
package com.jjenus.banking.sse;
