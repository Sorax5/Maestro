package fr.phylisiumstudio.repository;

import com.google.gson.Gson;
import lombok.Getter;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract repository implementation using Gson for JSON serialization
 *
 * @param <T> the type of the entity
 * @param <K> the type of the entity's id
 */
@Getter
public abstract class AbstractGsonRepository<T,K> implements IRepository<T,K> {
    private final Gson gson;
    private final File folder;
    private final Logger logger;

    protected AbstractGsonRepository(Gson gson, File folder, Logger logger) {
        this.gson = gson;
        this.folder = folder;
        this.logger = logger;

        if (!folder.exists() && !folder.mkdirs()) {
            logger.severe("Failed to create the directory: " + folder.getAbsolutePath());
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = GetFile(String.valueOf(GetModelId(entity)));
                String json = gson.toJson(entity);

                Files.write(file.toPath(), json.getBytes());
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = GetFile(String.valueOf(id));
                if (!file.exists()) {
                    throw new FileNotFoundException();
                }

                String json = new String(Files.readAllBytes(file.toPath()));
                return gson.fromJson(json, (Class<T>) EntityClass());
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = GetFile(String.valueOf(GetModelId(entity)));
                if (!file.exists()) {
                    throw new FileNotFoundException();
                }

                String json = gson.toJson(entity);
                Files.write(file.toPath(), json.getBytes());
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
        return CompletableFuture.runAsync(() -> {
            try {
                File file = GetFile(String.valueOf(id));
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                File[] files = folder.listFiles();
                if (files == null) {
                    throw new RuntimeException("Failed to list files in directory: " + folder.getAbsolutePath());
                }

                List<T> entities = new ArrayList<>();
                for (File file : files) {
                    if (file.isFile()) {
                        String json = new String(Files.readAllBytes(file.toPath()));
                        T entity = gson.fromJson(json, EntityClass());
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
        return CompletableFuture.supplyAsync(() -> {
            File file = GetFile(String.valueOf(id));
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
