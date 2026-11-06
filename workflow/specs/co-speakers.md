# Co-speakers: our spec

Source: the harness feature request `co-speakers` (version 1). The request gives the business rules and the API
surface. This file adds what it leaves open: status codes, edge cases and concurrency. Decisions marked **(ours)** are
the team's, not the product owner's.

## Rules

| # | Rule | Source |
|---|---|---|
| R1 | A talk keeps its one main speaker (`speakerId`). | request |
| R2 | `PUT /talks/{id}/co-speakers/{speakerId}` adds a registered speaker as a co-speaker and returns the talk, 200. | request |
| R3 | `DELETE /talks/{id}/co-speakers/{speakerId}` removes a co-speaker and returns the talk, 200. | request |
| R4 | At most two co-speakers per talk. | request |
| R5 | The main speaker can't be a co-speaker of their own talk. | request |
| R6 | Every talk (in every response that returns talks) has `coSpeakerIds`: the ids in the order they were added, `[]` when there are none. | request |
| R7 | Adding someone who is already a co-speaker changes nothing: 200, same order. This holds even when the talk already has two. | request |
| R8 | `GET /speakers/{id}/talks` and `GET /talks?speakerId=` still list only talks where the speaker is the main speaker. | request |
| R9 | Co-speaking doesn't count towards the limit of 3 talks, and a speaker at the limit can still co-speak. | request |
| R10 | Removed and then re-added goes to the end of the order. | **(ours)** |
| R11 | Removing someone who is a registered speaker but not a co-speaker of the talk (including removing the same one twice) changes nothing and answers 404. The harness rejected our first choice, 200 (idempotent like R7). | **(ours)** |
| R12 | The limit of two holds under concurrent `PUT`s for the same talk: the talk row is locked (`SELECT … FOR UPDATE`) while adding. | **(ours)** |

## Errors **(ours)**

Checked in this order; the first that applies wins.

| Case | Status |
|---|---|
| `id` or `speakerId` not an integer | 400 |
| Talk doesn't exist | 404 |
| Speaker doesn't exist | 404 |
| `DELETE` of a speaker who isn't a co-speaker of the talk (R11) | 404 |
| `PUT` with the main speaker (R5): a request that can never succeed, not a conflict with the talk's state. The harness rejected our first choice, 409 (co-speakers check-2) | 400 |
| `PUT` of a new co-speaker when the talk has two (R4) | 409 |

409 for a full talk matches what the base app answers when the limit of 3 talks is reached.

## Tests

- `CoSpeakersTest`: every rule and error above, plus ten simultaneous `PUT`s to one talk (R12). Verified to fail
  when the row lock is removed.
- `examples/co-speakers.hurl`: the happy path and the 404/409 cases, for running against the deployed service.
