# Change request: submission cap, our spec

Source: the harness change request `submission-cap-cr` (version 1, V: 2, released at tick 180), which changes the limit
of 3 talks per speaker from the base feature `talks` and replaces R9 of [co-speakers](co-speakers.md). The request
gives the business rules. This file adds what it leaves open. Decisions marked **(ours)** were made by the team on
2026-11-06, not by the product owner.

## Rules

A talk is *in the running* when its status is `SUBMITTED`, `WAITLISTED` or `ACCEPTED`; `REJECTED` and `WITHDRAWN`
talks are out of it ([acceptance](acceptance.md)).

| # | Rule | Source |
|---|---|---|
| R1 | A speaker's load is the number of talks in the running they present, as main speaker or as co-speaker. | request |
| R2 | A speaker can have a load of at most 3. | request |
| R3 | `POST /talks` for a main speaker whose load is 3 or more is refused with 409, as the base limit was. | request, base app for 409 |
| R4 | `PUT /talks/{id}/co-speakers/{speakerId}` of a new co-speaker whose load is 3 or more, to a talk in the running, is refused with 409. | request, **(ours)** for 409 |
| R5 | Adding a co-speaker to a `REJECTED` or `WITHDRAWN` talk ignores the cap: it doesn't add to the load. | request |
| R6 | A place is freed as soon as a talk is rejected or withdrawn, or the speaker is taken off it as co-speaker, and can be used by the next request. | request |
| R7 | Nothing that exists is removed or changed. A speaker over the cap keeps every talk and co-speaker place they have. | request |
| R8 | Re-adding someone who is already a co-speaker of the talk still changes nothing and answers 200, even when they are at or over the cap: it gives them nothing new (co-speakers R7). | **(ours)** |
| R9 | `GET /speakers/{id}/talks` and `GET /talks?speakerId=` still list only talks where the speaker is the main speaker. | request |
| R10 | The cap holds when submissions and co-speaker additions for one speaker arrive at the same time. | request |

## Errors **(ours)**

No new status codes. For `PUT /talks/{id}/co-speakers/{speakerId}` the order of [co-speakers](co-speakers.md) holds,
with the cap checked last: 400 for the main speaker, 200 for an existing co-speaker, 409 for a full talk, then 409 for
a speaker at the cap. `POST /talks` checks the cap last, after validating the body, as before.

## Concurrency **(ours)**

Every request that can add to a speaker's load locks that speaker's row (`SELECT … FOR UPDATE`) before counting their
load, and keeps it until commit: a submission locks its main speaker, a co-speaker addition locks the talk row first
(as before) and then the new co-speaker. Locks are always taken talk before speaker, and nothing takes them the other
way round, so the two can't deadlock. Rejecting, withdrawing and removing a co-speaker only lower a load and need no
speaker lock; a talk out of the running can't come back into it.

## Tests

- `CoSpeakerServiceTest`: the decision for an addition, as a pure function, including the cap and R5.
- `SubmissionCapTest`: R1–R10 against the real app, including submissions and co-speaker additions for one speaker
  sent at the same time. Verified to fail when the speaker lock is removed.
- `examples/submission-cap-cr.hurl`: the cap, freeing a place and R5, for running against the deployed service.
