package fr.phylisiumstudio.repository;

import com.google.gson.Gson;
import lombok.Getter;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract repository implementation using Gson for JSON serialization
 *
 * @param <T> the type of the entity
 * @param <K> the type of the entity's id
 */
@Getter
public abstract class AbstractGsonRepository<T,K> implements IRepository<T,K>, AutoCloseable {
    private final Gson gson;
    private final File folder;
    private final Logger logger;
    private final Executor ioExecutor;
    private final ThreadPoolExecutor managedExecutor;
    private static final int DEFAULT_IO_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final int DEFAULT_IO_QUEUE_CAPACITY = 1024;

    protected AbstractGsonRepository(Gson gson, File folder, Logger logger) {
        this(gson, folder, logger, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Create a repository with an internally managed bounded executor and configurable rejection behavior.
     *
     * <p>The executor is owned by this repository and can be shut down via {@link #close()}.
     *
     * @param gson Gson instance
     * @param folder storage folder
     * @param logger logger instance
     * @param rejectedExecutionHandler rejection policy used when the I/O queue is full
     */
    protected AbstractGsonRepository(
            Gson gson,
            File folder,
            Logger logger,
            RejectedExecutionHandler rejectedExecutionHandler
    ) {
        this(gson, folder, logger, createDefaultIoExecutor(rejectedExecutionHandler), true);
    }

    protected AbstractGsonRepository(Gson gson, File folder, Logger logger, Executor ioExecutor) {
        this(gson, folder, logger, ioExecutor, false);
    }

    private AbstractGsonRepository(Gson gson, File folder, Logger logger, Executor ioExecutor, boolean ownsExecutor) {
        this.gson = gson;
        this.folder = folder;
        this.logger = logger;
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor cannot be null");
        this.managedExecutor = ownsExecutor && ioExecutor instanceof ThreadPoolExecutor pool ? pool : null;

        if (!folder.exists() && !folder.mkdirs()) {
            logger.severe("Failed to create the directory: " + folder.getAbsolutePath());
        }
    }

    private static ThreadPoolExecutor createDefaultIoExecutor(RejectedExecutionHandler rejectedExecutionHandler) {
        var threadCounter = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            var thread = new Thread(runnable, "maestro-repository-io-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        return new ThreadPoolExecutor(
                DEFAULT_IO_THREADS,
                DEFAULT_IO_THREADS,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(DEFAULT_IO_QUEUE_CAPACITY),
                factory,
                Objects.requireNonNull(rejectedExecutionHandler, "rejectedExecutionHandler cannot be null")
        );
    }

    @Override
    public void close() {
        if (managedExecutor != null) {
            managedExecutor.shutdown();
        }
    }

    private <R> CompletableFuture<R> supplyIo(Supplier<R> supplier) {
        try {
            return CompletableFuture.supplyAsync(supplier, ioExecutor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new RuntimeException("I/O task rejected by repository executor", e));
        }
    }

    private CompletableFuture<Void> runIo(Runnable runnable) {
        try {
            return CompletableFuture.runAsync(runnable, ioExecutor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new RuntimeException("I/O task rejected by repository executor", e));
        }
    }

    /**
     * Create a new entity
     *
     * @param entity the entity to create
     * @return a CompletableFuture with the created entity
     */
    @Override
    public CompletableFuture<T> create(T entity) {
        return supplyIo(() -> {
            try {
                var file = GetFile(String.valueOf(GetModelId(entity)));
                var json = gson.toJson(entity);

                Files.writeString(file.toPath(), json);
                return entity;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to create entity: " + entity.toString(), e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Read an entity by its id
     *
     * @param id the id of the entity to read
     * @return a CompletableFuture with the read entity
     */
    @Override
    public CompletableFuture<T> read(K id) {
        return supplyIo(() -> {
            try {
                var file = GetFile(String.valueOf(id));
                if (!file.exists()) {
                    throw new FileNotFoundException();
                }

                var json = Files.readString(file.toPath());
                return gson.fromJson(json, EntityClass());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to read entity with id: " + id, e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Update an existing entity
     *
     * @param entity the entity to update
     * @return a CompletableFuture with the updated entity
     */
    @Override
    public CompletableFuture<T> update(T entity) {
        return supplyIo(() -> {
            try {
                var file = GetFile(String.valueOf(GetModelId(entity)));
                if (!file.exists()) {
                    throw new FileNotFoundException();
                }

                var json = gson.toJson(entity);
                Files.writeString(file.toPath(), json);
                return entity;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to update entity: " + entity.toString(), e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Delete an entity by its id
     *
     * @param id the id of the entity to delete
     * @return a CompletableFuture representing the delete operation
     */
    @Override
    public CompletableFuture<Void> delete(K id) {
        return runIo(() -> {
            try {
                var file = GetFile(String.valueOf(id));
                if (!file.exists()) {
                    throw new FileNotFoundException();
                }

                if (!file.delete()) {
                    throw new RuntimeException("Failed to delete file: " + file.getAbsolutePath());
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to delete entity with id: " + id, e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * List all entities
     *
     * @return a CompletableFuture with the list of all entities
     */
    @Override
    public CompletableFuture<List<T>> list() {
        return supplyIo(() -> {
            try {
                var files = folder.listFiles();
                if (files == null) {
                    throw new RuntimeException("Failed to list files in directory: " + folder.getAbsolutePath());
                }

                List<T> entities = new ArrayList<>();
                for (var file : files) {
                    if (file.isFile()) {
                        var json = Files.readString(file.toPath());
                        var entity = gson.fromJson(json, EntityClass());
                        entities.add(entity);
                    }
                }
                return entities;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to list entities", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Check if an entity exists by its id
     *
     * @param id the id of the entity to check
     * @return a CompletableFuture with true if the entity exists, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> exists(K id) {
        return supplyIo(() -> {
            var file = GetFile(String.valueOf(id));
            return file.exists();
        });
    }

    /**
     * Get the file corresponding to the entity name
     *
     * @param name the name of the entity
     * @return the file corresponding to the entity
     */
    public abstract File GetFile(String name);

    /**
     * Get the class of the entity
     *
     * @return the class of the entity
     */
    public abstract Class<T> EntityClass();

    /**
     * Get the id of the entity
     *
     * @param entity the entity
     * @return the id of the entity
     */
    public abstract K GetModelId(T entity);
}
