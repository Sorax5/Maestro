package fr.phylisiumstudio.repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IRepository<T,K> {
    CompletableFuture<T> create(T entity);
    CompletableFuture<T> read(K id);
    CompletableFuture<T> update(T entity);
    CompletableFuture<Void> delete(K id);
    CompletableFuture<List<T>> list();
    CompletableFuture<Boolean> exists(K id);
}
