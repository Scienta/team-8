# Lessons from the harness

What the harness's checks told us about the choices a feature request leaves open. Apply these when writing a spec in
`workflow/specs/`, and add a line whenever a check fails on one of our own decisions.

- **Idempotent only where the feature request says so.** If it says "adding someone who is already X changes
  nothing", that call is a 200. Anything else that changes nothing because there is nothing to change is a refusal: a
  `DELETE` of something that isn't there is 404, a move to the state a thing is already in is 409. (co-speakers
  check-1, R11)
- **A request that conflicts with the talk's current state is 409, as far as we know.** Our 409 for a third
  co-speaker hasn't failed a check, and the base app's 409 for the limit of 3 talks passes.
- **A request that can never succeed is 400, not 409.** co-speakers check-2 failed on making the main speaker a
  co-speaker with 409 and has passed since we answer 400. We apply the same to a workshop outside the long slot
  (scheduling); confirm or correct that once its checks have run.
- **Registering for a talk that isn't a workshop is 409, as far as we know: the "can never succeed" rule above doesn't
  carry over.** workshop-registration check-2 failed on it with 404, then with 400 (its transcript ends on that
  request, after our 409 for a withdrawn workshop). We now answer 409, like for a workshop that isn't `ACCEPTED`;
  confirm or correct once check-2 has run again.
