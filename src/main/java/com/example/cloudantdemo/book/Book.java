package com.example.cloudantdemo.book;

/**
 * API representation of a book. {@code id} and {@code rev} are the Cloudant document
 * identifiers; {@code rev} must be supplied when updating or deleting.
 */
public record Book(
        String id,
        String rev,
        String title,
        String author,
        Integer publishedYear
) {
}
