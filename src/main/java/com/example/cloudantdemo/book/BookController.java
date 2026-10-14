package com.example.cloudantdemo.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/books")
@Validated
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    public ResponseEntity<Book> create(@Valid @RequestBody BookRequest request) {
        Book created = bookService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<Book> list(@RequestParam(defaultValue = "25") @Min(1) @Max(200) int limit) {
        return bookService.list(limit);
    }

    @GetMapping("/search")
    public List<Book> searchByAuthor(@RequestParam @NotBlank String author,
                                     @RequestParam(defaultValue = "25") @Min(1) @Max(200) int limit) {
        return bookService.findByAuthor(author, limit);
    }

    @GetMapping("/{id}")
    public Book get(@PathVariable String id) {
        return bookService.get(id);
    }

    /** The current revision is passed as {@code rev}, as CouchDB and Cloudant require. */
    @PutMapping("/{id}")
    public Book update(@PathVariable String id,
                       @RequestParam @NotBlank String rev,
                       @Valid @RequestBody BookRequest request) {
        return bookService.update(id, rev, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestParam @NotBlank String rev) {
        bookService.delete(id, rev);
        return ResponseEntity.noContent().build();
    }
}
