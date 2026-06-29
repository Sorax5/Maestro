package fr.phylisiumstudio.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AbstractGsonRepository.
 * Covers async CRUD operations, concurrency, error handling,
 * and thread pool metrics.
 */
class RepositoryIntegrationTest {

    private final IntegrationRepository repository = new IntegrationRepository();

    @BeforeEach
    void setUp() {
        var files = repository.getFolder().listFiles();
        if (files != null) {
            Arrays.stream(files).forEach(File::delete);
        }
    }

    @Test
    void testCreate() throws Exception {
        var result = repository.create(new IntegrationEntity("1", "Entity One", 42)).get();

        assertNotNull(result);
        assertEquals("Entity One", result.name());
        assertEquals(42, result.value());

        var persisted = repository.read("1").get();
        assertEquals("1", persisted.id());
        assertEquals("Entity One", persisted.name());
        assertEquals(42, persisted.value());
    }

    @Test
    void testRead() throws Exception {
        repository.create(new IntegrationEntity("1", "Test Entity", 99)).get();

        var read = repository.read("1").get();

        assertEquals("1", read.id());
        assertEquals("Test Entity", read.name());
        assertEquals(99, read.value());
    }

    @Test
    void testUpdate() throws Exception {
        repository.create(new IntegrationEntity("1", "Original", 10)).get();

        var result = repository.update(new IntegrationEntity("1", "Updated", 20)).get();

        assertEquals("Updated", result.name());
        assertEquals(20, result.value());

        var persisted = repository.read("1").get();
        assertEquals("Updated", persisted.name());
        assertEquals(20, persisted.value());
    }

    @Test
    void testDelete() throws Exception {
        repository.create(new IntegrationEntity("1", "ToDelete", null)).get();
        assertTrue(repository.exists("1").get());

        repository.delete("1").get();

        assertFalse(repository.exists("1").get());
    }

    @Test
    void testListEmpty() throws Exception {
        var list = repository.list().get();
        assertNotNull(list);
        assertTrue(list.isEmpty(), "list() on an empty folder should return an empty list");
    }

    @Test
    void testList() throws Exception {
        repository.create(new IntegrationEntity("1", "Entity One", null)).get();
        repository.create(new IntegrationEntity("2", "Entity Two", null)).get();

        var list = repository.list().get();

        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(e -> e.id().equals("1")));
        assertTrue(list.stream().anyMatch(e -> e.id().equals("2")));
    }

    @Test
    void testExists() throws Exception {
        assertFalse(repository.exists("ghost").get());

        repository.create(new IntegrationEntity("ghost", "Should Exist", null)).get();

        assertTrue(repository.exists("ghost").get());
    }

    @Test
    void testCreateOverwritesExistingId() throws Exception {
        repository.create(new IntegrationEntity("1", "First", 1)).get();
        repository.create(new IntegrationEntity("1", "Second", 2)).get();

        var read = repository.read("1").get();
        assertEquals("Second", read.name());
        assertEquals(2, read.value());

        assertEquals(1, repository.list().get().size());
    }

    @Test
    void testCreateWithNullValue() throws Exception {
        repository.create(new IntegrationEntity("nullval", "Null Value", null)).get();

        var read = repository.read("nullval").get();
        assertNull(read.value());
    }

    @Test
    void testReadNonExistentThrows() {
        var future = repository.read("does-not-exist");
        assertThrows(ExecutionException.class, future::get,
                "Reading a missing entity should complete exceptionally");
    }

    @Test
    void testUpdateNonExistentThrows() {
        var future = repository.update(new IntegrationEntity("ghost", "Ghost", 0));
        assertThrows(ExecutionException.class, future::get,
                "Updating a missing entity should complete exceptionally");
    }

    @Test
    void testDeleteNonExistentThrows() {
        var future = repository.delete("does-not-exist");
        assertThrows(ExecutionException.class, future::get,
                "Deleting a missing entity should complete exceptionally");
    }

    @Test
    void testBatchCreate() throws Exception {
        var futures = IntStream.rangeClosed(1, 3)
                .mapToObj(i -> repository.create(new IntegrationEntity(String.valueOf(i), "Batch " + i, i)))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        var list = repository.list().get();
        assertEquals(3, list.size());
        assertTrue(list.stream().anyMatch(e -> e.id().equals("1") && e.value() == 1));
        assertTrue(list.stream().anyMatch(e -> e.id().equals("2") && e.value() == 2));
        assertTrue(list.stream().anyMatch(e -> e.id().equals("3") && e.value() == 3));
    }

    @Test
    void testBatchRead() throws Exception {
        repository.create(new IntegrationEntity("r1", "Read One", 1)).get();
        repository.create(new IntegrationEntity("r2", "Read Two", 2)).get();

        var f1 = repository.read("r1");
        var f2 = repository.read("r2");
        CompletableFuture.allOf(f1, f2).join();

        assertEquals("Read One", f1.get().name());
        assertEquals("Read Two", f2.get().name());
    }

    @Test
    void testBatchDelete() throws Exception {
        repository.create(new IntegrationEntity("b1", "Batch Delete One", 1)).get();
        repository.create(new IntegrationEntity("b2", "Batch Delete Two", 2)).get();

        CompletableFuture.allOf(
                repository.delete("b1"),
                repository.delete("b2")
        ).join();

        assertFalse(repository.exists("b1").get());
        assertFalse(repository.exists("b2").get());
    }

    @Test
    void testConcurrentCreates() throws Exception {
        var futures = IntStream.range(0, 100)
                .mapToObj(i -> repository.create(new IntegrationEntity("stress-" + i, "Stress", i)))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        var list = repository.list().get();
        assertEquals(100, list.size());
        assertTrue(list.stream().anyMatch(e -> e.id().equals("stress-0") && e.value() == 0));
        assertTrue(list.stream().anyMatch(e -> e.id().equals("stress-99") && e.value() == 99));
    }

    @Test
    void testRunAsync() throws Exception {
        var called = new AtomicBoolean(false);

        repository.runAsync(() -> {}, ignored -> called.set(true)).get();

        assertTrue(called.get(), "Consumer passed to runAsync should be invoked");
    }

    @Test
    void testRunAsyncWithNullConsumer() {
        assertDoesNotThrow(() -> repository.runAsync(() -> {}, null).get());
    }

    @Test
    void testThreadPoolMetricsAfterOperations() throws Exception {
        repository.create(new IntegrationEntity("m1", "Metrics", 1)).get();
        repository.create(new IntegrationEntity("m2", "Metrics", 2)).get();

        var metrics = repository.getThreadPoolMetrics();

        assertTrue(metrics.completedTasks() >= 2, "At least two tasks should have completed");
        assertTrue(metrics.activeThreads() >= 0);
        assertTrue(metrics.queueSize() >= 0);
    }

    @Test
    void testGetCorePoolSize() {
        int poolSize = repository.getCorePoolSize();
        int expected = Math.max(2, Runtime.getRuntime().availableProcessors());
        assertEquals(expected, poolSize);
    }

    @Test
    void testCloseShutdownsExecutor() {
        var repo = new IntegrationRepository();
        assertDoesNotThrow(repo::close, "close() should not throw");
    }

    record IntegrationEntity(String id, String name, Integer value) {}
}