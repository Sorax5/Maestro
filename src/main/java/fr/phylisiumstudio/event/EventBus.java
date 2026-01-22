package fr.phylisiumstudio.event;

import fr.phylisiumstudio.annotation.ActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * EventBus is a simple event bus implementation that allows registering, unregistering, and executing actions
 * based on method annotations. It supports parameterized actions using the Arguments class.
 * Actions can be registered by annotating methods with @ActionHandler and specifying the action name.
 */
public class EventBus {
    private final Map<String, List<Consumer<Arguments>>> namedActions = new ConcurrentHashMap<>();

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
            Arrays.stream(handler.getClass().getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(ActionHandler.class))
                    .filter(method -> method.getParameterCount() == 1)
                    .filter(method -> method.getParameterTypes()[0].equals(Arguments.class))
                    .forEach(method -> {
                        ActionHandler annotation = method.getAnnotation(ActionHandler.class);
                        String name = annotation.event();
                        method.setAccessible(true);

                        Consumer<Arguments> action = args -> {
                            try {
                                method.invoke(handler, args);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                throw new RuntimeException("Error invoking action '" + name + "'", e);
                            }
                        };

                        namedActions.computeIfAbsent(name, k -> new ArrayList<>()).add(action);
                    });
        }
        catch (Exception e) {
            System.err.println("Error registering actions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Unregisters actions from the given handler object.
     * The handler's methods must be annotated with @ActionBus.
     * The method name specified in the annotation will be used to find and remove the actions.
     *
     * @throws RuntimeException if an error occurs during unregistration
     *
     * @param handler the object containing methods to unregister as actions
     */
    public void unregisterActions(@NotNull Object handler) {
        try {
            Arrays.stream(handler.getClass().getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(ActionHandler.class))
                    .forEach(method -> {
                        ActionHandler annotation = method.getAnnotation(ActionHandler.class);
                        String name = annotation.event();

                        List<Consumer<Arguments>> actions = namedActions.get(name);
                        if (actions != null) {
                            actions.removeIf(action -> action.toString().contains(method.getName()));
                            if (actions.isEmpty()) {
                                namedActions.remove(name);
                            }
                        }
                    });
        }
        catch (Exception e) {
            System.err.println("Error unregistering actions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes all actions registered under the specified action name.
     * The provided Arguments object will be passed to each action.
     *
     * @throws RuntimeException if an error occurs during action execution
     *
     * @param actionName the name of the action to execute
     * @param args the Arguments object containing parameters for the action
     */
    public void execute(String actionName, @NotNull Arguments args) {
        try {
            List<Consumer<Arguments>> actions = namedActions.get(actionName);
            if (actions == null || actions.isEmpty()) {
                return;
            }

            for (Consumer<Arguments> action : actions) {
                try {
                    action.accept(args);
                } catch (Exception e) {
                    System.err.println("Error executing action '" + actionName + "': " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        catch (Exception e) {
            System.err.println("Error executing action '" + actionName + "': " + e.getMessage());
            e.printStackTrace();
        }
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
        public <T> @Nullable T get(@NotNull String key, Class<T> clazz) {
            Object value = args.get(key);
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
            Object value = args.get(key);

            if (!(value instanceof List<?> list)) {
                throw new ClassCastException("Value for key " + key + " is not a list");
            }

            List<T> result = new ArrayList<>(list.size());
            for (Object item : list) {
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
            Object value = args.get(key);
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
            Object value = args.get(key);
            if (value == null)  {
                return Optional.empty();
            }

            if (!(value instanceof List)) {
                throw new ClassCastException("Value for key " + key + " is not a list");
            }

            List<T> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
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
            Object value = args.get(key);
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
            Object value = args.get(key);
            if (value == null) {
                return defaultValue;
            }

            if (!(value instanceof List)) {
                throw new ClassCastException("Value is not a list");
            }

            List<T> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
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
            Map<String, Class<?>> types = new HashMap<>();
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                types.put(entry.getKey(), entry.getValue().getClass());
            }
            return types;
        }

        /**
         * Returns the emitter object that triggered the event or action.
         * This is typically the object that initiated the event.
         *
         * @return the emitter object
         */
        public Object getEmitter() {
            return emitter;
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
