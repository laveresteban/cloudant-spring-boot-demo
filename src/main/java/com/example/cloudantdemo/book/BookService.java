package com.example.cloudantdemo.book;

import com.example.cloudantdemo.config.CloudantProperties;
import com.ibm.cloud.cloudant.v1.Cloudant;
import com.ibm.cloud.cloudant.v1.model.AllDocsResult;
import com.ibm.cloud.cloudant.v1.model.Document;
import com.ibm.cloud.cloudant.v1.model.DocumentResult;
import com.ibm.cloud.cloudant.v1.model.DeleteDocumentOptions;
import com.ibm.cloud.cloudant.v1.model.FindResult;
import com.ibm.cloud.cloudant.v1.model.GetDatabaseInformationOptions;
import com.ibm.cloud.cloudant.v1.model.GetDocumentOptions;
import com.ibm.cloud.cloudant.v1.model.PostAllDocsOptions;
import com.ibm.cloud.cloudant.v1.model.PostDocumentOptions;
import com.ibm.cloud.cloudant.v1.model.PostFindOptions;
import com.ibm.cloud.cloudant.v1.model.PutDatabaseOptions;
import com.ibm.cloud.cloudant.v1.model.PutDocumentOptions;
import com.ibm.cloud.sdk.core.service.exception.ServiceResponseException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Stores books as JSON documents in a Cloudant database. Every document carries
 * {@code "type": "book"} so that Mango queries only match books.
 */
@Service
public class BookService {

    private static final String TYPE_FIELD = "type";
    private static final String BOOK_TYPE = "book";
    private static final int HTTP_NOT_FOUND = 404;

    private final Cloudant client;
    private final String db;

    public BookService(Cloudant client, CloudantProperties props) {
        this.client = client;
        this.db = props.database();
    }

    /**
     * Creates the database only when it is missing. Probing first means a user with
     * read/write access to an existing database never needs the rights to create one.
     */
    public void ensureDatabase() {
        try {
            client.getDatabaseInformation(
                    new GetDatabaseInformationOptions.Builder().db(db).build()
            ).execute();
        } catch (ServiceResponseException e) {
            if (e.getStatusCode() != HTTP_NOT_FOUND) {
                throw e;
            }
            client.putDatabase(new PutDatabaseOptions.Builder().db(db).build()).execute();
        }
    }

    public Book create(BookRequest request) {
        Document doc = toDocument(request);
        DocumentResult result = client.postDocument(
                new PostDocumentOptions.Builder().db(db).document(doc).build()
        ).execute().getResult();

        return new Book(result.getId(), result.getRev(),
                request.title(), request.author(), request.publishedYear());
    }

    public Book get(String id) {
        Document doc = client.getDocument(
                new GetDocumentOptions.Builder().db(db).docId(id).build()
        ).execute().getResult();

        return toBook(doc);
    }

    public List<Book> list(int limit) {
        AllDocsResult result = client.postAllDocs(
                new PostAllDocsOptions.Builder().db(db).includeDocs(true).limit(limit).build()
        ).execute().getResult();

        return result.getRows().stream()
                .map(row -> row.getDoc())
                .filter(doc -> doc != null && BOOK_TYPE.equals(doc.get(TYPE_FIELD)))
                .map(this::toBook)
                .toList();
    }

    public List<Book> findByAuthor(String author) {
        Map<String, Object> selector = Map.of(TYPE_FIELD, BOOK_TYPE, "author", author);

        FindResult result = client.postFind(
                new PostFindOptions.Builder().db(db).selector(selector).build()
        ).execute().getResult();

        return result.getDocs().stream().map(this::toBook).toList();
    }

    /**
     * Replaces a book. Cloudant rejects the write with a 409 if {@code rev} is stale,
     * which means another client changed the book first.
     */
    public Book update(String id, String rev, BookRequest request) {
        Document doc = toDocument(request);
        doc.setRev(rev);

        DocumentResult result = client.putDocument(
                new PutDocumentOptions.Builder().db(db).docId(id).document(doc).build()
        ).execute().getResult();

        return new Book(result.getId(), result.getRev(),
                request.title(), request.author(), request.publishedYear());
    }

    public void delete(String id, String rev) {
        client.deleteDocument(
                new DeleteDocumentOptions.Builder().db(db).docId(id).rev(rev).build()
        ).execute();
    }

    private Document toDocument(BookRequest request) {
        Document doc = new Document();
        doc.put(TYPE_FIELD, BOOK_TYPE);
        doc.put("title", request.title());
        doc.put("author", request.author());
        if (request.publishedYear() != null) {
            doc.put("publishedYear", request.publishedYear());
        }
        return doc;
    }

    private Book toBook(Document doc) {
        // JSON numbers may come back as Integer or Double depending on the parser.
        Number year = (Number) doc.get("publishedYear");
        return new Book(
                doc.getId(),
                doc.getRev(),
                (String) doc.get("title"),
                (String) doc.get("author"),
                year == null ? null : year.intValue()
        );
    }
}
