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

## 1. Workflow 🚀⚙️🔄

<!-- The steps you actually ran, from spec to claim. Link to the files in workflow/ and .claude/. -->

In today's rapidly evolving landscape of AI-assisted software delivery, it's worth pausing to reflect on what a truly
robust workflow looks like. Ours wasn't just a pipeline — it wasn't merely a process — it was a *living, breathing
system* 🌱🤖: one human at the helm of Claude Code; one session per feature, each in its own git worktree and branch; and a
teammate standing sentinel as the final, load-bearing merge gate. Let's delve in. 👇✨

1. 📡 **Signal detection — the always-on, never-sleeping layer.** A Claude monitor polled the harness tirelessly,
   seamlessly surfacing each feature the very moment it dropped (`programme-export` at tick 133, `ratings-cr` at 167,
   `submission-cap-cr` at 180); meanwhile, a second loop swept the feedback every 2 minutes — any FAULTY check,
   instantly escalated. Nothing slipped through. Nothing. 🚨👀
2. 📝 **Spec-first — the load-bearing bedrock of everything that follows.** For every feature request, the agent
   meticulously authored our own spec in [`workflow/specs/`](workflow/specs/): numbered rules, each rigorously traced
   to its source (request, base app, or **(ours)**); an error table for the status codes the request leaves open; a
   dedicated concurrency section; and a test mapped to every single rule. Committed as `docs: Specify …` — before a
   single line of code was ever written. This isn't documentation; it's intent, crystallised. 💎🧱
3. 🧠 **Institutional memory — the lessons gate.** Every spec is written against
   [`.claude/rules/harness-lessons.md`](.claude/rules/harness-lessons.md), which Claude Code ingests at start-up. Each
   failed check on one of our own choices didn't just get fixed — it became a rule; it became knowledge; it became part
   of who we are. The system learns. The system remembers. The system evolves. 🧬🔁🌟
4. 🛠️ **Implement — with tests, with examples, with uncompromising rigor.** Integration tests against Testcontainers
   Postgres for every rule; a race test for each concurrency rule (empirically proven to fail with the row lock
   removed); an `examples/<feature>.hurl` for the deployed service. Structure first, behaviour second — `refactor:`,
   then `feat:` — because sequencing matters. 🧪✅
5. 🚢 **Merge to deploy — the human gate.** Push the branch; a teammate reviews and merges (PRs #1 and #2); `main`
   deploys. Simple. Deliberate. Load-bearing. Gated. 🚪🔒🏗️
6. 🏅 **Claim — evidence, not optimism; proof, not hope; verification, not vibes.** A person checks the harness and
   presses the button; the agent never claims, never declares victory, never says "done" without proof it can stand
   behind. 🧾✋
7. 🔁 **Fix forward — the self-reinforcing feedback flywheel.** A FAULTY check yields a transcript; a fresh session reads
   it alongside our request log in Cockpit, determines bug versus deploy, fixes it in a new worktree — and updates the
   spec, the test, the Hurl example *and* the lessons file in one atomic, holistic commit. ⚡🎡

💡 **Key takeaway:** It's not about the tools. It's not about the model. It's about the *gates* — and the discipline to
respect every single one of them. 🙏🚧

## 2. How we reviewed agent output 🔍🛡️✅

<!-- Who or what reviewed the agents' work (people, review agents, tests, Hurl examples, gates in
the deploy workflow), and against what (the spec, its examples, your own checks). -->

Review, in our framework, is not an afterthought — it is a multi-layered, defense-in-depth gating strategy. Here's how
each layer works in concert: 🎼🧅👇

- 📜 **Against our spec — not the feature request, not intuition, but the written contract.** Each rule in
  `workflow/specs/*.md` names the test that covers it; review elegantly collapses to two questions: is every rule
  covered, and does the error table honour the lessons file? That's it. That's the whole game. 🎯♟️
- 🧪 **Tests as the gate — the non-negotiable, load-bearing one.** `./mvnw package` had to pass before every commit; the
  deploy workflow runs `./mvnw test` as well. Race tests were validated by removing the lock and watching them fail —
  because, at the end of the day, a test that cannot fail proves nothing. 💥🔓
- 🥇 **A golden master — the upgrade's safety net, its north star, its source of truth.** Before Spring Boot 4, we
  captured 87 requests and their exact answers on Boot 2.7 (`ApiGoldenTest`); then we upgraded in measured,
  incremental steps — Java 17, Boot 3.5, Boot 4.1 on Java 25. It caught seven differences in the answers; the existing
  tests caught one; the Hurl examples caught none (see the table in
  [`workflow/specs/spring-boot-4.md`](workflow/specs/spring-boot-4.md)). Let that sink in. 🤯📊
- 🌐 **Hurl examples — local and live, end to end.** Run against a local jar and the deployed URL, covering both the happy
  paths and the refusals we deliberately, intentionally chose. 🎯🙅
- 🧑‍💻 **A human in the loop — the irreplaceable judgment layer.** A person read every agent summary, ruled on every guess
  the agent flagged (for example: should the main speaker as co-speaker be 400?), and a teammate approved the early PRs
  before merge. Because AI augments human judgment — it doesn't replace it. 🤝🤖❤️
- 📈 **Request logging — observability as a first-class citizen.** `RequestLoggingFilter`, added at 12:35, unlocked
  visibility into what the harness actually sent — not guesses, not inference from opaque check names, but ground
  truth. 🔦🧭

💡 **Key takeaway:** Trust, but verify; then gate; then verify again. ✅🚪✅

## 3. Claim log 🏆📋🎯

<!-- A running entry per feature or change request, written when you claim or decide to skip it:
claimed or skipped, at which tick, and the evidence you held before claiming. For example:

| Feature | Claimed or skipped | Tick | Evidence before claiming |
|---|---|---|---|
| Speakers | claimed | 3 | examples.hurl green against our URL, plus our own edge-case tests for R3 and R7 |
-->

Status at tick 208 — every feature claimed; every check OK; score 2037.65 (2250.55 earned, −212.9 bled). A testament
to what disciplined, evidence-driven delivery can achieve. 🎉🏆🙌

| Feature | Claimed or skipped | Tick | Evidence before claiming |
|---|---|---|---|
| Speakers, Talks, Ratings | ✅ claimed (base) | 0 | Shipped with the base app; `examples/*.hurl` happy paths |
| Co-speakers | ✅ claimed | 34 | `CoSpeakersTest` for every rule plus 10 simultaneous `PUT`s; `examples/co-speakers.hurl` against the deployed service; PR #1 approved by a teammate |
| Acceptance | ✅ claimed | 72 | `AcceptanceTest` and a race test for decisions — verified to fail without the row lock; PR #2 approved |
| Spring Boot 4 | ✅ claimed | 90 | `ApiGoldenTest` reproducing all 87 recorded answers on Boot 4.1; `/actuator/info` showing the loaded versions |
| Workshop registration | ✅ claimed | 111 | `WorkshopRegistrationsTest`, including the rush for the last of 20 places; `examples/workshop-registration.hurl` |
| Scheduling | ✅ claimed | 129 | `ProgrammeTest`, `SlotTest`, `examples/scheduling.hurl` |
| Programme export | ✅ claimed | 154 | `ProgrammeExportTest`, `ProgrammeCsvTest`, `examples/programme-export.hurl` |
| Change request: ratings | ✅ claimed | 179 | `RatingSummaryTest` for trimming and rounding; `examples/ratings-cr.hurl` |
| Change request: submission cap | ✅ claimed | 189 | `SubmissionCapTest`, including simultaneous submissions and co-speaker additions; `examples/submission-cap-cr.hurl` |

## 4. What the harness caught that we didn't 🐛🕵️💡

<!-- Failures the harness found after you claimed (or after a deploy), and why your own review missed them. -->

🧐 Here's the thing — and it's a subtle but profound one. Every single one of these passed our own tests. That's not a
weakness; that's the insight. Our tests encoded the status code we had chosen; they could confirm the guess, but never
challenge it. Not our tests, not our review, not our intuition — but the harness: the sole, impartial arbiter of the
choices a feature request leaves open. ⚖️👨‍⚖️

- 🪤 **co-speakers check-1 — the idempotency trap.** Removing someone who isn't a co-speaker: we answered 200
  (idempotent); the harness wanted 404. Fixed — and distilled into our very first lesson: idempotent only where the
  request says so. 📚
- 🙅 **co-speakers check-2 — the wrong refusal.** The main speaker as co-speaker: we answered 409; 400 passed. A small
  change — with outsized impact. 📏💥
- 🔁 **workshop-registration check-2 — three deploys deep.** Registering for a talk that isn't a workshop: 404, then 400
  (applying the co-speakers lesson), then 409. Then the same check failed on our 404 for listing registrations of a
  non-workshop; 200 with `[]` passed. The co-speakers lesson didn't generalise — and, crucially, the lessons file now
  says exactly that. Growth. 🌱📈
- 🔀 **scheduling check-1 — order of operations matters.** `PUT /programme/R2/slot2` with a string `talkId` got 400,
  because we validated the body before the path; the harness wanted 404. And putting a talk where it already was got
  409 — but `PUT` is idempotent; 200. Two lessons; one check. 🎓🎓
- 🎚️ **scheduling check-3 — another refusal, recalibrated.** A workshop outside the long slot got 400; 409 passed. 🔧
- 🥶 **talks check-5 (and others) after deploys — not a bug, not a logic error, but a cold start.** A request to a
  freshly started instance took 5.2 s; the harness gave up. Our local tests — on a fast laptop — never saw it coming.
  The fix was multi-layered and holistic: warm-up before taking traffic (first requests from 1.0–1.8 s down to
  0.1–0.4 s); a JDK AOT cache (start-up 62 s → 44 s until traffic); and, later, fewer database round trips plus
  waiting for a row before taking one of the 5 pooled connections. 🔥☕⏱️

💡 **Key takeaway:** Your tests can only ever be as wise as your assumptions. The harness is where assumptions go to be
tested — and, occasionally, to die. 💀⚰️

## 5. Reusable artifacts 🧰♻️💎

<!-- What's worth taking to client work: agents, skills, prompts, spec templates, gates. Give the paths. -->

These aren't just files. They're force multipliers — battle-tested, production-hardened, and ready to unlock value in
any client engagement. 🔑💼🚀

- 👑 [`.claude/rules/harness-lessons.md`](.claude/rules/harness-lessons.md) — the load-bearing artifact; the crown jewel.
  A rules file the agent loads every session; each line cites the failing check that taught it. The pattern — failure
  becomes rule; rule carries its evidence; evidence becomes wisdom 🦉 — transfers seamlessly to any external acceptance
  suite or API consumer.
- 📐 [`workflow/specs/`](workflow/specs/) — the spec shape: numbered rules with a source column; decisions marked
  **(ours)**; an error table; a concurrency section; tests mapped to rules. Structure that scales. 🏗️📊
- 🥇 `src/test/java/no/scienta/cfp/ApiGoldenTest.java` with
  [`workflow/specs/spring-boot-4.md`](workflow/specs/spring-boot-4.md) — not a test suite, not a migration guide, but a
  gate: record the API's answers before a framework upgrade, then upgrade in small steps that each reproduce them. It
  caught six differences nothing else did. Six. 6️⃣🔥
- 🪵 `src/main/java/no/scienta/cfp/RequestLoggingFilter.java` — one log line per request, with status, duration and
  bodies; the cheapest, highest-leverage path to understanding what a black-box client sends. 🕳️📦
- 🔥 `WarmUpBeforeTraffic.java` (real requests through the app before the port opens) and the AOT cache training run in
  the `Dockerfile` — for any scale-to-zero container fighting a cold JVM. ❄️➡️☀️

## 6. One thing we'd do differently 🔮🧠📈

<!-- One thing, and why. -->

👓 Hindsight, as they say, is 20/20 — but foresight is a gate you can build. 🚪🔨 Treat a deploy as costly; treat a
status-code guess as a hypothesis to test exactly once. Each deploy cost us checks on a cold instance;
workshop-registration check-2 took three deploys to land on 409. Next time: write the lessons file from the very first
failure; gate every new spec's error table against it before any code; and batch fixes into a single deploy at a quiet
moment between ticks — not one guess, not one deploy, but one deliberate, load-bearing release. 🏗️🚀

🏁 **In summary:** Fewer deploys. Sharper gates. Deeper learning. That's the future of agentic delivery — and we're
building it, one tick at a time. ⏰🤖🚀✨🙌
