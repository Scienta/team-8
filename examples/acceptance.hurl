# Acceptance state machine: our own examples (workflow/specs/acceptance.md). Rule numbers refer to that spec.
# Run: hurl --test --variable base_url=http://localhost:8080 acceptance.hurl

# Setup: a speaker, a co-speaker and a talk
POST {{base_url}}/speakers
{ "name": "Main Speaker", "email": "acc-main-{{newUuid}}@example.com" }
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Co Speaker", "email": "acc-co-{{newUuid}}@example.com" }
HTTP 201
[Captures]
co_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Deciding things", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"
[Asserts]
jsonpath "$.status" == "SUBMITTED"

# R4: accepting needs two different reviewers; one reviewer twice is still one
PUT {{base_url}}/talks/{{talk_id}}/ratings/101
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/101
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "ACCEPTED" }
HTTP 409

# R1, R2: waitlist it
PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "WAITLISTED" }
HTTP 200
[Asserts]
jsonpath "$.id" == {{talk_id}}
jsonpath "$.status" == "WAITLISTED"

# R9: the same decision again changes nothing
PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "WAITLISTED" }
HTTP 200
[Asserts]
jsonpath "$.status" == "WAITLISTED"

# R4, R2: a second reviewer, then accept
PUT {{base_url}}/talks/{{talk_id}}/ratings/102
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "ACCEPTED" }
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"

# R3: accepted is final for the committee
PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "REJECTED" }
HTTP 409

# R7: the status shows wherever the talk is shown
GET {{base_url}}/speakers/{{speaker_id}}/talks
HTTP 200
[Asserts]
jsonpath "$[0].status" == "ACCEPTED"

# R5: only the main speaker can withdraw
POST {{base_url}}/talks/{{talk_id}}/withdraw
{ "speakerId": {{co_id}} }
HTTP 403

# R5, R6: the main speaker withdraws the accepted talk
POST {{base_url}}/talks/{{talk_id}}/withdraw
{ "speakerId": {{speaker_id}} }
HTTP 200
[Asserts]
jsonpath "$.status" == "WITHDRAWN"

# R3, R6: withdrawn is out for good
POST {{base_url}}/talks/{{talk_id}}/withdraw
{ "speakerId": {{speaker_id}} }
HTTP 409

PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "WAITLISTED" }
HTTP 409

GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.status" == "WITHDRAWN"

# Errors: not a decision, unknown talk
PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "SUBMITTED" }
HTTP 400

PUT {{base_url}}/talks/2147483000/status
{ "status": "REJECTED" }
HTTP 404
