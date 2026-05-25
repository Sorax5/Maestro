package fr.phylisiumstudio.event;

import fr.phylisiumstudio.annotation.ActionHandler;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * EventBus is a simple event bus implementation that allows registering, unregistering, and executing actions
 * based on method annotations. It supports parameterized actions using the Arguments class.
 * Actions can be registered by annotating methods with @ActionHandler and specifying the action name.
 */
public class EventBus {
    private final Map<String, List<Consumer<Arguments>>> namedActions = new ConcurrentHashMap<>();
    private final Map<Object, HandlerRegistration> handlerRegistry = new ConcurrentHashMap<>();
    private final List<Middleware> middlewares = new CopyOnWriteArrayList<>();
    private Consumer<Throwable> errorHandler = Throwable::printStackTrace;
    private EventJournal eventJournal = null;
    @Getter
    private boolean eventSourcingEnabled = false;

    /**
     * Registers actions from the given handler object.
     * The handler's methods must be annotated with @ActionBus and have a single parameter of type Arguments.
     * The method name specified in the annotation will be used as the action name.
     *
     * @throws RuntimeException if an error occurs during method invocation
     *
     * @param handler the object containing methods to register as actions
     */
    public void registerActions(@NotNull Object handler) {
        try {
            var registeredActionsByEvent = new HandlerRegistration();
            Arrays.stream(handler.getClass().getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(ActionHandler.class))
                    .filter(method -> method.getParameterCount() == 1)
                    .filter(method -> method.getParameterTypes()[0].equals(Arguments.class))
                    .forEach(method -> {
                        var annotation = method.getAnnotation(ActionHandler.class);
                        var name = annotation.event();
                        method.setAccessible(true);

                        Consumer<Arguments> action = args -> {
                            try {
                                method.invoke(handler, args);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                errorHandler.accept(new RuntimeException("Error invoking action '" + name + "'", e));
                            }
                        };

                        namedActions.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(action);
                        registeredActionsByEvent.add(name, action);
                    });

            var existing = handlerRegistry.computeIfAbsent(handler, key -> new HandlerRegistration());
            existing.mergeFrom(registeredActionsByEvent);
        }
        catch (Exception e) {
            errorHandler.accept(e);
        }
    }

    /**
     * Unregisters actions from the given handler object.
     * The handler's methods must be annotated with @ActionBus.
     * The method name specified in the annotation will be used to find and remove the actions.
     * This should be called explicitly when a handler is no longer needed.
     *
     * @throws RuntimeException if an error occurs during unregistration
     *
     * @param handler the object containing methods to unregister as actions
     */
    public void unregisterActions(@NotNull Object handler) {
        try {
            var registeredActionsByEvent = handlerRegistry.remove(handler);

            if (registeredActionsByEvent != null) {
                for (var entry : registeredActionsByEvent.entries()) {
                    var eventName = entry.getKey();
                    var actionsToRemove = entry.getValue();
                    var actions = namedActions.get(eventName);
                    if (actions != null) {
                        actions.removeAll(actionsToRemove);
                        if (actions.isEmpty()) {
                            namedActions.remove(eventName);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            errorHandler.accept(e);
        }
    }

    private static final class HandlerRegistration {
        private final Map<String, List<Consumer<Arguments>>> actionsByEvent = new ConcurrentHashMap<>();

        void add(String eventName, Consumer<Arguments> action) {
            actionsByEvent.computeIfAbsent(eventName, key -> new CopyOnWriteArrayList<>()).add(action);
        }

        void mergeFrom(HandlerRegistration other) {
            for (var entry : other.actionsByEvent.entrySet()) {
                actionsByEvent
                        .computeIfAbsent(entry.getKey(), key -> new CopyOnWriteArrayList<>())
                        .addAll(entry.getValue());
            }
        }

        Set<Map.Entry<String, List<Consumer<Arguments>>>> entries() {
            return actionsByEvent.entrySet();
        }
    }

    /**
     * Executes all actions registered under the specified action name.
     * The provided Arguments object will be passed to each action through the middleware chain.
     *
     * @param actionName the name of the action to execute
     * @param args the Arguments object containing parameters for the action
     */
    public void execute(String actionName, @NotNull Arguments args) {
        try {
            if (eventSourcingEnabled && eventJournal != null) {
                eventJournal.recordEvent(actionName, args);
            }

            var actions = namedActions.get(actionName);
            if (actions == null || actions.isEmpty()) {
                return;
            }

            Runnable chainedAction = () -> {
                for (var action : actions) {
                    try {
                        action.accept(args);
                    } catch (Exception e) {
                        errorHandler.accept(e);
                    }
                }
            };

            for (var i = middlewares.size() - 1; i >= 0; i--) {
                final var middleware = middlewares.get(i);
                final var nextAction = chainedAction;
                chainedAction = () -> middleware.process(actionName, args, nextAction);
            }

            chainedAction.run();
        }
        catch (Exception e) {
            errorHandler.accept(e);
        }
    }

    /**
     * Adds a middleware to the EventBus.
     * Middlewares are executed in the order they are added.
     *
     * @param middleware the middleware to add
     */
    public void addMiddleware(@NotNull Middleware middleware) {
        middlewares.add(middleware);
    }

    /**
     * Removes a middleware from the EventBus.
     *
     * @param middleware the middleware to remove
     */
    public void removeMiddleware(@NotNull Middleware middleware) {
        middlewares.remove(middleware);
    }

    /**
     * Clears all middlewares from the EventBus.
     */
    public void clearMiddlewares() {
        middlewares.clear();
    }

    /**
     * Returns the number of middlewares currently registered.
     *
     * @return the count of middlewares
     */
    public int getMiddlewaresCount() {
        return middlewares.size();
    }

    /**
     * Enable event sourcing with the specified journal.
     * All events will be recorded in the journal.
     *
     * @param journal the EventJournal to use for recording
     */
    public void enableEventSourcing(@NotNull EventJournal journal) {
        this.eventJournal = journal;
        this.eventSourcingEnabled = true;
    }

    /**
     * Disable event sourcing.
     */
    public void disableEventSourcing() {
        this.eventSourcingEnabled = false;
    }

    /**
     * Get the EventJournal if event sourcing is enabled.
     *
     * @return the EventJournal, or null if not enabled
     */
    @Nullable
    public EventJournal getEventJournal() {
        return eventSourcingEnabled ? eventJournal : null;
    }

    /**
     * Replay events from the journal between two time points.
     * This reconstructs the state as if those events were executed again.
     *
     * @param eventName the name of events to replay
     * @param from the start time (inclusive)
     * @param to the end time (inclusive)
     */
    public void replay(@NotNull String eventName, @NotNull Instant from, @NotNull Instant to) {
        if (eventJournal == null) {
            throw new IllegalStateException("Event sourcing is not enabled");
        }

        var events = eventJournal.getEvents(eventName, from, to);
        for (var record : events) {
            var actions = namedActions.get(record.eventName);
            if (actions != null) {
                var replayArgs = record.toArguments();
                for (var action : actions) {
                    try {
                        action.accept(replayArgs);
                    } catch (Exception e) {
                        errorHandler.accept(e);
                    }
                }
            }
        }
    }

    /**
     * Replay all events of a specific type from the journal.
     *
     * @param eventName the name of events to replay
     */
    public void replayAll(@NotNull String eventName) {
        if (eventJournal == null) {
            throw new IllegalStateException("Event sourcing is not enabled");
        }

        var events = eventJournal.getEventsByName(eventName);
        for (var record : events) {
            var actions = namedActions.get(record.eventName);
            if (actions != null) {
                var replayArgs = record.toArguments();
                for (var action : actions) {
                    try {
                        action.accept(replayArgs);
                    } catch (Exception e) {
                        errorHandler.accept(e);
                    }
                }
            }
        }
    }

    /**
     * Sets the error handler for the EventBus.
     * The error handler is called when an exception occurs during action execution or registration.
     *
     * @param handler the error handler consumer
     */
    public void setErrorHandler(@NotNull Consumer<Throwable> handler) {
        this.errorHandler = handler;
    }

    /**
     * Returns a set of all action names currently registered in the EventBus.
     *
     * @return a set of action names
     */
    public Set<String> getActionNames() {
        return namedActions.keySet();
    }

    /**
     * Checks if an action with the specified name is registered in the EventBus.
     *
     * @param name the name of the action to check
     * @return true if the action is registered, false otherwise
     */
    public boolean hasAction(String name) {
        return namedActions.containsKey(name);
    }

    /**
     * Removes the action with the specified name from the EventBus.
     *
     * @param name the name of the action to remove
     */
    public void removeAction(String name) {
        namedActions.remove(name);
    }

    /**
     * Clears all actions registered in the EventBus.
     */
    public void clearActions() {
        namedActions.clear();
    }

    /**
     * Returns the number of actions currently registered in the EventBus.
     *
     * @return the count of registered actions
     */
    public int getActionsCount() {
        return namedActions.size();
    }

    // -------- Inner class Arguments --------

    /**
     * Represents the arguments passed to actions registered in the EventBus.
     * It allows registering parameters, retrieving them by key, and provides methods for type-safe access.
     */
    public static class Arguments {
        private final Map<String, Object> args;
        /**
         * -- GETTER --
         *  Returns the emitter object that triggered the event or action.
         *  This is typically the object that initiated the event.
         *
         * @return the emitter object
         */
        @Getter
        private final Object emitter;

        /**
         * Constructs an Arguments object with the specified emitter.
         * The emitter is typically the object that triggered the event or action.
         *
         * @param emitter the object that triggered the event or action
         */
        public Arguments(Object emitter) {
            this.args = new HashMap<>();
            this.emitter = emitter;
        }

        /**
         * Registers a parameter with the specified key and value.
         * The value can be of any type, but it should be used consistently with its type when retrieved.
         *
         * @param key the key to register the parameter under
         * @param value the value to register
         * @param <T> the type of the value
         */
        public <T> void register(@NotNull String key, T value) {
            args.put(key, value);
        }

        Map<String, Object> snapshotValues() {
            return new HashMap<>(args);
        }

        /**
         * Retrieves a parameter by its key and casts it to the specified type.
         * If the key does not exist or the value is not of the expected type, an exception is thrown.
         *
         * @throws IllegalArgumentException if no value is found for the key
         * @throws ClassCastException if the value is not of the expected type
         *
         * @param key the key of the parameter to retrieve
         * @param clazz the class of the expected type
         * @param <T> the type of the value
         * @return the value cast to the specified type
         */
        public <T> @NotNull T get(@NotNull String key, Class<T> clazz) {
            var value = args.get(key);
            if (value == null) {
                throw new IllegalArgumentException("No value found for key: " + key);
            }

            if (!clazz.isInstance(value)) {
                throw new ClassCastException("Value for key " + key + " is not of type " + clazz.getName());
            }

            return clazz.cast(value);
        }

        /**
         * Retrieves a list of parameters by their key and casts each item to the specified type.
         * If the key does not exist or the value is not a list, an exception is thrown.
         *
         * @throws ClassCastException if the value is not a list or items are not of the expected type
         *
         * @param key the key of the parameter to retrieve
         * @param clazz the class of the expected type for each item in the list
         * @param <T> the type of the items in the list
         * @return a list of items cast to the specified type
         */
        public <T> List<T> getList(@NotNull String key, Class<T> clazz) {
            var value = args.get(key);

            if (!(value instanceof List<?> list)) {
                throw new ClassCastException("Value for key " + key + " is not a list");
            }

            var result = new ArrayList<T>(list.size());
            for (var item : list) {
                if (!clazz.isInstance(item)) {
                    throw new ClassCastException("Item in list is not of type " + clazz.getName());
                }

                result.add(clazz.cast(item));
            }
            return result;
        }

        /**
         * Retrieves a parameter by its key and returns it as an Optional.
         * If the key does not exist, an empty Optional is returned.
         * If the value is not of the expected type, a ClassCastException is thrown.
         *
         * @throws ClassCastException if the value is not of the expected type
         *
         * @param key the key of the parameter to retrieve
         * @param clazz the class of the expected type
         * @param <T> the type of the value
         * @return an Optional containing the value if present, or empty if not found
         */
        public <T> Optional<T> getOptional(@NotNull String key, Class<T> clazz) {
            var value = args.get(key);
            if (value == null) {
                return Optional.empty();
            }

            if (!clazz.isInstance(value)) {
                throw new ClassCastException("Value is not of type " + clazz.getName());
            }
            return Optional.of(clazz.cast(value));
        }

        /**
         * Retrieves a list of parameters by their key and returns it as an Optional.
         * If the key does not exist, an empty Optional is returned.
         * If the value is not a list or items are not of the expected type, a ClassCastException is thrown.
         *
         * @throws ClassCastException if the value is not a list or items are not of the expected type
         *
         * @param key the key of the parameter to retrieve
         * @param clazz the class of the expected type for each item in the list
         * @param <T> the type of the items in the list
         * @return an Optional containing a list of items if present, or empty if not found
         */
        public <T> Optional<List<T>> getOptionalList(@NotNull String key, Class<T> clazz) {
            var value = args.get(key);
            if (value == null)  {
                return Optional.empty();
            }

            if (!(value instanceof List)) {
                throw new ClassCastException("Value for key " + key + " is not a list");
            }

            var result = new ArrayList<T>();
            for (var item : (List<?>) value) {
                if (!clazz.isInstance(item)) {
                    throw new ClassCastException("Item in list is not of type " + clazz.getName());
                }
                result.add(clazz.cast(item));
            }
            return Optional.of(result);
        }

        /**
         * Checks if a parameter with the specified key exists in the Arguments.
         *
         * @param key the key to check
         * @return true if the key exists, false otherwise
         */
        public boolean containsKey(String key) {
            return args.containsKey(key);
        }

        /**
         * Retrieves a parameter by its key and returns it as an Optional.
         * If the key does not exist, the default value is returned.
         * If the value is not of the expected type, a ClassCastException is thrown.
         *
         * @throws ClassCastException if the value is not of the expected type
         *
         * @param key the key of the parameter to retrieve
         * @param clazz the class of the expected type
         * @param defaultValue the default value to return if the key does not exist
         * @param <T> the type of the value
         * @return the value if present, or defaultValue if not found
         */
        public <T> T getOrDefault(String key, Class<T> clazz, T defaultValue) {
            var value = args.get(key);
            if (value == null) {
                return defaultValue;
            }

            if (!clazz.isInstance(value)) {
                throw new ClassCastException("Value is not of type " + clazz.getName());
            }
            return clazz.cast(value);
        }

        /**
         * Retrieves a list of parameters by their key and returns it as a List.
         * If the key does not exist, the default value is returned.
         * If the value is not a list or items are not of the expected type, a ClassCastException is thrown.
         *
         * @throws ClassCastException if the value is not a list or items are not of the expected type
         *
         * @param key the key of the parameter to retrieve
         * @param clazz the class of the expected type for each item in the list
         * @param defaultValue the default value to return if the key does not exist
         * @param <T> the type of the items in the list
         * @return a list of items if present, or defaultValue if not found
         */
        public <T> List<T> getListOrDefault(String key, Class<T> clazz, List<T> defaultValue) {
            var value = args.get(key);
            if (value == null) {
                return defaultValue;
            }

            if (!(value instanceof List)) {
                throw new ClassCastException("Value is not a list");
            }

            var result = new ArrayList<T>();
            for (var item : (List<?>) value) {
                if (!clazz.isInstance(item)) {
                    throw new ClassCastException("Item in list is not of type " + clazz.getName());
                }
                result.add(clazz.cast(item));
            }
            return result;
        }

        /**
         * Returns a map of all keys and their corresponding types in the Arguments.
         * The types are determined by the class of the values registered under each key.
         *
         * @return a map where keys are parameter names and values are their types
         */
        public Map<String, Class<?>> getKeysAndTypes() {
            var types = new HashMap<String, Class<?>>();
            for (var entry : args.entrySet()) {
                types.put(entry.getKey(), entry.getValue().getClass());
            }
            return types;
        }

        /**
         * Returns the emitter object cast to the specified class.
         * If the emitter is not of the expected type, a ClassCastException is thrown.
         *
         * @throws ClassCastException if the emitter is not of the expected type
         *
         * @param clazz the class to cast the emitter to
         * @param <T> the type of the emitter
         * @return the emitter cast to the specified type
         */
        public <T> T getEmitterAs(Class<T> clazz) {
            if (!clazz.isInstance(emitter)) {
                throw new ClassCastException("Emitter is not of type " + clazz.getName());
            }
            return clazz.cast(emitter);
        }
    }
}
