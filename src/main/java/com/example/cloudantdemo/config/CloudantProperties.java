package com.example.cloudantdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings bound from the {@code cloudant.*} namespace.
 *
 * <p>Use {@code apiKey} for IBM Cloudant (IAM). Use {@code username}/{@code password}
 * for Apache CouchDB. If neither is set, requests are sent without authentication.
 */
@ConfigurationProperties(prefix = "cloudant")
public record CloudantProperties(
        String url,
        String apiKey,
        String username,
        String password,
        String database
) {
}
