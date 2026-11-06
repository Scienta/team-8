# Workshop registration: our spec

Source: the harness feature request `workshop-registration` (version 1, V: 2). The request gives the business rules and
the API surface. This file adds what it leaves open: status codes, edge cases and concurrency. Decisions marked
**(ours)** were made by the team on 2026-11-06, not by the product owner.

## Rules

| # | Rule | Source |
|---|---|---|
| R1 | `POST /workshops/{id}/registrations` with `{"name": …, "email": …}` registers an attendee and returns the registration (`id`, `workshopId`, `name`, `email`), 201. | request |
| R2 | Name and email are both required: a missing, non-string or blank value is refused. | request |
| R3 | An email has to look like one, as for speakers (`Utils.isValidEmail`: an `@` with a `.` after it). | **(ours)** |
| R4 | A workshop is a talk with format `WORKSHOP`. Any other talk isn't one and doesn't take registrations; its list of registrations is `[]`. check-2 failed on 404 for that list. | request, **(ours)** for 409 and 200 |
| R5 | Only an `ACCEPTED` workshop takes registrations. `SUBMITTED`, `WAITLISTED`, `REJECTED` and `WITHDRAWN` ones don't. | request |
| R6 | A workshop that is withdrawn after people registered keeps them on its list. | request |
| R7 | At most 20 registrations per workshop, ever. | request |
| R8 | One registration per email per workshop. Emails are compared ignoring letter case and surrounding whitespace; the response and the list show the email as the attendee typed it. | request, **(ours)** for whitespace |
| R9 | The same email can register for several workshops. | request |
| R10 | `GET /workshops/{id}/registrations` lists the workshop's registrations in the order they were made, `[]` when there are none. It works for a workshop in any status. | request, **(ours)** for any status |
| R11 | Registrations to one workshop are serialised by locking the talk row (`SELECT … FOR UPDATE`), so a rush never takes more than 20 and never registers an email twice. A unique index on the normalised email backs R8 up. | request, **(ours)** for how |

## Errors **(ours)**

Checked in this order; the first that applies wins.

| Case | Status |
|---|---|
| `id` not an integer, body missing or not JSON | 400 |
| `name` or `email` missing, not a string or blank (R2), or email not an email (R3) | 400 |
| No talk with that id | 404 |
| Registering for a talk that isn't a `WORKSHOP` (R4), refused like one that isn't `ACCEPTED`. check-2 failed on it with 404 and with 400, and got past it with 409. | 409 |
| Workshop not `ACCEPTED` (R5): a conflict with its current state, which may change | 409 |
| Email already registered for this workshop (R8), even when the workshop is full | 409 |
| Workshop has 20 registrations (R7) | 409 |

409 for a full workshop matches what the base app answers when the limit of 3 talks is reached, and 409 for a taken
email matches `POST /speakers`.

## Tests

- `WorkshopRegistrationsTest`: every rule and error above against the real app, plus five rushes of 60 simultaneous
  registrations to one workshop (R11). Verified to fail when the row lock is removed.
- `examples/workshop-registration.hurl`: the happy path and the main refusals, for running against the deployed
  service.
