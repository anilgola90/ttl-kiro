# API Standards

## Design Principles
- RESTful resource-oriented design
- Nouns for resources, HTTP verbs for actions
- Plural resource names: `/api/tickets`, not `/api/ticket`
- All endpoints prefixed with `/api/v1/` for versioning

## HTTP Methods
| Operation         | Method | Example                        |
|-------------------|--------|--------------------------------|
| List resources    | GET    | GET /api/v1/tickets            |
| Get one resource  | GET    | GET /api/v1/tickets/{id}       |
| Create resource   | POST   | POST /api/v1/tickets           |
| Full update       | PUT    | PUT /api/v1/tickets/{id}       |
| Partial update    | PATCH  | PATCH /api/v1/tickets/{id}     |
| Delete resource   | DELETE | DELETE /api/v1/tickets/{id}    |
| Sub-resource      | POST   | POST /api/v1/tickets/{id}/comments |
| AI ask            | POST   | POST /api/v1/ai/ask            |

## Status Codes
- 200 OK — successful GET, PUT, PATCH
- 201 Created — successful POST (include `Location` header)
- 204 No Content — successful DELETE
- 400 Bad Request — validation failure (malformed input)
- 404 Not Found — resource does not exist
- 409 Conflict — duplicate resource
- 422 Unprocessable Entity — business rule violation (e.g. invalid state transition)
- 500 Internal Server Error — unexpected server fault

## Request / Response Format
- Content-Type: `application/json` always
- All timestamps: ISO-8601 UTC — `2025-01-15T10:30:00Z`
- Ticket IDs: human-readable format `TKT-{number}` (e.g. `TKT-1001`)
- Enums in JSON: UPPER_SNAKE_CASE strings (e.g. `"IN_PROGRESS"`)

## Pagination (list endpoints)
```json
{
  "data": [...],
  "page": 0,
  "size": 20,
  "totalElements": 145,
  "totalPages": 8
}
```
- Default page size: 20, max: 100
- Query params: `?page=0&size=20&sort=createdAt,desc`

## Filtering & Search
- Status filter: `GET /api/v1/tickets?status=OPEN`
- Keyword search: `GET /api/v1/tickets?search=payment+failure`
- Multiple filters composable: `?status=OPEN&priority=HIGH`

## Error Envelope (all error responses)
```json
{
  "error": "INVALID_TRANSITION",
  "message": "Cannot transition from CLOSED to OPEN",
  "timestamp": "2025-01-15T10:30:00Z",
  "path": "/api/v1/tickets/TKT-1001/status"
}
```

## AI Ask Endpoint Contract
Request:
```json
POST /api/v1/ai/ask
{
  "question": "What caused previous payment failures?"
}
```
Response (match found):
```json
{
  "answer": "Based on ticket TKT-1001 and TKT-1023, payment failures were caused by...",
  "sources": ["TKT-1001", "TKT-1023"],
  "grounded": true
}
```
Response (no match):
```json
{
  "answer": "No relevant tickets were found to answer this question.",
  "sources": [],
  "grounded": false
}
```

## Versioning
- Version in URL path: `/api/v1/`
- When breaking changes are needed, introduce `/api/v2/` — never silently break v1
- Deprecate with `Deprecation` and `Sunset` response headers

## CORS
- Configure allowed origins explicitly — never use `*` in production
- Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS

## Documentation
- All endpoints must be documented via SpringDoc OpenAPI (`springdoc-openapi-starter-webmvc-ui`)
- Swagger UI available at `/swagger-ui.html` in dev/test profiles only
- Use `@Operation`, `@ApiResponse`, `@Schema` annotations on controllers and DTOs
