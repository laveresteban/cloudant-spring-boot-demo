package com.example.cloudantdemo.book;

import com.example.cloudantdemo.config.CloudantProperties;
import com.ibm.cloud.cloudant.v1.Cloudant;
import com.ibm.cloud.cloudant.v1.model.AllDocsResult;
import com.ibm.cloud.cloudant.v1.model.DatabaseInformation;
import com.ibm.cloud.cloudant.v1.model.DeleteDocumentOptions;
import com.ibm.cloud.cloudant.v1.model.DocsResultRow;
import com.ibm.cloud.cloudant.v1.model.Document;
import com.ibm.cloud.cloudant.v1.model.DocumentResult;
import com.ibm.cloud.cloudant.v1.model.FindResult;
import com.ibm.cloud.cloudant.v1.model.GetDatabaseInformationOptions;
import com.ibm.cloud.cloudant.v1.model.PostAllDocsOptions;
import com.ibm.cloud.cloudant.v1.model.PostDocumentOptions;
import com.ibm.cloud.cloudant.v1.model.PostFindOptions;
import com.ibm.cloud.cloudant.v1.model.PutDatabaseOptions;
import com.ibm.cloud.cloudant.v1.model.PutDocumentOptions;
import com.ibm.cloud.cloudant.v1.model.Ok;
import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.http.ServiceCall;
import com.ibm.cloud.sdk.core.service.exception.ConflictException;
import com.ibm.cloud.sdk.core.service.exception.NotFoundException;
import com.ibm.cloud.sdk.core.service.exception.ServiceResponseException;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    private static final String DB = "books";

    @Mock
    private Cloudant client;

    private BookService service;

    @BeforeEach
    void setUp() {
        service = new BookService(client,
                new CloudantProperties("http://unused", null, null, null, DB));
    }

    // ---- ensureDatabase -------------------------------------------------------

    @Test
    void ensureDatabaseDoesNotCreateExistingDatabase() {
        ServiceCall<DatabaseInformation> probe = mockCall();
        given(client.getDatabaseInformation(any(GetDatabaseInformationOptions.class)))
                .willReturn(probe);

        service.ensureDatabase();

        verify(client, never()).putDatabase(any(PutDatabaseOptions.class));
    }

    @Test
    void ensureDatabaseCreatesMissingDatabase() {
        ServiceCall<DatabaseInformation> probe = mockCall();
        given(client.getDatabaseInformation(any(GetDatabaseInformationOptions.class)))
                .willReturn(probe);
        given(probe.execute()).willThrow(new NotFoundException(httpResponse(404)));
        ServiceCall<Ok> create = mockCall();
        given(client.putDatabase(any(PutDatabaseOptions.class))).willReturn(create);

        service.ensureDatabase();

        ArgumentCaptor<PutDatabaseOptions> captor = ArgumentCaptor.forClass(PutDatabaseOptions.class);
        verify(client).putDatabase(captor.capture());
        assertThat(captor.getValue().db()).isEqualTo(DB);
    }

    @Test
    void ensureDatabaseRethrowsUnexpectedErrors() {
        ServiceCall<DatabaseInformation> probe = mockCall();
        given(client.getDatabaseInformation(any(GetDatabaseInformationOptions.class)))
                .willReturn(probe);
        given(probe.execute()).willThrow(new ServiceResponseException(403, httpResponse(403)));

        assertThatThrownBy(() -> service.ensureDatabase())
                .isInstanceOf(ServiceResponseException.class);
        verify(client, never()).putDatabase(any(PutDatabaseOptions.class));
    }

    // ---- create / get --------------------------------------------------------

    @Test
    void createStoresBookAsTypedDocument() {
        DocumentResult saved = mock(DocumentResult.class);
        given(saved.getId()).willReturn("doc-1");
        given(saved.getRev()).willReturn("1-abc");
        ServiceCall<DocumentResult> call = mockCall();
        given(client.postDocument(any(PostDocumentOptions.class))).willReturn(call);
        given(call.execute().getResult()).willReturn(saved);

        Book book = service.create(new BookRequest("Dune", "Frank Herbert", 1965));

        ArgumentCaptor<PostDocumentOptions> captor = ArgumentCaptor.forClass(PostDocumentOptions.class);
        verify(client).postDocument(captor.capture());
        Document sent = captor.getValue().document();
        assertThat(captor.getValue().db()).isEqualTo(DB);
        assertThat(sent.get("type")).isEqualTo("book");
        assertThat(sent.get("title")).isEqualTo("Dune");
        assertThat(sent.get("author")).isEqualTo("Frank Herbert");
        assertThat(sent.get("publishedYear")).isEqualTo(1965);

        assertThat(book).isEqualTo(new Book("doc-1", "1-abc", "Dune", "Frank Herbert", 1965));
    }

    @Test
    void createOmitsPublishedYearWhenAbsent() {
        DocumentResult saved = mock(DocumentResult.class);
        ServiceCall<DocumentResult> call = mockCall();
        given(client.postDocument(any(PostDocumentOptions.class))).willReturn(call);
        given(call.execute().getResult()).willReturn(saved);

        service.create(new BookRequest("Untitled", "Anon", null));

        ArgumentCaptor<PostDocumentOptions> captor = ArgumentCaptor.forClass(PostDocumentOptions.class);
        verify(client).postDocument(captor.capture());
        assertThat(captor.getValue().document().get("publishedYear")).isNull();
    }

    @Test
    void getMapsDocumentToBook() {
        ServiceCall<Document> call = mockCall();
        given(client.getDocument(any())).willReturn(call);
        given(call.execute().getResult()).willReturn(bookDocument("doc-1", "1-abc", 1965.0));

        Book book = service.get("doc-1");

        // JSON numbers may arrive as Double; the mapper must still produce an Integer year.
        assertThat(book).isEqualTo(new Book("doc-1", "1-abc", "Dune", "Frank Herbert", 1965));
    }

    @Test
    void getAllowsMissingPublishedYear() {
        ServiceCall<Document> call = mockCall();
        given(client.getDocument(any())).willReturn(call);
        given(call.execute().getResult()).willReturn(bookDocument("doc-1", "1-abc", null));

        assertThat(service.get("doc-1").publishedYear()).isNull();
    }

    // ---- list / search ---------------------------------------------------------

    @Test
    void listReturnsOnlyBookDocuments() {
        DocsResultRow bookRow = row(bookDocument("doc-1", "1-a", 1965.0));
        Document other = new Document();
        other.setId("doc-2");
        other.put("type", "invoice");
        DocsResultRow otherRow = row(other);
        DocsResultRow emptyRow = row(null);

        AllDocsResult result = mock(AllDocsResult.class);
        given(result.getRows()).willReturn(List.of(bookRow, otherRow, emptyRow));
        ServiceCall<AllDocsResult> call = mockCall();
        given(client.postAllDocs(any(PostAllDocsOptions.class))).willReturn(call);
        given(call.execute().getResult()).willReturn(result);

        List<Book> books = service.list(10);

        assertThat(books).containsExactly(new Book("doc-1", "1-a", "Dune", "Frank Herbert", 1965));

        ArgumentCaptor<PostAllDocsOptions> captor = ArgumentCaptor.forClass(PostAllDocsOptions.class);
        verify(client).postAllDocs(captor.capture());
        assertThat(captor.getValue().includeDocs()).isTrue();
        assertThat(captor.getValue().limit()).isEqualTo(10);
    }

    @Test
    void findByAuthorQueriesByTypeAndAuthor() {
        FindResult result = mock(FindResult.class);
        given(result.getDocs()).willReturn(List.of(bookDocument("doc-1", "1-a", 1965.0)));
        ServiceCall<FindResult> call = mockCall();
        given(client.postFind(any(PostFindOptions.class))).willReturn(call);
        given(call.execute().getResult()).willReturn(result);

        List<Book> books = service.findByAuthor("Frank Herbert", 40);

        ArgumentCaptor<PostFindOptions> captor = ArgumentCaptor.forClass(PostFindOptions.class);
        verify(client).postFind(captor.capture());
        Map<String, Object> selector = captor.getValue().selector();
        assertThat(selector).isEqualTo(Map.of("type", "book", "author", "Frank Herbert"));
        assertThat(captor.getValue().limit()).isEqualTo(40);
        assertThat(books).hasSize(1);
    }

    // ---- update / delete -------------------------------------------------------

    @Test
    void updateSendsCurrentRevisionInDocumentBody() {
        DocumentResult saved = mock(DocumentResult.class);
        given(saved.getId()).willReturn("doc-1");
        given(saved.getRev()).willReturn("2-b");
        ServiceCall<DocumentResult> call = mockCall();
        given(client.putDocument(any(PutDocumentOptions.class))).willReturn(call);
        given(call.execute().getResult()).willReturn(saved);

        Book updated = service.update("doc-1", "1-a", new BookRequest("Dune", "Frank Herbert", 1965));

        ArgumentCaptor<PutDocumentOptions> captor = ArgumentCaptor.forClass(PutDocumentOptions.class);
        verify(client).putDocument(captor.capture());
        assertThat(captor.getValue().docId()).isEqualTo("doc-1");
        assertThat(captor.getValue().document().getRev()).isEqualTo("1-a");
        assertThat(updated.rev()).isEqualTo("2-b");
    }

    @Test
    void deleteSendsIdAndRevision() {
        ServiceCall<DocumentResult> call = mockCall();
        given(client.deleteDocument(any(DeleteDocumentOptions.class))).willReturn(call);

        service.delete("doc-1", "1-a");

        ArgumentCaptor<DeleteDocumentOptions> captor = ArgumentCaptor.forClass(DeleteDocumentOptions.class);
        verify(client).deleteDocument(captor.capture());
        assertThat(captor.getValue().docId()).isEqualTo("doc-1");
        assertThat(captor.getValue().rev()).isEqualTo("1-a");
    }

    @Test
    void updatePropagatesConflict() {
        ServiceCall<DocumentResult> call = mockCall();
        given(client.putDocument(any(PutDocumentOptions.class))).willReturn(call);
        given(call.execute()).willThrow(new ConflictException(httpResponse(409)));

        assertThatThrownBy(() -> service.update("doc-1", "1-old", new BookRequest("T", "A", null)))
                .isInstanceOf(ConflictException.class);
    }

    // ---- helpers ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <T> ServiceCall<T> mockCall() {
        ServiceCall<T> call = mock(ServiceCall.class);
        Response<T> response = mock(Response.class);
        try {
            org.mockito.Mockito.lenient().when(call.execute()).thenReturn(response);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return call;
    }

    private static Document bookDocument(String id, String rev, Object year) {
        Document doc = new Document();
        doc.setId(id);
        doc.setRev(rev);
        doc.put("type", "book");
        doc.put("title", "Dune");
        doc.put("author", "Frank Herbert");
        if (year != null) {
            doc.put("publishedYear", year);
        }
        return doc;
    }

    private static DocsResultRow row(Document doc) {
        DocsResultRow row = mock(DocsResultRow.class);
        given(row.getDoc()).willReturn(doc);
        return row;
    }

    static okhttp3.Response httpResponse(int code) {
        return new okhttp3.Response.Builder()
                .request(new Request.Builder().url("http://localhost/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("status " + code)
                .body(ResponseBody.create("{\"error\":\"test\",\"reason\":\"test\"}", (MediaType) null))
                .build();
    }
}
