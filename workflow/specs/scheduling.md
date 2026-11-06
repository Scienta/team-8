# Programme scheduling: our spec

Source: the harness feature request `scheduling` (version 1, V: 3). The request gives the business rules and the API
surface. This file adds what it leaves open: status codes, edge cases and concurrency. Decisions marked **(ours)** were
made by the team on 2026-11-06, not by the product owner.

## Rules

| # | Rule | Source |
|---|---|---|
| R1 | The venue is fixed: rooms `R1`, `R2`, `R3` and slots `S1` 09:00–10:00, `S2` 10:15–11:15, `S3` 11:30–12:30, `S4` 13:30–15:30, `S5` 15:45–16:45, the same in every room. Times are `HH:mm` on a 24-hour clock. | request |
| R2 | `PUT /programme/{room}/{slot}` with `{"talkId": …}` puts the talk in that room and slot and returns the entry (`room`, `slot`, `start`, `end`, `talkId`), 200. | request |
| R3 | `GET /programme/{room}/{slot}` returns the entry, 200. | request |
| R4 | `DELETE /programme/{room}/{slot}` takes the talk out, 204 with no body. The talk can then go somewhere else. | request, **(ours)** for 204 |
| R5 | Only an `ACCEPTED` talk goes on the programme. | request |
| R6 | A workshop (format `WORKSHOP`) needs the long slot, `S4`. Every other format fits any slot, `S4` included. | request |
| R7 | One talk per room and slot. | request |
| R8 | A talk is on the programme at most once. To move it, take it out first: a `PUT` of a talk that is already on the programme elsewhere is refused. A `PUT` of a talk to the room and slot it is already in changes nothing and returns its entry, 200, even if the talk has changed since (R10). We first refused it with 409; scheduling check-1 failed on it. | request, **(ours)** for the same place |
| R9 | The people on talks in the same slot are all different: no main speaker or co-speaker of the new talk is a main speaker or co-speaker of a talk already in that slot, in any room. | request |
| R10 | The rules are checked when a talk is put on the programme, against the talks as they are then. A talk that changes afterwards (withdrawn, gets a co-speaker) stays where it is, and its entry still shows. | request |
| R11 | Concurrent `PUT`s never break R7–R9. Each `PUT` locks the talk's row, then the slot's row in `programme_slot` (`SELECT … FOR UPDATE`), always in that order, so two talks can't both pass the checks for one slot. A primary key on (room, slot) and a unique index on the talk back R7 and R8 up. | request, **(ours)** for how |

## Errors **(ours)**

Checked in this order; the first that applies wins. A talk put where it already is (R8) is 200 once the body and the
talk are known to be valid, before any 409.

| Case | Status |
|---|---|
| Body missing or not JSON | 400 |
| Room or slot not one of R1 (`R4`, `s1`, …): no such place on the programme. Checked before `talkId`: check-1 failed when we answered 400 for `PUT /programme/R2/slot2` with a string `talkId`. | 404 |
| `talkId` missing or not an integer | 400 |
| No talk with that `talkId`: as `POST /talks` with an unknown `speakerId`, a body that refers to nothing is 400 | 400 |
| Workshop outside `S4` (R6). We first answered 400, as for the main speaker as co-speaker (co-speakers check-2); check-3 failed on it. | 409 |
| Talk not `ACCEPTED` (R5): a conflict with its current state, which may change | 409 |
| Talk already on the programme in another room or slot (R8). In this room and slot it is 200, not an error. | 409 |
| Room and slot taken by another talk (R7) | 409 |
| A speaker of the talk is already speaking in that slot (R9) | 409 |
| `GET` or `DELETE` of a room and slot with no talk in it | 404 |

## Tests

- `ProgrammeTest`: every rule and error above against the real app, plus rushes of simultaneous `PUT`s to one slot
  (R11).
- `SlotTest`: the slot times and which formats fit which slot.
- `examples/scheduling.hurl`: the happy path and the main refusals, for running against the deployed service.
