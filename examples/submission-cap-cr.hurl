# Change request: submission cap (workflow/specs/submission-cap-cr.md). Our own examples: the harness published none.
# Rule numbers refer to that spec. Run: examples/run.sh [base-url]

# Setup: Grace, and Alan and Barbara, who each have a talk
POST {{base_url}}/speakers
{ "name": "Grace Hopper", "email": "grace-{{newUuid}}@example.com" }
HTTP 201
[Captures]
grace: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Alan Kay", "email": "alan-{{newUuid}}@example.com" }
HTTP 201
[Captures]
alan: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Barbara Liskov", "email": "barbara-{{newUuid}}@example.com" }
HTTP 201
[Captures]
barbara: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Objects", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{alan}} }
HTTP 201
[Captures]
alans_talk: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Substitution", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{barbara}} }
HTTP 201
[Captures]
barbaras_talk: jsonpath "$.id"

# R1: Grace co-speaks on Alan's talk and submits two of her own: 3 talks
PUT {{base_url}}/talks/{{alans_talk}}/co-speakers/{{grace}}
HTTP 200

POST {{base_url}}/talks
{ "title": "Compilers", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{grace}} }
HTTP 201
[Captures]
compilers: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Nanoseconds", "format": "LIGHTNING_10", "level": "BEGINNER", "speakerId": {{grace}} }
HTTP 201

# R3: at the cap, she can't submit a fourth
POST {{base_url}}/talks
{ "title": "Debugging", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{grace}} }
HTTP 409

# R4: nor be added as a co-speaker to a talk in the running
PUT {{base_url}}/talks/{{barbaras_talk}}/co-speakers/{{grace}}
HTTP 409

GET {{base_url}}/talks/{{barbaras_talk}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 0

# R8: re-adding her where she already co-speaks changes nothing
PUT {{base_url}}/talks/{{alans_talk}}/co-speakers/{{grace}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 1

# R9: her talk list shows only her own talks
GET {{base_url}}/speakers/{{grace}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 2

# R6: withdrawing one of her talks frees a place straight away
POST {{base_url}}/talks/{{compilers}}/withdraw
{ "speakerId": {{grace}} }
HTTP 200

PUT {{base_url}}/talks/{{barbaras_talk}}/co-speakers/{{grace}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds[0]" == {{grace}}

POST {{base_url}}/talks
{ "title": "Debugging", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{grace}} }
HTTP 409

# R6: so does taking her off a talk as co-speaker
DELETE {{base_url}}/talks/{{alans_talk}}/co-speakers/{{grace}}
HTTP 200

POST {{base_url}}/talks
{ "title": "Debugging", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{grace}} }
HTTP 201

# R5: a rejected talk takes her even at the cap, and doesn't count
PUT {{base_url}}/talks/{{alans_talk}}/status
{ "status": "REJECTED" }
HTTP 200

PUT {{base_url}}/talks/{{alans_talk}}/co-speakers/{{grace}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds[0]" == {{grace}}

# R6: rejecting a talk she co-speaks on frees her place there
PUT {{base_url}}/talks/{{barbaras_talk}}/status
{ "status": "REJECTED" }
HTTP 200

POST {{base_url}}/talks
{ "title": "Cobol", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{grace}} }
HTTP 201
