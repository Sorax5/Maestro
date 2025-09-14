package fr.phylisiumstudio.state;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A generic state machine implementation.
 *
 * @param <T> The type of the owner of the state machine.
 */
public class StateMachine<T> {
    private final T owner;
    private final Map<String, State<T>> states = new HashMap<>();
    private final Map<String, Map<String, String>> transitions = new HashMap<>();

    @Getter
    private State<T> currentState;

    /**
     * Constructor for the StateMachine.
     *
     * @param owner The owner of the state machine.
     */
    public StateMachine(T owner) {
        this.owner = owner;
    }

    /**
     * Add a state to the state machine.
     *
     * @param state The state instance.
     */
    public void AddState(State<T> state) {
        states.put(state.getId(), state);
    }

    /**
     * Add a transition between two states.
     *
     * @param from the name of the state from which the transition starts
     * @param transitionName the name of the transition
     * @param to the name of the state to which the transition goes
     */
    public void AddTransition(String from, String transitionName, String to) {
        transitions.computeIfAbsent(from, k -> new HashMap<>()).put(transitionName, to);
    }

    /**
     * Set the initial state of the state machine.
     *
     * @param name the name of the initial state
     */
    public void SetInitialState(String name) {
        currentState = states.get(name);
        if (currentState != null) {
            currentState.onEnter(owner);
        }
    }

    /**
     * Handle a transition by its name.
     *
     * @param transitionName the name of the transition to handle
     */
    public void HandleTransition(@NotNull String transitionName) {
        if (currentState == null) {
            return;
        }

        Map<String, String> transition = transitions.get(transitionName);
        if (transition == null) {
            return;
        }

        String nextStateName = transition.get(currentState.getId());
        State<T> nextState = states.get(nextStateName);

        if (nextState == null) {
            return;
        }

        currentState.onExit(owner);
        currentState = nextState;
        currentState.onEnter(owner);
    }

    /**
     * Update the current state.
     *
     * @param deltaTime the time elapsed since the last update
     */
    public void Update(float deltaTime) {
        if (currentState != null) {
            currentState.update(owner, deltaTime);
        }
    }
}
