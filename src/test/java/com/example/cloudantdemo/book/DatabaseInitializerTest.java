package com.example.cloudantdemo.book;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DatabaseInitializerTest {

    @Test
    void runEnsuresDatabaseExists() {
        BookService bookService = mock(BookService.class);

        new DatabaseInitializer(bookService).run(new DefaultApplicationArguments());

        verify(bookService).ensureDatabase();
    }
}
