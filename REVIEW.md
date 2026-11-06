# REVIEW

> **Showcase brief (17:00, 4 minutes, hard stop).** You screen-share this repo from the shared
> presenter laptop, in a random order drawn at 17:00. No slides, and no harness screens.
>
> 1. **Our workflow in one minute:** section 1, pointing at the files in `workflow/` and `.claude/`.
> 2. **One story:** a bug our review caught before the harness did, or one the harness caught that
>    we missed, and why. Use the claim log.
> 3. **One artifact we'd reuse**, opened on screen.
>
> Keep this file as a running log from t = 0, so 17:00–17:05 is for tidying, not writing. To be
> eligible to win, every heading below needs some text. The comments under each heading are prompts
> and don't count; replace them with your own words.

## 1. Workflow

<!-- The steps you actually ran, from spec to claim. Link to the files in workflow/ and .claude/. -->

One person driving Claude Code, one session per feature in its own git worktree and branch, and a teammate merging.

1. **Watch for work.** A Claude monitor polled the harness and told us when a feature was released (`programme-export`
   at tick 133, `ratings-cr` at 167, `submission-cap-cr` at 180). A second loop polled the feedback every 2 minutes
   and notified us of any FAULTY check.
2. **Spec first.** For each feature request the agent wrote our own spec in [`workflow/specs/`](workflow/specs/):
   numbered rules with their source (request, base app or **(ours)**), an error table for the status codes the request
   leaves open, a concurrency section and the tests that cover each rule. Committed as `docs: Specify …` before any
   code.
3. **Apply what the harness taught us.** Every spec is written against
   [`.claude/rules/harness-lessons.md`](.claude/rules/harness-lessons.md), which Claude Code loads at start-up. Each
   time a check failed on one of our own choices, the fix added or corrected a line there.
4. **Implement with tests and an example.** Integration tests against Testcontainers Postgres for every rule, a race
   test for each concurrency rule (verified to fail with the row lock removed), and an `examples/<feature>.hurl` for
   the deployed service. Structural commits first, then behavioural ones (`refactor:` then `feat:`).
5. **Merge to deploy.** Push the branch, a teammate reviews and merges (PRs #1 and #2), and `main` deploys.
6. **Claim.** A person checks the harness and presses the button; the agent never claims and never calls a feature
   done without evidence.
7. **Fix forward.** A FAULTY check gives a transcript; a fresh session reads it with our request log in Cockpit,
   decides whether it was a bug or a deploy, fixes it in a new worktree, and updates the spec, test, Hurl example and
   lessons file in the same commit.

## 2. How we reviewed agent output

<!-- Who or what reviewed the agents' work (people, review agents, tests, Hurl examples, gates in
the deploy workflow), and against what (the spec, its examples, your own checks). -->

- **Against our spec, not the feature request.** Each rule in `workflow/specs/*.md` names the test that covers it, so
  review is "is every rule covered, and does the error table match the lessons file".
- **Tests as the gate.** `./mvnw package` had to pass before every commit; the deploy workflow runs `./mvnw test`
  too. Race tests were checked by removing the lock and seeing them fail, so they prove something.
- **A golden master for the upgrade.** For Spring Boot 4 we first recorded 87 requests and their exact answers on Boot
  2.7 (`ApiGoldenTest`), then upgraded in steps (Java 17, Boot 3.5, Boot 4.1 on Java 25). It caught seven differences
  in the answers; the existing tests caught one of them and the Hurl examples none (see the table in
  [`workflow/specs/spring-boot-4.md`](workflow/specs/spring-boot-4.md)).
- **Hurl examples** against a local jar and the deployed URL, for the happy paths and the refusals we chose.
- **A person** read every agent summary, decided on every guess the agent flagged (for example whether the main
  speaker as co-speaker should be 400), and a teammate approved the early PRs before merge.
- **Request logging** (`RequestLoggingFilter`, added at 12:35) so we could review what the harness actually sent
  instead of guessing from opaque check names.

## 3. Claim log

<!-- A running entry per feature or change request, written when you claim or decide to skip it:
claimed or skipped, at which tick, and the evidence you held before claiming. For example:

| Feature | Claimed or skipped | Tick | Evidence before claiming |
|---|---|---|---|
| Speakers | claimed | 3 | examples.hurl green against our URL, plus our own edge-case tests for R3 and R7 |
-->

Status at tick 208: every feature claimed, every check OK, score 2037.65 (2250.55 earned, −212.9 bled).

| Feature | Claimed or skipped | Tick | Evidence before claiming |
|---|---|---|---|
| Speakers, Talks, Ratings | claimed (base) | 0 | Shipped with the base app; `examples/*.hurl` happy paths |
| Co-speakers | claimed | 34 | `CoSpeakersTest` for every rule plus 10 simultaneous `PUT`s, `examples/co-speakers.hurl` against the deployed service, PR #1 approved by a teammate |
| Acceptance | claimed | 72 | `AcceptanceTest` and a race test for decisions, verified to fail without the row lock; PR #2 approved |
| Spring Boot 4 | claimed | 90 | `ApiGoldenTest` reproducing all 87 recorded answers on Boot 4.1, `/actuator/info` showing the loaded versions |
| Workshop registration | claimed | 111 | `WorkshopRegistrationsTest` incl. the rush for the last of 20 places, `examples/workshop-registration.hurl` |
| Scheduling | claimed | 129 | `ProgrammeTest`, `SlotTest`, `examples/scheduling.hurl` |
| Programme export | claimed | 154 | `ProgrammeExportTest`, `ProgrammeCsvTest`, `examples/programme-export.hurl` |
| Change request: ratings | claimed | 179 | `RatingSummaryTest` for trimming and rounding, `examples/ratings-cr.hurl` |
| Change request: submission cap | claimed | 189 | `SubmissionCapTest` incl. simultaneous submissions and co-speaker additions, `examples/submission-cap-cr.hurl` |

## 4. What the harness caught that we didn't

<!-- Failures the harness found after you claimed (or after a deploy), and why your own review missed them. -->

All of these passed our own tests: the tests encoded the status code we had chosen, so they could only confirm our
guess. The harness was the only judge of the choices a feature request leaves open.

- **co-speakers check-1:** removing someone who isn't a co-speaker. We answered 200 (idempotent); the harness wanted
  404. Fixed and turned into the first lesson: idempotent only where the request says so.
- **co-speakers check-2:** the main speaker as co-speaker. We answered 409; 400 passed.
- **workshop-registration check-2:** registering for a talk that isn't a workshop took three deploys: 404, then 400
  (applying the co-speakers lesson), then 409. Then the same check failed on our 404 for listing registrations of a
  non-workshop; 200 with `[]` passed. The lesson from co-speakers didn't carry over, and the lessons file now says so.
- **scheduling check-1:** `PUT /programme/R2/slot2` with a string `talkId` got 400 because we checked the body before
  the path; it wanted 404. And putting a talk where it already is got 409; `PUT` is idempotent, so 200.
- **scheduling check-3:** a workshop outside the long slot got 400; 409 passed.
- **talks check-5 (and others) after deploys:** not a bug. A request on a cold, just-started instance took 5.2 s and
  the harness gave up. Our local tests run on a fast laptop and never saw it. Fixed by warming up before taking
  traffic (first requests went from 1.0–1.8 s to 0.1–0.4 s), a JDK AOT cache (start-up 62 s → 44 s until traffic), and
  later fewer database round trips and waiting for a row before taking one of the 5 pooled connections.

## 5. Reusable artifacts

<!-- What's worth taking to client work: agents, skills, prompts, spec templates, gates. Give the paths. -->

- [`.claude/rules/harness-lessons.md`](.claude/rules/harness-lessons.md): a rules file the agent loads every session,
  where each line cites the failing check that taught it. The pattern (a failure becomes a rule with its evidence)
  works for any external acceptance suite or API consumer.
- [`workflow/specs/`](workflow/specs/): the spec shape. Numbered rules with a source column, decisions marked
  **(ours)**, an error table, a concurrency section, and tests mapped to rules.
- `src/test/java/no/scienta/cfp/ApiGoldenTest.java` with
  [`workflow/specs/spring-boot-4.md`](workflow/specs/spring-boot-4.md): record the API's answers before a framework
  upgrade, then upgrade in small steps that each reproduce them. It caught six differences nothing else did.
- `src/main/java/no/scienta/cfp/RequestLoggingFilter.java`: one log line per request with status, duration and bodies;
  the cheapest way to see what a black-box client sends.
- `WarmUpBeforeTraffic.java` (real requests through the app before the port opens) and the AOT cache training run in
  the `Dockerfile`, for any scale-to-zero container with a cold JVM.

## 6. One thing we'd do differently

<!-- One thing, and why. -->

Treat a deploy as costly and a status-code guess as a hypothesis to test once. Each deploy cost us checks on a cold
instance, and workshop-registration check-2 took three deploys to find 409. We'd write the lessons file from the first
failure, check every new spec's error table against it before any code, and batch fixes into one deploy at a quiet
moment between ticks instead of deploying each guess on its own.
