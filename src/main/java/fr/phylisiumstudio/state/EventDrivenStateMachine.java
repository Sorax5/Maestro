package fr.phylisiumstudio.state;

import fr.phylisiumstudio.event.EventBus;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EventDrivenStateMachine connects an EventBus to a StateMachine, allowing events to trigger transitions.
 * Events from the EventBus can now automatically trigger state machine transitions.
 *
 * <p>Example:
 * <pre>
 * StateMachine<Player> stateMachine = new StateMachine<>(player);
 * EventBus eventBus = new EventBus();
 *
 * EventDrivenStateMachine<Player> bridge = new EventDrivenStateMachine<>(stateMachine, eventBus);
 * bridge.bindTransition("onDamage", "player_damaged");
 *
 * // Now when eventBus.execute("onDamage", args) is called,
 * // it automatically triggers stateMachine.handleTransition("player_damaged")
 * </pre>
 *
 * @param <T> The type of the owner of the state machine
 */
public class EventDrivenStateMachine<T> {
    @Getter
    private final StateMachine<T> stateMachine;
    @Getter
    private final EventBus eventBus;

    private final Map<String, String> eventToTransitionMappings = new ConcurrentHashMap<>();

    /**
     * Create a new EventDrivenStateMachine.
     *
     * @param stateMachine the StateMachine to bind to
     * @param eventBus the EventBus to listen to
     */
    public EventDrivenStateMachine(@NotNull StateMachine<T> stateMachine, @NotNull EventBus eventBus) {
        this.stateMachine = stateMachine;
        this.eventBus = eventBus;

        eventBus.addMiddleware((eventName, args, next) -> {
            next.run();

            var transitionName = eventToTransitionMappings.get(eventName);
            if (transitionName != null) {
                stateMachine.handleTransition(transitionName);
            }
        });
    }

    /**
     * Bind an event to a state machine transition.
     * When the event is executed, the transition will be automatically triggered.
     *
     * @param eventName the name of the EventBus event
     * @param transitionName the name of the StateMachine transition
     */
    public void bindTransition(@NotNull String eventName, @NotNull String transitionName) {
        eventToTransitionMappings.put(eventName, transitionName);
    }

    /**
     * Unbind an event from a transition.
     *
     * @param eventName the name of the EventBus event
     */
    public void unbindTransition(@NotNull String eventName) {
        eventToTransitionMappings.remove(eventName);
    }

    /**
     * Clear all bindings.
     */
    public void clearBindings() {
        eventToTransitionMappings.clear();
    }
}


