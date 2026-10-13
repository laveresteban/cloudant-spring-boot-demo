package com.example.cloudantdemo.web;

import com.example.cloudantdemo.book.BookController;
import com.example.cloudantdemo.book.BookService;
import com.ibm.cloud.sdk.core.service.exception.ConflictException;
import com.ibm.cloud.sdk.core.service.exception.NotFoundException;
import com.ibm.cloud.sdk.core.service.exception.ServiceResponseException;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies that SDK exceptions become the documented HTTP status codes. */
@WebMvcTest(BookController.class)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BookService bookService;

    @Test
    void notFoundMapsTo404() throws Exception {
        given(bookService.get("missing")).willThrow(new NotFoundException(httpResponse(404)));

        mvc.perform(get("/api/books/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Book not found"));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        given(bookService.update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .willThrow(new ConflictException(httpResponse(409)));

        mvc.perform(put("/api/books/abc").param("rev", "1-old")
                        .contentType("application/json")
                        .content("{\"title\":\"T\",\"author\":\"A\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void cloudantClientErrorIsPassedThrough() throws Exception {
        given(bookService.get("x")).willThrow(new ServiceResponseException(403, httpResponse(403)));

        mvc.perform(get("/api/books/x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void cloudantServerErrorMapsTo502() throws Exception {
        given(bookService.get("x")).willThrow(new ServiceResponseException(503, httpResponse(503)));

        mvc.perform(get("/api/books/x"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    private static Response httpResponse(int code) {
        return new Response.Builder()
                .request(new Request.Builder().url("http://localhost/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("status " + code)
                .body(ResponseBody.create("{\"error\":\"test\",\"reason\":\"test\"}", (MediaType) null))
                .build();
    }
}
