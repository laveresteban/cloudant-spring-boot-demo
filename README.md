# Cloudant Spring Boot Demo

A small REST API for managing books, built with Spring Boot 3.5 and the
[IBM Cloudant Java SDK](https://github.com/IBM/cloudant-java-sdk). Books are stored as JSON
documents, and the app works against IBM Cloudant or a local Apache CouchDB.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (only for the local CouchDB option)

## Run against local CouchDB

```bash
docker compose up -d                # CouchDB on http://localhost:5984 (admin / password)

export CLOUDANT_URL=http://localhost:5984
export CLOUDANT_USERNAME=admin
export CLOUDANT_PASSWORD=password

mvn spring-boot:run
```

If port 5984 is already in use, pick another host port:
`COUCHDB_PORT=5985 docker compose up -d`, then set `CLOUDANT_URL=http://localhost:5985`.

## Run against IBM Cloudant

1. Create a Cloudant instance on IBM Cloud and generate an IAM API key with read/write access.
2. Set the environment variables:

```bash
export CLOUDANT_URL=https://<your-account>.cloudantnosqldb.appdomain.cloud
export CLOUDANT_APIKEY=<your-iam-api-key>
mvn spring-boot:run
```

When `CLOUDANT_APIKEY` is set, it takes precedence over username and password.

### Configuration

| Variable           | Default                  | Purpose                                        |
|--------------------|--------------------------|------------------------------------------------|
| `CLOUDANT_URL`     | `http://localhost:5984`  | Service endpoint                               |
| `CLOUDANT_APIKEY`  | *(empty)*                | IAM API key (IBM Cloudant)                     |
| `CLOUDANT_USERNAME`| *(empty)*                | Basic auth user (CouchDB)                      |
| `CLOUDANT_PASSWORD`| *(empty)*                | Basic auth password (CouchDB)                  |
| `CLOUDANT_DB`      | `books`                  | Database name, created on startup if missing   |
| `SERVER_PORT`      | `8080`                   | HTTP port                                      |

If neither an API key nor a username is set, the client sends unauthenticated requests.

## API

| Method   | Path                                  | Description                                        |
|----------|---------------------------------------|----------------------------------------------------|
| `POST`   | `/api/books`                          | Create a book (`201 Created` with `Location`)     |
| `GET`    | `/api/books?limit=25`                 | List books (`limit` 1–200)                         |
| `GET`    | `/api/books/search?author=...&limit=25` | Find books by author (`limit` 1–200, default 25) |
| `GET`    | `/api/books/{id}`                     | Get one book                                       |
| `PUT`    | `/api/books/{id}?rev=...`             | Replace a book; `rev` must be the current revision |
| `DELETE` | `/api/books/{id}?rev=...`             | Delete a book (`204 No Content`)                   |

Errors use RFC 9457 problem details: `400` for invalid input, `404` for an unknown book,
`409` when `rev` is stale, and `502` for other Cloudant server errors.

### Example session

```bash
# Create
curl -i -X POST http://localhost:8080/api/books \
  -H 'Content-Type: application/json' \
  -d '{"title":"Dune","author":"Frank Herbert","publishedYear":1965}'
# => {"id":"5e39...","rev":"1-3706...","title":"Dune",...}

# Update (pass the current rev)
curl -X PUT "http://localhost:8080/api/books/<id>?rev=<rev>" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Dune","author":"Frank Herbert","publishedYear":1965}'

# Search
curl "http://localhost:8080/api/books/search?author=Frank%20Herbert"

# Delete (use the latest rev)
curl -X DELETE "http://localhost:8080/api/books/<id>?rev=<rev>"
```

## Tests

```bash
mvn test
```

| Test class                       | What it covers                                                      |
|----------------------------------|---------------------------------------------------------------------|
| `BookControllerTest`             | HTTP contract: status codes, validation, `Location` header, params  |
| `ApiExceptionHandlerTest`        | SDK exceptions mapped to 404, 409, 403 and 502                       |
| `BookServiceTest`                | Document mapping, Mango selector, database probe, options sent       |
| `CloudantConfigTest`             | Authenticator selection and property binding                        |
| `DatabaseInitializerTest`        | Database is ensured on startup                                      |
| `BookServiceIntegrationTest`     | Real CouchDB via Testcontainers: CRUD, revision conflicts, search    |

Only the integration test needs Docker. Without Docker it is skipped and the rest still run.

## Project layout

```
src/main/java/com/example/cloudantdemo/
├── CloudantDemoApplication.java     Entry point
├── config/
│   ├── CloudantConfig.java          Builds the Cloudant client and picks the authenticator
│   └── CloudantProperties.java      Binds the cloudant.* settings
├── book/
│   ├── Book.java                    API response model
│   ├── BookRequest.java            Validated request body
│   ├── BookService.java             Cloudant SDK calls (documents, _find)
│   ├── BookController.java          REST endpoints
│   └── DatabaseInitializer.java     Creates the database on startup
└── web/
    └── ApiExceptionHandler.java     Maps SDK exceptions to HTTP status codes
```

## Key SDK calls

| Operation       | SDK method                              |
|-----------------|-----------------------------------------|
| Create database | `putDatabase(PutDatabaseOptions)`       |
| Create document | `postDocument(PostDocumentOptions)`     |
| Read document   | `getDocument(GetDocumentOptions)`       |
| Replace         | `putDocument(PutDocumentOptions)`       |
| Delete          | `deleteDocument(DeleteDocumentOptions)` |
| List            | `postFind(PostFindOptions)`             |
| Query           | `postFind(PostFindOptions)`             |

The client is created with `new Cloudant(Cloudant.DEFAULT_SERVICE_NAME, authenticator)`
followed by `setServiceUrl(...)`. The SDK also offers `Cloudant.newInstance()`, which reads
the same settings from environment variables.

## Notes

- The Mango search in `findByAuthor` works without an index for small datasets. For larger
  ones, create a JSON index on `type` and `author` with `_index`, or Cloudant will warn about
  a full scan.
- The `type` field tags every document, so list and search return only books, even if the
  database holds other document types.
- On startup the app checks whether the database exists and creates it only if it is missing.
  A user with read/write access to an existing database does not need permission to create one.
