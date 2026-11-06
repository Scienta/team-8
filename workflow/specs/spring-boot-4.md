# Spring Boot 4 upgrade: our spec and plan

Source: the harness feature request `spring-boot-4` (version 1, V: 3). It asks for Spring Boot 4 on a supported Java,
`GET /actuator/info` telling the truth about the running versions, and **nothing else changing for users**: the
Speakers, Talk submissions and Ratings specs keep working exactly, formats included.

## Target

Spring Boot 4.1.2 (latest GA on 2026-11-06), Java 25 (LTS) in the `Dockerfile` for both build and runtime.

## Known traps

| Trap | Why it matters | Guard |
|---|---|---|
| Java 20+ puts a narrow no-break space before `AM`/`PM` in `DateFormat` US short | `ratedAtDisplay` must have an ordinary space (Ratings spec) | Pinned pattern in `Utils.usShort`, `UtilsTest` |
| Spring 6+ no longer matches a trailing slash by default | `/speakers/` and `/speakers/{id}/` must keep working (Speakers spec) | `ApiGoldenTest` |
| `javax.*` became `jakarta.*` (Boot 3) | Compilation | the build |
| Jackson 3 (Boot 4): new packages, other defaults for dates and number coercion | `submittedAt` with milliseconds and `+00:00`, `submittedOn`, how odd bodies are read | `ApiGoldenTest` |
| Renamed starters and properties (Boot 4) | e.g. `spring.jackson.parser.allow-comments`, `spring-boot-starter-web` | `ApiGoldenTest`, start-up log |
| Testcontainers 2 (new artifact names) | the tests | the build |
| `/actuator/info` contributor API | must report the real versions, never hard-coded | `ApiGoldenTest` checks it against `SpringBootVersion` |

## Plan

Every step compiles, passes all tests and could be deployed on its own.

1. Pin the reviewer UI format so it doesn't depend on the JDK.
2. Golden-master test (`ApiGoldenTest`): a fixed script of requests, including odd ones, whose statuses and normalised
   bodies are recorded on Boot 2.7 (run on the local JDK 25) and compared on every later step. Differences are
   reviewed one by one; only those we accept are re-recorded.
3. Java 17 on Boot 2.7.
4. Boot 3.5 (jakarta, trailing slash).
5. Boot 4.1 and Java 25 (Jackson 3, Testcontainers 2, renamed starters and properties).
6. Deploy, run `examples/run.sh` against the service, compare `/actuator/info`, then a person claims.

## What the golden master caught

Every row was a failing build or test before it was fixed. Only the first row was also caught by the existing tests;
the rest were caught only by `ApiGoldenTest`, and none by the Hurl examples.

| Step | Difference | Fix |
|---|---|---|
| Boot 3.5 | 500 on every path variable: parameter names missing | `<parameters>true</parameters>` in the compiler plugin |
| Boot 3.5 | Framework wording of error messages | Accepted, re-recorded |
| Boot 4.1 | `submittedAt` written with `Z`, `submittedOn` as a full timestamp | Formatted by `Utils` before the upgrade |
| Boot 4.1 | JSON fields sorted alphabetically | `spring.jackson.mapper.sort-properties-alphabetically=false` |
| Boot 4.1 | JSON with comments refused (400) | `spring.jackson.json.read.allow-java-comments` |
| Boot 4.1 | Error bodies without `message` | `spring.web.error.include-message`, `include-binding-errors` |
| Boot 4.1 | Error `timestamp` with `Z`, no `charset` on error responses | `LegacyErrorResponses` |

