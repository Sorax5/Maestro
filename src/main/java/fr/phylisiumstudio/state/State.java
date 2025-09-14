package fr.phylisiumstudio.state;

/**
 * A generic state interface.
 *
 * @param <T> The type of the owner of the state.
 */
public interface State<T> {
    /**
     * Get the unique identifier of the state.
     *
     * @return a string representing the unique identifier of the state.
     */
    String getId();

    /**
     * Called when entering the state.
     *
     * @param owner the owner of the state machine.
     */
    void onEnter(T owner);

    /**
     * Called when exiting the state.
     *
     * @param owner the owner of the state machine.
     */
    void onExit(T owner);

    /**
     * Called to update the state.
     *
     * @param owner the owner of the state machine.
     * @param deltaTime the time elapsed since the last update.
     */
    void update(T owner, float deltaTime);
}
