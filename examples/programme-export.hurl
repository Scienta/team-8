# Programme export: our own examples (workflow/specs/programme-export.md). Rule numbers refer to that spec.
# Run: hurl --test --variable base_url=http://localhost:8080 programme-export.hurl
# Uses R2 S4 and R2 S5 and frees them again at the end. They must be free when it starts.

# Setup: two speakers, an accepted workshop with a co-speaker and an accepted presentation with a picky title
POST {{base_url}}/speakers
{ "name": "Grace Hopper", "email": "export-grace-{{newUuid}}@example.com" }
HTTP 201
[Captures]
grace_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Åse Ødegård", "email": "export-ase-{{newUuid}}@example.com" }
HTTP 201
[Captures]
ase_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Compilers", "format": "WORKSHOP", "level": "BEGINNER", "speakerId": {{grace_id}} }
HTTP 201
[Captures]
workshop_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Kotlin, \"really\" fast", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{ase_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"

PUT {{base_url}}/talks/{{workshop_id}}/co-speakers/{{ase_id}}
HTTP 200

PUT {{base_url}}/talks/{{workshop_id}}/ratings/401
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{workshop_id}}/ratings/402
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{workshop_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/401
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/402
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

# Put them on the programme, the later one first
PUT {{base_url}}/programme/R2/S5
{ "talkId": {{talk_id}} }
HTTP 200

PUT {{base_url}}/programme/R2/S4
{ "talkId": {{workshop_id}} }
HTTP 200

# R1, R3, R5, R6: the JSON view
GET {{base_url}}/programme
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$[?(@.talkId == {{workshop_id}})].slot" == "S4"
jsonpath "$[?(@.talkId == {{workshop_id}})].start" == "13:30"
jsonpath "$[?(@.talkId == {{workshop_id}})].end" == "15:30"
jsonpath "$[?(@.talkId == {{workshop_id}})].room" == "R2"
jsonpath "$[?(@.talkId == {{workshop_id}})].title" == "Compilers"
jsonpath "$[?(@.talkId == {{workshop_id}})].format" == "WORKSHOP"
jsonpath "$[?(@.talkId == {{workshop_id}})].speakers" count == 2
jsonpath "$[?(@.talkId == {{workshop_id}})].speakers[0]" == "Grace Hopper"
jsonpath "$[?(@.talkId == {{workshop_id}})].speakers[1]" == "Åse Ødegård"
jsonpath "$[?(@.talkId == {{talk_id}})].title" == "Kotlin, \"really\" fast"

# R2, R4, R6–R9: the CSV view, text/csv even when asked for JSON, the workshop's row before the later talk's
GET {{base_url}}/programme.csv
Accept: application/json
HTTP 200
[Asserts]
header "Content-Type" == "text/csv;charset=UTF-8"
body startsWith "slot,start,end,room,talkId,title,format,speakers\n"
body endsWith "\n"
body not endsWith "\n\n"
body not contains "\r"
body contains "S4,13:30,15:30,R2,{{workshop_id}},Compilers,WORKSHOP,Grace Hopper; Åse Ødegård\n"
body matches "(?s)\nS4,13:30,15:30,R2,{{workshop_id}},.*\nS5,15:45,16:45,R2,{{talk_id}},"
body contains "S5,15:45,16:45,R2,{{talk_id}},\"Kotlin, \"\"really\"\" fast\",PRESENTATION,Åse Ødegård\n"

# Take both out again; R3: they're gone from the export
DELETE {{base_url}}/programme/R2/S5
HTTP 204

DELETE {{base_url}}/programme/R2/S4
HTTP 204

GET {{base_url}}/programme.csv
HTTP 200
[Asserts]
body not contains ",{{talk_id}},"
body not contains ",{{workshop_id}},"
