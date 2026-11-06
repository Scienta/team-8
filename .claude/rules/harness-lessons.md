# Lessons from the harness

What the harness's checks told us about the choices a feature request leaves open. Apply these when writing a spec in
`workflow/specs/`, and add a line whenever a check fails on one of our own decisions.

- **Idempotent only where the feature request says so.** If it says "adding someone who is already X changes
  nothing", that call is a 200. Anything else that changes nothing because there is nothing to change is a refusal: a
  `DELETE` of something that isn't there is 404, a move to the state a thing is already in is 409. (co-speakers
  check-1, R11)
- **A request that conflicts with the talk's current state is 409, as far as we know.** Our 409 for a third
  co-speaker hasn't failed a check, and the base app's 409 for the limit of 3 talks passes.
- **A path that names nothing is 404, before the body is checked.** scheduling check-1 failed on `PUT
  /programme/R2/slot2` with `{"talkId": "4573"}` while we answered 400 for the string `talkId`.
- **A request that can never succeed is 400, not 409, for co-speakers only.** co-speakers check-2 failed on making the
  main speaker a co-speaker with 409 and has passed since we answer 400. It doesn't carry over: scheduling check-3
  failed on our 400 for a workshop outside the long slot, which we now answer with 409.
- **Registering for a talk that isn't a workshop is 409: the "can never succeed" rule above doesn't carry over.**
  workshop-registration check-2 failed on it with 404, then with 400, and got past it with 409, like for a workshop
  that isn't `ACCEPTED`.
- **Listing what a talk of the wrong kind has is 200 with `[]`, not 404.** The path names a talk that exists, so it
  isn't "a path that names nothing". workshop-registration check-2 failed on our 404 for `GET
  /workshops/{id}/registrations` of a `LIGHTNING_20` talk, right after our 409 for registering to it.
