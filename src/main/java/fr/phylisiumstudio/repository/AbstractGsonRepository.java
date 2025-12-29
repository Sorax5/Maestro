package fr.phylisiumstudio.repository;

import com.google.gson.Gson;
import lombok.Getter;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    @Override
    public CompletableFuture<T> create(T entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = GetFile(String.valueOf(entity.hashCode()));
                String json = gson.toJson(entity);

                Files.write(file.toPath(), json.getBytes());
                return entity;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to create entity: " + entity.toString(), e);
                throw new RuntimeException(e);
            }
        });
    }

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

    @Override
    public CompletableFuture<T> update(T entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = GetFile(String.valueOf(entity.hashCode()));
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

    @Override
    public CompletableFuture<List<T>> list() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File[] files = folder.listFiles();
                if (files == null) {
                    throw new RuntimeException("Failed to list files in directory: " + folder.getAbsolutePath());
                }

                List<T> entities = new java.util.ArrayList<>();
                for (File file : files) {
                    if (file.isFile()) {
                        String json = new String(Files.readAllBytes(file.toPath()));
                        T entity = gson.fromJson(json, (Class<T>) EntityClass());
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

    @Override
    public CompletableFuture<Boolean> exists(K id) {
        return CompletableFuture.supplyAsync(() -> {
            File file = GetFile(String.valueOf(id));
            return file.exists();
        });
    }

    public abstract File GetFile(String name);

    public abstract Class<T> EntityClass();
}
