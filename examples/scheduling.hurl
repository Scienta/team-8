# Programme scheduling: our own examples (workflow/specs/scheduling.md). Rule numbers refer to that spec.
# Run: hurl --test --variable base_url=http://localhost:8080 scheduling.hurl
# Uses R3 S4 and R3 S5 and frees them again at the end. They must be free when it starts.

# Setup: one speaker with an accepted workshop, an accepted presentation and a talk that isn't accepted yet
POST {{base_url}}/speakers
{ "name": "Programme Speaker", "email": "programme-{{newUuid}}@example.com" }
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Plan a conference", "format": "WORKSHOP", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
workshop_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Rooms and slots", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Not decided yet", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
submitted_id: jsonpath "$.id"

PUT {{base_url}}/talks/{{workshop_id}}/ratings/301
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{workshop_id}}/ratings/302
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{workshop_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/301
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/302
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

# R5: only accepted talks go on the programme
PUT {{base_url}}/programme/R3/S5
{ "talkId": {{submitted_id}} }
HTTP 409

# R6: a workshop needs the long slot
PUT {{base_url}}/programme/R3/S5
{ "talkId": {{workshop_id}} }
HTTP 409

# R2: put the workshop in the long slot
PUT {{base_url}}/programme/R3/S4
{ "talkId": {{workshop_id}} }
HTTP 200
[Asserts]
jsonpath "$.room" == "R3"
jsonpath "$.slot" == "S4"
jsonpath "$.start" == "13:30"
jsonpath "$.end" == "15:30"
jsonpath "$.talkId" == {{workshop_id}}

# R3: look it up
GET {{base_url}}/programme/R3/S4
HTTP 200
[Asserts]
jsonpath "$.talkId" == {{workshop_id}}

# R7: one talk per room and slot
PUT {{base_url}}/programme/R3/S4
{ "talkId": {{talk_id}} }
HTTP 409

# R1, R8: the presentation goes elsewhere, and only once; putting it where it is again changes nothing
PUT {{base_url}}/programme/R3/S5
{ "talkId": {{talk_id}} }
HTTP 200
[Asserts]
jsonpath "$.start" == "15:45"
jsonpath "$.end" == "16:45"

PUT {{base_url}}/programme/R3/S5
{ "talkId": {{talk_id}} }
HTTP 200
[Asserts]
jsonpath "$.talkId" == {{talk_id}}

PUT {{base_url}}/programme/R3/S4
{ "talkId": {{talk_id}} }
HTTP 409

# Errors: unknown room or slot, missing talkId, unknown talk
PUT {{base_url}}/programme/R4/S1
{ "talkId": {{talk_id}} }
HTTP 404

PUT {{base_url}}/programme/R2/slot2
{ "talkId": "{{talk_id}}" }
HTTP 404

GET {{base_url}}/programme/R1/S6
HTTP 404

PUT {{base_url}}/programme/R3/S5
{ }
HTTP 400

PUT {{base_url}}/programme/R3/S5
{ "talkId": 999999999 }
HTTP 400

# R4: take both out again; a free place is 404
DELETE {{base_url}}/programme/R3/S5
HTTP 204

DELETE {{base_url}}/programme/R3/S4
HTTP 204

GET {{base_url}}/programme/R3/S4
HTTP 404

DELETE {{base_url}}/programme/R3/S4
HTTP 404
