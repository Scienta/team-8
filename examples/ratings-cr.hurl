# Change request: ratings (workflow/specs/ratings-cr.md). Our own examples: the harness published none.
# Run: examples/run.sh [base-url]

# Setup: a speaker with two talks
POST {{base_url}}/speakers
{
  "name": "Barbara Liskov",
  "email": "barbara-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Data abstraction",
  "format": "PRESENTATION",
  "level": "INTERMEDIATE",
  "speakerId": {{speaker_id}}
}
HTTP 201
[Captures]
steady: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Substitution",
  "format": "PRESENTATION",
  "level": "INTERMEDIATE",
  "speakerId": {{speaker_id}}
}
HTTP 201
[Captures]
outlier: jsonpath "$.id"

# R1: four ratings, every score counts: 4, 5, 5, 5 = 4.75
PUT {{base_url}}/talks/{{steady}}/ratings/1
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{steady}}/ratings/2
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{steady}}/ratings/3
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{steady}}/ratings/4
{
  "score": 5
}
HTTP 200

GET {{base_url}}/talks/{{steady}}
HTTP 200
[Asserts]
jsonpath "$.averageRating" == 4.75
jsonpath "$.ratingCount" == 4

# R2: five ratings, one 1 and one 5 left out: 5, 5, 5 = 5.0 (it was 4.2)
PUT {{base_url}}/talks/{{outlier}}/ratings/1
{
  "score": 1
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/2
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/3
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/4
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/5
{
  "score": 5
}
HTTP 200

# R2, R4: trimmed, and every reviewer still counts
GET {{base_url}}/talks/{{outlier}}
HTTP 200
[Asserts]
jsonpath "$.averageRating" == 5.0
jsonpath "$.ratingCount" == 5

# R5: the trimmed average ranks the outlier first
GET {{base_url}}/talks
[Query]
sort: rating
speakerId: {{speaker_id}}
HTTP 200
[Asserts]
jsonpath "$" count == 2
jsonpath "$[0].id" == {{outlier}}
jsonpath "$[1].id" == {{steady}}

# R3: ten ratings, the 1 and one 5 left out: 25 / 8 = 3.125, a tie that goes to 3.12
PUT {{base_url}}/talks/{{outlier}}/ratings/1
{
  "score": 1
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/2
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/3
{
  "score": 2
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/4
{
  "score": 3
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/5
{
  "score": 3
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/6
{
  "score": 3
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/7
{
  "score": 3
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/8
{
  "score": 3
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/9
{
  "score": 3
}
HTTP 200

PUT {{base_url}}/talks/{{outlier}}/ratings/10
{
  "score": 5
}
HTTP 200

GET {{base_url}}/talks/{{outlier}}
HTTP 200
[Asserts]
jsonpath "$.averageRating" == 3.12
jsonpath "$.ratingCount" == 10
