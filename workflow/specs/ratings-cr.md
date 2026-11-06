# Change request: ratings, our spec

Source: the harness change request `ratings-cr` (version 1, V: 2, released at tick 167), which changes how the base
feature `ratings` calculates `averageRating`. The request gives the business rules. This file adds what it leaves
open. Decisions marked **(ours)** were made by the team on 2026-11-06, not by the product owner.

## Rules

| # | Rule | Source |
|---|---|---|
| R1 | A talk with fewer than 5 ratings averages every score, as before. | request |
| R2 | A talk with 5 or more ratings leaves exactly one highest and exactly one lowest score out of its average, even when several reviewers gave the same top or bottom score. `1, 1, 5, 5, 5` averages `1, 5, 5`. | request |
| R3 | The average is rounded to 2 decimals, ties at the third decimal to the even neighbour: `3.125` shows as `3.12`, `3.375` as `3.38`. The rounding is of the exact quotient, so binary floating point never decides a tie. | request, **(ours)** for exact |
| R4 | `ratingCount` counts every reviewer, including the two left out of the average. | request |
| R5 | `GET /talks?sort=rating` ranks by the new, rounded average, then by `ratingCount`, then oldest first, with unrated talks last, as before. | request |
| R6 | The average shows wherever a talk is shown (`GET /talks`, `GET /talks/{id}`, `GET /speakers/{id}/talks`), and is `null` for a talk nobody has rated, as before. | base spec |
| R7 | A whole average still shows as a JSON number with one decimal (`3.0`), as the base app did. | **(ours)** |
| R8 | Everything that reads a talk's average keeps reading it as it is, now trimmed and to 2 decimals: the CSV report and the overall average on `/stats`, which still rounds its own result down to 1 decimal. | **(ours)**: "everything else stays as it is" |

## Errors

None new: the change request adds no endpoints and no inputs.

## Concurrency

The count, sum, lowest and highest score are read in one statement, so a rating that lands meanwhile can't leave
them disagreeing. Saving a rating is unchanged.

## Tests

- `RatingSummaryTest`: R1–R3 on the pure calculation, including the examples of the request and a tie whose
  quotient isn't exact in binary (`121 / 40 = 3.025`).
- `RatingsTest`: R2–R6 against the real app, including a ranking that the trimmed average reorders.
- `examples/ratings-cr.hurl`: the trimmed and rounded average and the ranking, for running against the deployed
  service.
