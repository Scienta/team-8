# Workshop registration: our own examples (workflow/specs/workshop-registration.md). Rule numbers refer to that spec.
# Run: hurl --test --variable base_url=http://localhost:8080 workshop-registration.hurl

# Setup: a speaker with a workshop and a presentation
POST {{base_url}}/speakers
{ "name": "Workshop Host", "email": "ws-host-{{newUuid}}@example.com" }
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Build a CFP in a day", "format": "WORKSHOP", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
workshop_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Not a workshop", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"

# R5: a workshop that isn't accepted takes no registrations, but has an (empty) list
POST {{base_url}}/workshops/{{workshop_id}}/registrations
{ "name": "Too Early", "email": "early@example.com" }
HTTP 409

GET {{base_url}}/workshops/{{workshop_id}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 0

# Accept the workshop: two reviewers, then the decision
PUT {{base_url}}/talks/{{workshop_id}}/ratings/201
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{workshop_id}}/ratings/202
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{workshop_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

# R1: register, the email as typed
POST {{base_url}}/workshops/{{workshop_id}}/registrations
{ "name": "Ada Lovelace", "email": "Ada@Example.com" }
HTTP 201
[Asserts]
jsonpath "$.id" isInteger
jsonpath "$.workshopId" == {{workshop_id}}
jsonpath "$.name" == "Ada Lovelace"
jsonpath "$.email" == "Ada@Example.com"

POST {{base_url}}/workshops/{{workshop_id}}/registrations
{ "name": "Grace Hopper", "email": "grace@example.com" }
HTTP 201

# R8: the same email in another case is the same person
POST {{base_url}}/workshops/{{workshop_id}}/registrations
{ "name": "Ada Again", "email": "ADA@example.COM" }
HTTP 409

# R2, R3: name and email are required, and the email has to look like one
POST {{base_url}}/workshops/{{workshop_id}}/registrations
{ "email": "nameless@example.com" }
HTTP 400

POST {{base_url}}/workshops/{{workshop_id}}/registrations
{ "name": "No Email" }
HTTP 400

POST {{base_url}}/workshops/{{workshop_id}}/registrations
{ "name": "Bad Email", "email": "bad" }
HTTP 400

# R4: a talk that isn't a workshop, and a talk that doesn't exist
POST {{base_url}}/workshops/{{talk_id}}/registrations
{ "name": "Ada Lovelace", "email": "ada@example.com" }
HTTP 409

GET {{base_url}}/workshops/{{talk_id}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 0

GET {{base_url}}/workshops/999999999/registrations
HTTP 404

# R10: the list, in the order people registered
GET {{base_url}}/workshops/{{workshop_id}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 2
jsonpath "$[0].email" == "Ada@Example.com"
jsonpath "$[1].email" == "grace@example.com"

# R6: withdrawn, the list stays but takes no more
POST {{base_url}}/talks/{{workshop_id}}/withdraw
{ "speakerId": {{speaker_id}} }
HTTP 200

POST {{base_url}}/workshops/{{workshop_id}}/registrations
{ "name": "Too Late", "email": "late@example.com" }
HTTP 409

GET {{base_url}}/workshops/{{workshop_id}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 2
