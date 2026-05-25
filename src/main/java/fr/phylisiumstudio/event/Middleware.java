package fr.phylisiumstudio.event;

import org.jetbrains.annotations.NotNull;

/**
 * Middleware interface for EventBus interceptor chain.
 * Allows pre/post processing of events before they reach handlers.
 *
 * <p>Example:
 * <pre>
 * eventBus.addMiddleware((event, args, next) -> {
 *     System.out.println("Before: " + event);
 *     next.run();
 *     System.out.println("After: " + event);
 * });
 * </pre>
 */
@FunctionalInterface
public interface Middleware {
    /**
     * Process an event through the middleware.
     *
     * @param eventName the name of the event being processed
     * @param args the arguments passed to the event
     * @param next callback to invoke the next middleware in the chain
     */
    void process(@NotNull String eventName, @NotNull EventBus.Arguments args, @NotNull Runnable next);
}

