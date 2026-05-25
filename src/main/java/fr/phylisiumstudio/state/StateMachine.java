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
    @Getter
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
    public void addState(State<T> state) {
        states.put(state.getId(), state);
    }

    /**
     * Add a transition between two states.
     *
     * @param from           the name of the state from which the transition starts
     * @param transitionName the name of the transition
     * @param to             the name of the state to which the transition goes
     */
    public void addTransition(String from, String transitionName, String to) {
        transitions.computeIfAbsent(transitionName, k -> new HashMap<>()).put(from, to);
    }

    /**
     * Set the initial state of the state machine.
     *
     * @param name the name of the initial state
     */
    public void setInitialState(String name) {
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
    public void handleTransition(@NotNull String transitionName) {
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
    public void update(float deltaTime) {
        if (currentState != null) {
            currentState.update(owner, deltaTime);
        }
    }

    /**
     * @deprecated Use {@link #addState(State)} instead
     */
    @Deprecated(forRemoval = false)
    public void AddState(State<T> state) {
        addState(state);
    }

    /**
     * @deprecated Use {@link #addTransition(String, String, String)} instead
     */
    @Deprecated(forRemoval = false)
    public void AddTransition(String from, String transitionName, String to) {
        addTransition(from, transitionName, to);
    }

    /**
     * @deprecated Use {@link #setInitialState(String)} instead
     */
    @Deprecated(forRemoval = false)
    public void SetInitialState(String name) {
        setInitialState(name);
    }

    /**
     * @deprecated Use {@link #handleTransition(String)} instead
     */
    @Deprecated(forRemoval = false)
    public void HandleTransition(String transitionName) {
        handleTransition(transitionName);
    }

    /**
     * @deprecated Use {@link #update(float)} instead
     */
    @Deprecated(forRemoval = false)
    public void Update(float deltaTime) {
        update(deltaTime);
    }
}
