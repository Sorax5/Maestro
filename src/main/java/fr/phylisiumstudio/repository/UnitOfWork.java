package fr.phylisiumstudio.repository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * UnitOfWork pattern implementation for repository batch operations.
 *
 * <p>Allows grouping multiple repository operations (create, update, delete)
 * into a single ordered execution batch.
 * This implementation is <strong>not</strong> ACID transactional: if a failure happens,
 * previously applied operations are not automatically undone.
 *
 * <p>Example:
 * <pre>
 * repository.batch(uow -> {
 *     uow.create(entityA);
 *     uow.update(entityB);
 *     uow.delete(idC);
 *     // execution sequentielle; echec possible avec application partielle
 * });
 * </pre>
 *
 * @param <T> the type of the entity
 * @param <K> the type of the entity's id
 */
public class UnitOfWork<T, K> {
    private final IRepository<T, K> repository;
    private final List<Operation<T, K>> operations = new ArrayList<>();
    private final Object stateLock = new Object();
    private State state = State.ACTIVE;

    /**
     * Create a new UnitOfWork.
     *
     * @param repository the repository to perform operations on
     */
    public UnitOfWork(IRepository<T, K> repository) {
        this.repository = repository;
    }

    /**
     * Register a create operation.
     *
     * @param entity the entity to create
     */
    public void create(T entity) {
        synchronized (stateLock) {
            ensureActive();
            operations.add(new Operation<>(OperationType.CREATE, entity, null));
        }
    }

    /**
     * Register an update operation.
     *
     * @param entity the entity to update
     */
    public void update(T entity) {
        synchronized (stateLock) {
            ensureActive();
            operations.add(new Operation<>(OperationType.UPDATE, entity, null));
        }
    }

    /**
     * Register a delete operation.
     *
     * @param id the id of the entity to delete
     */
    public void delete(K id) {
        synchronized (stateLock) {
            ensureActive();
            operations.add(new Operation<>(OperationType.DELETE, null, id));
        }
    }

    /**
     * Get the number of pending operations.
     *
     * @return the count of operations
     */
    public int getPendingOperationsCount() {
        synchronized (stateLock) {
            return operations.size();
        }
    }

    public boolean isCommitted() {
        synchronized (stateLock) {
            return state == State.COMMITTED;
        }
    }

    public boolean isRolledBack() {
        synchronized (stateLock) {
            return state == State.ROLLED_BACK;
        }
    }

    /**
     * Execute all pending operations sequentially.
     * If an operation fails, execution stops and pending operations are cleared.
     * Operations already applied before the failure are not reverted.
     * This method blocks until completion.
     *
     * @throws RuntimeException if any operation fails
     */
    public void commit() {
        try {
            commitAsync().join();
        } catch (CompletionException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Batch execution failed", cause != null ? cause : e);
        }
    }

    /**
     * Execute all pending operations sequentially without blocking the caller thread.
     * If an operation fails, execution stops and pending operations are cleared.
     * Operations already applied before the failure are not reverted.
     *
     * @return a CompletableFuture completed when execution finishes
     */
    public CompletableFuture<Void> commitAsync() {
        final List<Operation<T, K>> operationsSnapshot;
        synchronized (stateLock) {
            if (state != State.ACTIVE) {
                return CompletableFuture.failedFuture(new IllegalStateException("UnitOfWork is not active: " + state));
            }
            state = State.COMMITTING;
            operationsSnapshot = new ArrayList<>(operations);
        }

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (var op : operationsSnapshot) {
            chain = chain.thenCompose(ignored -> executeOperation(op));
        }

        return chain.thenRun(() -> {
            synchronized (stateLock) {
                if (state != State.COMMITTING) {
                    throw new IllegalStateException("Invalid state transition to COMMITTED from " + state);
                }
                operations.clear();
                state = State.COMMITTED;
            }
        }).handle((ignored, ex) -> {
            if (ex != null) {
                synchronized (stateLock) {
                    operations.clear();
                    if (state != State.COMMITTED) {
                        state = State.ROLLED_BACK;
                    }
                }
                return CompletableFuture.<Void>failedFuture(
                        new RuntimeException("Batch execution failed; previously applied operations may remain: " + ex.getMessage(), ex)
                );
            }
            return CompletableFuture.<Void>completedFuture(null);
        }).thenCompose(future -> future);
    }

    /**
     * Clear pending operations that have not yet been executed.
     * This does not revert operations already applied by {@link #commit()}.
     * Rollback is not allowed while a commit is in progress.
     */
    public void rollback() {
        synchronized (stateLock) {
            if (state == State.COMMITTED) {
                throw new IllegalStateException("UnitOfWork has already been committed");
            }
            if (state == State.COMMITTING) {
                throw new IllegalStateException("UnitOfWork commit is in progress and cannot be rolled back");
            }
            operations.clear();
            state = State.ROLLED_BACK;
        }
    }

    /**
     * Clear all pending operations without committing them.
     */
    public void clear() {
        synchronized (stateLock) {
            operations.clear();
        }
    }

    private CompletableFuture<Void> executeOperation(Operation<T, K> op) {
        return switch (op.type) {
            case CREATE -> repository.create(op.entity).thenApply(ignored -> null);
            case UPDATE -> repository.update(op.entity).thenApply(ignored -> null);
            case DELETE -> repository.delete(op.id);
        };
    }

    private enum OperationType {
        CREATE, UPDATE, DELETE
    }

    private enum State {
        ACTIVE,
        COMMITTING,
        COMMITTED,
        ROLLED_BACK
    }

    private void ensureActive() {
        if (state != State.ACTIVE) {
            throw new IllegalStateException("UnitOfWork is not active: " + state);
        }
    }

    private record Operation<T, K>(OperationType type, T entity, K id) {
    }
}

