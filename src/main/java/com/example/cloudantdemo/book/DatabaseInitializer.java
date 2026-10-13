package com.example.cloudantdemo.book;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates the Cloudant database on startup so the API works on a fresh account. */
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private final BookService bookService;

    public DatabaseInitializer(BookService bookService) {
        this.bookService = bookService;
    }

    @Override
    public void run(ApplicationArguments args) {
        bookService.ensureDatabase();
    }
}
