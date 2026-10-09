# REST API

All endpoints except readiness require `Authorization: Bearer <access_token>`. UUID IDs are used for organizations, incidents, and notifications. JSON property names use camelCase. Validation errors use RFC 9457 problem details. Missing authentication returns 401, denied role returns 403, unknown/foreign tenant resources return 404, stale updates and invalid workflow transitions return 409.

| Method | Path                                               | Access            | Request / response                                          |
| ------ | -------------------------------------------------- | ----------------- | ----------------------------------------------------------- |
| GET    | `/api/organizations`                               | Authenticated     | List `{id,name,role}` memberships                           |
| POST   | `/api/organizations`                               | Authenticated     | `{name}` → organization; 201                                |
| PUT    | `/api/organizations/{org}`                         | Admin             | `{name}` → organization                                     |
| GET    | `/api/organizations/{org}/members`                 | Member            | List `{subject,displayName,role}`                           |
| PUT    | `/api/organizations/{org}/members`                 | Admin             | `{subject,displayName,role}`; upsert                        |
| GET    | `/api/organizations/{org}/incidents`               | Member            | `?page=0&size=20&status=OPEN` → `{items,page,size,hasMore}` |
| POST   | `/api/organizations/{org}/incidents`               | Admin/responder   | `{title,description,severity}` → incident; 201              |
| GET    | `/api/organizations/{org}/incidents/{id}`          | Member            | Incident                                                    |
| PATCH  | `/api/organizations/{org}/incidents/{id}`          | Admin/responder   | `{status,assignee,version}` → updated incident              |
| GET    | `/api/organizations/{org}/incidents/{id}/activity` | Member            | Latest 200 `{id,actor,message,createdAt}`                   |
| POST   | `/api/organizations/{org}/incidents/{id}/comments` | Admin/responder   | `{message}`; 201                                            |
| GET    | `/api/organizations/{org}/summary`                 | Member            | `{total,open,acknowledged,resolved,critical}`               |
| GET    | `/api/organizations/{org}/notifications`           | Member            | Own latest 50 `{id,incidentId,message,readAt,createdAt}`    |
| PATCH  | `/api/organizations/{org}/notifications/{id}/read` | Recipient         | Empty body                                                  |
| GET    | `/api/organizations/{org}/deliveries`              | Admin             | Latest 100 outbox delivery states (snake_case fields)       |
| POST   | `/api/organizations/{org}/deliveries/{id}/retry`   | Admin             | Empty body; only failed unpublished events                  |
| GET    | `/api/health`                                      | Public, web proxy | Database readiness; status only                             |
| GET    | `/actuator/health/readiness`                       | Public, API       | Database readiness; status only                             |

Roles: `ADMIN`, `RESPONDER`, `VIEWER`. Severity: `SEV1` (critical) through `SEV4` (low). Status: `OPEN`, `ACKNOWLEDGED`, `RESOLVED`. Critical counts only unresolved SEV1 incidents. `assignee` is a same-organization responder/admin subject or `null`. PATCH carries the version received on the last read; successful updates increment it. No incident deletion is implemented, preserving the response history.

Example incident:

```json
{
  "id": "aaaaaaaa-0000-0000-0000-000000000001",
  "organizationId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "title": "Checkout errors",
  "description": "Investigating payment gateway",
  "severity": "SEV1",
  "status": "OPEN",
  "assignee": null,
  "createdBy": "11111111-1111-1111-1111-111111111111",
  "version": 0,
  "createdAt": "2026-10-08T20:00:00Z",
  "updatedAt": "2026-10-08T20:00:00Z"
}
```

API boundaries: title 160 characters, description 8000, comment 2000, organization/display names 120, identity subjects 200; empty titles and comments are rejected. Notification messages are clipped to 500 characters, while full comments remain in activity. Page numbers are limited to 0–10000 and page sizes to 1–100. Large exports and full-text search are outside this project's scope.
