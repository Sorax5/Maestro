package fr.phylisiumstudio.repository;

/**
 * Functional interface for UnitOfWork batch callbacks.
 *
 * @param <T> the entity type
 * @param <K> the id type
 */
@FunctionalInterface
public interface BatchCallback<T, K> {
    /**
     * Register operations in a UnitOfWork batch.
     *
     * @param uow the UnitOfWork to register operations on
     * @throws Exception if an error occurs
     */
    void execute(UnitOfWork<T, K> uow) throws Exception;
}

