# Nordia Tasks: synchronisation service (track B)

Spring Boot implementation of the REST synchronisation service from the case study brief
("Nordia Tasks", track B). It follows the contract of the provided `README.md` and `server.js`,
without the simulated failures and latency.

* Java 17, Spring Boot 4.1.2, Maven (wrapper included), Spring JDBC, H2 in file mode
* 50 automated tests, with no sleeps and no reliance on timing or random failures

## Run it

Requirement: JDK 17 or later. Nothing else to install (the Maven wrapper is included and H2 is embedded).

```bash
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

The service listens on <http://localhost:8080>. At the very first start it creates `./data/nordia.mv.db`
and loads `members.json` and `tasks.json` (6 members, 45 tasks). Later starts keep the data.
Delete the `data/` folder to start again from the seed.

## Test it

```bash
./mvnw test                     # Windows: .\mvnw.cmd test
```

Expected result: `Tests run: 50, Failures: 0, Errors: 0`. Every test uses its own in-memory database
(or a temporary H2 file for the restart test), so nothing is written to `data/`.

## Try it by hand

Any REST client works (Postman, curl). With curl:

```bash
# Create. The id is chosen by the client, the key makes the call replayable.
curl -i -X POST http://localhost:8080/tasks -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-create-001" \
  -d '{"id":"3f2b8c1e-5d4a-4e7b-9c1a-0b2d3e4f5a6b","title":"Test"}'
# Send the exact same command again: same answer, plus the header "Idempotent-Replay: true".

# Change it, stating the version you started from.
curl -i -X PATCH http://localhost:8080/tasks/3f2b8c1e-5d4a-4e7b-9c1a-0b2d3e4f5a6b \
  -H "Content-Type: application/json" -H "Idempotency-Key: demo-patch-001" \
  -d '{"baseVersion":1,"title":"Changed"}'

# Read what changed since a cursor (45 seeded changes, so "c45" is the end of the seed).
curl "http://localhost:8080/tasks?since=c45&limit=20"
```

(On Windows PowerShell, use `curl.exe` or Postman: PowerShell handles quotes in JSON differently.)

## API

| Route | Description |
|---|---|
| `GET /members` | the six members |
| `GET /tasks?since=&limit=` | tasks changed after the cursor, oldest change first, tombstones included. `limit` 1 to 50 (default 20). Answer: `{ items, nextCursor, hasMore }` |
| `GET /tasks/{id}` | one task (a deleted task is returned with `deleted: true`) |
| `POST /tasks` | create (`id` UUID chosen by the client, `title`, optional `note`, `status`, `assigneeId`, `dueDate`). `201`, version 1 |
| `PATCH /tasks/{id}` | change. Body: `baseVersion` and the fields to change. `null` clears `note`, `assigneeId`, `dueDate` |
| `DELETE /tasks/{id}?baseVersion=n` | delete (tombstone, version + 1) |

Writes need the header `Idempotency-Key` (8 to 64 characters).

Error bodies are `{ "code", "error" }`, plus `details` (422) or `current` (409, 410, the task as it is now).

| Status | `code` | When |
|---|---|---|
| 400 | `missing_idempotency_key`, `invalid_json`, `invalid_cursor` | malformed request |
| 404 | `not_found` | unknown task or route |
| 405 | `method_not_allowed` | wrong method |
| 409 | `version_conflict` | `baseVersion` is not the current version |
| 409 | `id_exists` | creating an id that already exists |
| 410 | `task_deleted` | changing a deleted task |
| 422 | `validation_error` | invalid or unknown field, nothing to change, assignee not a member |
| 422 | `invalid_transition` | a `done` task moved back to `todo` (`doing` is allowed) |
| 422 | `idempotency_key_reuse` | same key, different request |
| 500 | `internal_error` | unexpected error (logged) |

Order of checks for a PATCH, following `server.js`: key (400), JSON (400), replay or key reuse,
validation (422), unknown task (404), deleted task (410), outdated version (409), `done` to `todo` (422), apply.
A repeated DELETE on a deleted task answers 200 and changes nothing, whatever the version.

## Design choices

**One writer at a time, enforced by the database.** Every write is one transaction that starts with
`SELECT ... FOR UPDATE` on the single row of `sync_state`. The change number is allocated under that lock,
so the order of commits is the order of change numbers and a reader following the cursor can never skip a change.
The version check is also a compare-and-set (`UPDATE ... WHERE id = ? AND version = ?`), kept as a second
line of defence. The cost is throughput: writes are serialised. `LOCK_TIMEOUT=10000` lets waiting writers
queue for up to 10 seconds instead of failing after H2's short default.

**Idempotency stored in the database.** The `idempotency` table keeps, for each key, a fingerprint of the
request (SHA-256 of the method, the URL with its query and the JSON body with sorted keys), the HTTP status and
the body of the answer. A known key with the same fingerprint replays the stored answer (`Idempotent-Replay: true`),
with a different fingerprint it gives 422. The answer is final: 2xx and 4xx answers are stored, malformed
requests (400) are not. An unexpected error rolls the transaction back, nothing is stored, and the client can
retry with the same key. Keys are kept forever (purging is not implemented). Because the key is checked inside
the write lock, the same key sent twice at the same time is applied once.

**Cursor.** The cursor is the change number of the last task returned (`c<n>`, opaque for clients). It is not
`updatedAt`, which can be equal for two changes. A task changed again gets a new change number and is delivered again.
One extra row is read to compute `hasMore`.

**Business errors are values, not exceptions.** Services return a `Result` (status and JSON body). An exception
would roll the transaction back and the answer to be remembered under the key would be lost.

**Body read as raw JSON.** It lets the service tell an absent field from a field set to `null`, reject unknown
fields and wrong types, and answer bad JSON in the common error format.

**Seed data.** Loaded once, in one transaction, only if the database is empty. Versions, dates and order are kept.
One seed task has the assignee `m-99`, who is not a member: it is loaded as-is (a warning is logged), the
`assignee_id` column has no foreign key, and members are validated only on writes.

**Dates.** `updatedAt` is UTC with exactly three decimals (`Instant.toString()` drops them when they are zero).
`dueDate` is a plain calendar date.

## Tests

| Class | Tests | What it checks |
|---|---|---|
| `IdempotencyTest` | 6 | replay of PATCH, POST, after later changes; key reuse; stored errors; key order |
| `ConcurrencyTest` | 2 | 8 simultaneous PATCH with the same `baseVersion` (exactly one wins); the same key sent 8 times at once (applied once) |
| `PaginationTest` | 1 | deletions between two pages: nothing skipped, no duplicate, tombstones delivered, stable cursor |
| `RestartTest` | 2 | real H2 file, two starts: seed not loaded again, keys and change counter survive |
| `WriteApiTest` | 16 | create, patch, delete, rules, validation, 404/409/410/422 |
| `ReadApiTest` | 17 | members, pagination, cursor and limit errors, tombstones, UTF-8, error format |
| `SeedLoaderTest`, `TimestampsTest`, `TasksApplicationTests` | 6 | seed (including `m-99`), date format, context |

The concurrency tests assert invariants that do not depend on the order in which requests arrive
(for example "exactly one 200 and seven 409"), so they do not depend on timing. The expected values of the read
tests were taken from the provided mock server running without failures.
HTTP tests use `MockMvc` (controllers, service and database are real, Tomcat is not).

## Project layout

```
src/main/java/com/nordia/tasks/
  api/         TaskController, MemberController, ApiExceptionHandler, Responses
  service/     TaskService (reads, write flow), TaskValidator, Fingerprint
  repository/  TaskRepository, MemberRepository, SyncStateRepository, IdempotencyRepository
  model/       Task, Member, TaskPage, Result
  seed/        SeedLoader
  util/        Json, Timestamps
src/main/resources/  application.properties, schema.sql, seed/members.json, seed/tasks.json
src/test/java/...    tests listed above
```

## Limits and what I cut

* A single service instance and a single writer at a time.
* Not done: purging old idempotency keys, Dockerfile and CI workflow, `/health`, OpenAPI, structured logs.
* Not tested: a real HTTP server, a crash in the middle of a transaction, load, several instances, lock
  timeouts under heavy load.
* Known limitation: the seed is loaded right after the web server starts, so a request arriving in the first
  milliseconds of the very first start could see an empty database.
* Differences from the mock: no random failures, latency or `/_test` routes; unexpected errors answer
  `500 internal_error`; a cursor with more than 18 digits is refused as `invalid_cursor`.
* Members are read on each write to validate `assigneeId` (fine for six members).

## AI usage

Claude (Anthropic) was used throughout this work: analysis of the brief, architecture, first version of the
code, tests and this README. I ran, tested and committed everything myself. Details are in the submission form.
