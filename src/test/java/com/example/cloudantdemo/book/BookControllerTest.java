package com.example.cloudantdemo.book;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
class BookControllerTest {

    private static final String VALID_BOOK = """
            {"title": "Dune", "author": "Frank Herbert", "publishedYear": 1965}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BookService bookService;

    @Test
    void createReturns201WithLocation() throws Exception {
        given(bookService.create(any())).willReturn(
                new Book("abc123", "1-rev", "Dune", "Frank Herbert", 1965));

        mvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BOOK))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/books/abc123"))
                .andExpect(jsonPath("$.id").value("abc123"))
                .andExpect(jsonPath("$.rev").value("1-rev"));
    }

    @Test
    void createRejectsBlankTitle() throws Exception {
        mvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "author": "Frank Herbert"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getReturnsBook() throws Exception {
        given(bookService.get("abc123")).willReturn(
                new Book("abc123", "1-rev", "Dune", "Frank Herbert", 1965));

        mvc.perform(get("/api/books/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dune"))
                .andExpect(jsonPath("$.publishedYear").value(1965));
    }

    @Test
    void listUsesDefaultLimit() throws Exception {
        given(bookService.list(anyInt())).willReturn(List.of(
                new Book("abc123", "1-rev", "Dune", "Frank Herbert", 1965)));

        mvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("abc123"));

        verify(bookService).list(25);
    }

    @Test
    void listRejectsLimitOutOfRange() throws Exception {
        mvc.perform(get("/api/books").param("limit", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchByAuthorDelegatesToService() throws Exception {
        given(bookService.findByAuthor(anyString())).willReturn(List.of());

        mvc.perform(get("/api/books/search").param("author", "Frank Herbert"))
                .andExpect(status().isOk());

        verify(bookService).findByAuthor("Frank Herbert");
    }

    @Test
    void updatePassesRevisionToService() throws Exception {
        given(bookService.update(anyString(), anyString(), any())).willReturn(
                new Book("abc123", "2-new", "Dune", "Frank Herbert", 1965));

        mvc.perform(put("/api/books/abc123")
                        .param("rev", "1-rev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BOOK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rev").value("2-new"));

        verify(bookService).update(eq("abc123"), eq("1-rev"), any());
    }

    @Test
    void createRejectsPublishedYearOutOfRange() throws Exception {
        mvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Old", "author": "Anon", "publishedYear": 999}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWithoutRevReturns400() throws Exception {
        mvc.perform(put("/api/books/abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BOOK))
                .andExpect(status().isBadRequest());

        verify(bookService, never()).update(anyString(), anyString(), any());
    }

    @Test
    void searchWithoutAuthorReturns400() throws Exception {
        mvc.perform(get("/api/books/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteWithoutRevReturns400() throws Exception {
        mvc.perform(delete("/api/books/abc123"))
                .andExpect(status().isBadRequest());

        verify(bookService, never()).delete(anyString(), anyString());
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/books/abc123").param("rev", "1-rev"))
                .andExpect(status().isNoContent());

        verify(bookService).delete("abc123", "1-rev");
    }
}
