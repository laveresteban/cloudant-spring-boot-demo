package com.example.cloudantdemo.book;

import com.example.cloudantdemo.config.CloudantConfig;
import com.example.cloudantdemo.config.CloudantProperties;
import com.ibm.cloud.cloudant.v1.Cloudant;
import com.ibm.cloud.cloudant.v1.model.Document;
import com.ibm.cloud.cloudant.v1.model.PostDocumentOptions;
import com.ibm.cloud.sdk.core.service.exception.ConflictException;
import com.ibm.cloud.sdk.core.service.exception.NotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs {@link BookService} against a real Apache CouchDB, which speaks the same API as
 * Cloudant. Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class BookServiceIntegrationTest {

    private static final int COUCHDB_PORT = 5984;

    @Container
    static GenericContainer<?> couchdb = new GenericContainer<>("couchdb:3.4")
            .withEnv("COUCHDB_USER", "admin")
            .withEnv("COUCHDB_PASSWORD", "password")
            .withExposedPorts(COUCHDB_PORT)
            .waitingFor(Wait.forHttp("/").forPort(COUCHDB_PORT).forStatusCode(200));

    private static Cloudant cloudant;
    private BookService service;
    private String db;

    @BeforeAll
    static void connect() {
        // Built through CloudantConfig so the production wiring is exercised too.
        cloudant = new CloudantConfig().cloudant(new CloudantProperties(
                "http://" + couchdb.getHost() + ":" + couchdb.getMappedPort(COUCHDB_PORT),
                null, "admin", "password", null));
    }

    /** Each test gets its own database so tests cannot see each other's data. */
    void newService() {
        db = "books_it_" + UUID.randomUUID().toString().replace("-", "");
        service = new BookService(cloudant, new CloudantProperties(null, null, null, null, db));
        service.ensureDatabase();
    }

    @Test
    void ensureDatabaseIsIdempotent() {
        newService();

        service.ensureDatabase();
        service.ensureDatabase();

        assertThat(service.list(10)).isEmpty();
    }

    @Test
    void createThenGetRoundTrip() {
        newService();

        Book created = service.create(new BookRequest("Dune", "Frank Herbert", 1965));
        Book fetched = service.get(created.id());

        assertThat(created.rev()).startsWith("1-");
        assertThat(fetched).isEqualTo(created);
    }

    @Test
    void updateWithCurrentRevisionIncrementsRevision() {
        newService();
        Book created = service.create(new BookRequest("Dune", "Frank Herbert", 1965));

        Book updated = service.update(created.id(), created.rev(),
                new BookRequest("Dune (revised)", "Frank Herbert", 1965));

        assertThat(updated.rev()).startsWith("2-");
        assertThat(service.get(created.id()).title()).isEqualTo("Dune (revised)");
    }

    @Test
    void updateWithStaleRevisionIsRejected() {
        newService();
        Book created = service.create(new BookRequest("Dune", "Frank Herbert", 1965));
        service.update(created.id(), created.rev(), new BookRequest("First edit", "Frank Herbert", 1965));

        assertThatThrownBy(() -> service.update(created.id(), created.rev(),
                new BookRequest("Stale edit", "Frank Herbert", 1965)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteRemovesBook() {
        newService();
        Book created = service.create(new BookRequest("Dune", "Frank Herbert", 1965));

        service.delete(created.id(), created.rev());

        assertThatThrownBy(() -> service.get(created.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteWithStaleRevisionIsRejected() {
        newService();
        Book created = service.create(new BookRequest("Dune", "Frank Herbert", 1965));
        service.update(created.id(), created.rev(), new BookRequest("Edited", "Frank Herbert", 1965));

        assertThatThrownBy(() -> service.delete(created.id(), created.rev()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getMissingBookIsNotFound() {
        newService();

        assertThatThrownBy(() -> service.get("does-not-exist"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listExcludesDocumentsOfOtherTypes() {
        newService();
        service.create(new BookRequest("Dune", "Frank Herbert", 1965));
        service.create(new BookRequest("Emma", "Jane Austen", 1815));

        Document other = new Document();
        other.put("type", "invoice");
        other.put("total", 42);
        cloudant.postDocument(new PostDocumentOptions.Builder().db(db).document(other).build())
                .execute();

        List<Book> books = service.list(10);

        assertThat(books).hasSize(2);
        assertThat(books).extracting(Book::title).containsExactlyInAnyOrder("Dune", "Emma");
    }

    @Test
    void searchByAuthorReturnsMatchingBooksOnly() {
        newService();
        service.create(new BookRequest("Dune", "Frank Herbert", 1965));
        service.create(new BookRequest("Emma", "Jane Austen", 1815));

        List<Book> results = service.findByAuthor("Jane Austen", 25);

        assertThat(results).extracting(Book::title).containsExactly("Emma");
    }

    @Test
    void searchByAuthorHonoursLimitAboveTheDatabaseDefault() {
        newService();
        for (int i = 0; i < 30; i++) {
            service.create(new BookRequest("Volume " + i, "Prolific Author", 1900 + i));
        }

        assertThat(service.findByAuthor("Prolific Author", 30)).hasSize(30);
    }
}
