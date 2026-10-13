package com.example.cloudantdemo.book;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BookRequest(
        @NotBlank String title,
        @NotBlank String author,
        @Min(1000) @Max(2100) Integer publishedYear
) {
}
