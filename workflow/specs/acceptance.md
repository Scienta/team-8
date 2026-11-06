# Acceptance state machine: our spec

Source: the harness feature request `acceptance` (version 1, V: 2). The request gives the business rules and the API
surface. This file adds what it leaves open: status codes, edge cases and concurrency. Decisions marked **(ours)** were
made by the team on 2026-11-06, not by the product owner.

## States

```
             ┌───────────── committee ─────────────┐
SUBMITTED ───┼──> WAITLISTED ──┬──> ACCEPTED (final for the committee)
             │                 └──> REJECTED (final)
             ├──> ACCEPTED
             └──> REJECTED

speaker: SUBMITTED, WAITLISTED, ACCEPTED ──> WITHDRAWN (final)
```

## Rules

| # | Rule | Source |
|---|---|---|
| R1 | `PUT /talks/{id}/status` with `{"status": …}` is a committee decision: `ACCEPTED`, `REJECTED` or `WAITLISTED`. It returns the talk, 200. | request |
| R2 | From `SUBMITTED` the committee may accept, reject or waitlist. From `WAITLISTED` it may accept or reject. | request |
| R3 | `ACCEPTED` and `REJECTED` are final for the committee; `WITHDRAWN` is final for everyone. | request |
| R4 | Accepting needs ratings from at least two different reviewers. Rejecting and waitlisting need none. | request |
| R5 | `POST /talks/{id}/withdraw` with `{"speakerId": …}` withdraws the talk and returns it, 200. Only the main speaker (`speakerId` of the talk) may; co-speakers may not. | request, **(ours)** for co-speakers |
| R6 | A talk can be withdrawn unless it is `REJECTED` or `WITHDRAWN`; an `ACCEPTED` talk can be withdrawn. | request |
| R7 | The current status shows wherever a talk is shown (`status` on every talk). | request |
| R8 | A decision or withdrawal that isn't allowed changes nothing. | request |
| R9 | A decision equal to the current status changes nothing and answers 200 with the talk (e.g. `WAITLISTED` again, or `ACCEPTED` again, which needs no new ratings check). | **(ours)** |
| R10 | Concurrent decisions and withdrawals on one talk are serialised by locking the talk row (`SELECT … FOR UPDATE`), so the outcome is always reachable by applying the requests one at a time. | request, **(ours)** for how |

## Errors **(ours)**

Checked in this order; the first that applies wins.

| Case | Status |
|---|---|
| `id` not an integer, body missing or not JSON | 400 |
| Decision `status` missing or not one of `ACCEPTED`, `REJECTED`, `WAITLISTED` (also `SUBMITTED`, `WITHDRAWN`) | 400 |
| Withdrawal `speakerId` missing or not an integer | 400 |
| Talk doesn't exist | 404 |
| Withdrawal by anyone but the main speaker, including unknown speaker ids | 403 |
| Decision or withdrawal the state machine doesn't allow (R2, R3, R6) | 409 |
| `ACCEPTED` with fewer than two reviewers (R4) | 409 |

## Tests

- `TalkStatusTest`: the transition table, as a pure function.
- `AcceptanceTest`: every rule and error above against the real app, plus simultaneous decisions and withdrawals on
  one talk (R10).
- `examples/acceptance.hurl`: the happy paths and the main refusals, for running against the deployed service.
