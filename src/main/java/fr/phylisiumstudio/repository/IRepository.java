package fr.phylisiumstudio.repository;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public interface IRepository<T,K> {
    CompletableFuture<T> create(T entity);
    CompletableFuture<T> read(K id);
    CompletableFuture<T> update(T entity);
    CompletableFuture<Void> delete(K id);
    CompletableFuture<List<T>> list();
    CompletableFuture<Boolean> exists(K id);

    /**
     * Execute a best-effort batch of operations sequentially.
     *
     * <p>This is not an atomic transaction: if one operation fails,
     * earlier operations may already be applied and are not automatically reverted.
     *
     * <p>Example:
     * <pre>
     * repository.batch(uow -> {
     *     uow.create(entityA);
     *     uow.update(entityB);
     *     uow.delete(idC);
     * });
     * </pre>
     *
     * @param callback the callback used to register batch operations
     * @return a CompletableFuture that completes when the batch finishes
     */
    default CompletableFuture<Void> batch(BatchCallback<T, K> callback) {
        var uow = new UnitOfWork<>(this);
        try {
            callback.execute(uow);
        } catch (Exception e) {
            uow.rollback();
            return CompletableFuture.failedFuture(
                    new RuntimeException("Batch setup failed; no new operation was executed: " + e.getMessage(), e)
            );
        }
        return uow.commitAsync();
    }

    /**
     * Execute a best-effort batch on a caller-provided executor.
     *
     * <p>This overload avoids using the common pool and lets applications choose
     * their own threading strategy for batch setup/dispatch.
     *
     * @param callback the callback used to register batch operations
     * @param executor executor used to run batch setup
     * @return a CompletableFuture that completes when the batch finishes
     */
    default CompletableFuture<Void> batch(BatchCallback<T, K> callback, Executor executor) {
        Objects.requireNonNull(executor, "executor cannot be null");
        return CompletableFuture.supplyAsync(() -> {
            var uow = new UnitOfWork<>(this);
            try {
                callback.execute(uow);
                return uow;
            } catch (Exception e) {
                uow.rollback();
                throw new CompletionException(
                        new RuntimeException("Batch setup failed; no new operation was executed: " + e.getMessage(), e)
                );
            }
        }, executor).thenCompose(UnitOfWork::commitAsync);
    }
}

