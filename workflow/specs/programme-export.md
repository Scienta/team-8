# Programme export: our spec

Source: the harness feature request `programme-export` (version 1, V: 1). The request gives the business rules and the
API surface. This file adds what it leaves open: status codes, edge cases and concurrency. Decisions marked **(ours)**
were made by the team on 2026-11-06, not by the product owner.

## Rules

| # | Rule | Source |
|---|---|---|
| R1 | `GET /programme` returns the programme as a JSON array of rows, 200. | request |
| R2 | `GET /programme.csv` returns the same rows as CSV, 200. | request |
| R3 | One row per talk on the programme ([scheduling](scheduling.md)), nothing else. Talks that changed after they were scheduled (withdrawn, got a co-speaker) are on the programme (scheduling R10) and so in the export. | request, **(ours)** for withdrawn talks |
| R4 | Rows are ordered by slot start (`S1` to `S5`), then by room (`R1`, `R2`, `R3`). Both views have the same order. | request |
| R5 | A row has `slot`, `start`, `end`, `room`, `talkId`, `title`, `format` and `speakers`, in that order. `start` and `end` are the slot's times as in scheduling (`HH:mm`). Title, format and speakers are the talk's as they are now, not when it was scheduled. | request, **(ours)** for the JSON key order |
| R6 | `speakers` lists names: the main speaker first, then the co-speakers in the order they were added. In JSON it's an array of strings; in CSV one field with the names joined by `; ` (semicolon and space). | request |
| R7 | The CSV is UTF-8 with `Content-Type: text/csv;charset=UTF-8`, whatever the request's `Accept` header says. | request, **(ours)** for the charset parameter |
| R8 | The CSV starts with exactly `slot,start,end,room,talkId,title,format,speakers`. Every line, the header included, ends in `\n` (no `\r`), and there is no blank line at the end. | request |
| R9 | RFC 4180 fields: a field is quoted only if it contains a comma, a double quote, `\r` or `\n`; quotes inside are doubled. Everything else is written as it is, so a semicolon or a non-ASCII letter isn't quoted or changed. A line break inside a quoted title is kept as it is. | request, **(ours)** for `\r` |
| R10 | An empty programme is `[]`, or a CSV with only the header line. | request |
| R11 | Each view is read in one SQL statement, so a row never mixes the programme before and after a concurrent change. | **(ours)** |

## Errors **(ours)**

None: both views always answer 200. A trailing slash (`/programme/`) is served as without one, like every other URL
(`TrailingSlashConfig`).

## Tests

- `ProgrammeExportTest`: every rule above against the real app.
- `ProgrammeCsvTest`: the CSV format, quoting and line endings, without the app.
- `examples/programme-export.hurl`: both views, for running against the deployed service.
