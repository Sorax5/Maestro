package fr.phylisiumstudio.repository;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Concrete {@link AbstractGsonRepository} implementation used by integration tests.
 *
 * <p>Each instance creates its own isolated temporary directory on disk.
 * JUnit 5 instantiates a new test class per {@code @Test} method by default,
 * so every test starts with a fresh, empty folder — complementing the
 * {@code @BeforeEach} cleanup in {@link RepositoryIntegrationTest}.</p>
 */
class IntegrationRepository
        extends AbstractGsonRepository<RepositoryIntegrationTest.IntegrationEntity, String> {

    IntegrationRepository() {
        super(
                new Gson(),
                createTempFolder(),
                Logger.getLogger(IntegrationRepository.class.getName())
        );
    }

    // -------------------------------------------------------------------------
    // AbstractGsonRepository contract
    // -------------------------------------------------------------------------

    @Override
    public File GetFile(String name) {
        return new File(getFolder(), name + ".json");
    }

    @Override
    public Class<RepositoryIntegrationTest.IntegrationEntity> EntityClass() {
        return RepositoryIntegrationTest.IntegrationEntity.class;
    }

    @Override
    public String GetModelId(RepositoryIntegrationTest.IntegrationEntity entity) {
        return entity.id();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Create a new temporary directory for this repository instance.
     * Wraps the checked {@link IOException} so it can be called from the
     * {@code super()} chain without a try-catch.
     */
    private static File createTempFolder() {
        try {
            var folder = Files.createTempDirectory("integration-test-repo").toFile();
            folder.deleteOnExit();
            return folder;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp folder for integration tests", e);
        }
    }
}