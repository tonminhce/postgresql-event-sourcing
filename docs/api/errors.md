# API error types

Every error of the API is serialized as an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)
problem detail, with the `application/problem+json` media type:

```json
{
  "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#order-state-conflict",
  "title": "Invalid order state",
  "status": 409,
  "detail": "Order in status COMPLETED can't be cancelled",
  "instance": "/orders/48f08af0-e7a5-3451-8ed7-87cc9d131ad1",
  "code": "ORDER_STATE_CONFLICT"
}
```

| Member | Meaning |
| --- | --- |
| `type` | Identifies the problem type. It is the base URL of this page followed by the anchor of the error, so it points to the section below that documents it. The base URL is configurable with `api.problem-type-docs-url`. |
| `title` | The short summary of the problem type. It is the same for every occurrence of the error. |
| `status` | The HTTP status of the response. |
| `detail` | The human-readable explanation of this occurrence. It may change, so do not parse it. |
| `instance` | The path of the request that failed. |
| `code` | The stable, machine-readable code of the error. **Branch on this one.** It never changes. |

The catalog is also available as JSON on `GET /api/errors`, for the clients and the
SDK generators that want to discover the codes without reading this page.

The `type` member of the errors that are raised by the framework itself, such as an
unsupported media type, is `about:blank`: they have no problem type of their own, and
only their status code is meaningful.

## The catalog

| Code | Status | Title |
| --- | --- | --- |
| [`VALIDATION_ERROR`](#validation-error) | 400 | Validation failed |
| [`INVALID_REQUEST`](#invalid-request) | 400 | Invalid request |
| [`RESOURCE_NOT_FOUND`](#resource-not-found) | 404 | Resource not found |
| [`ORDER_STATE_CONFLICT`](#order-state-conflict) | 409 | Invalid order state |
| [`CONCURRENT_MODIFICATION`](#concurrent-modification) | 409 | Concurrent modification |
| [`IDEMPOTENCY_CONFLICT`](#idempotency-conflict) | 409 | Idempotency key conflict |
| [`IDEMPOTENCY_REQUEST_IN_PROGRESS`](#idempotency-request-in-progress) | 409 | Request is already in progress |
| [`VALIDATION_FAILED`](#validation-failed) | 422 | Validation failed |
| [`IDEMPOTENCY_KEY_MISMATCH`](#idempotency-key-mismatch) | 422 | Idempotency key mismatch |

## <a id="validation-error"></a>Validation failed

**`VALIDATION_ERROR`** &mdash; HTTP 400 Bad Request

A required field of the request is missing, or its value cannot be read. The request
never reaches the domain model.

**What to do:** read the `detail` member, it names the field that is missing. Send the
field the API expects.

```json
{
  "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Fields 'riderId' and 'price' are required",
  "instance": "/orders",
  "code": "VALIDATION_ERROR"
}
```

## <a id="invalid-request"></a>Invalid request

**`INVALID_REQUEST`** &mdash; HTTP 400 Bad Request

The request cannot be interpreted: the body is missing a field that the requested
transition requires, or the request is otherwise malformed.

**What to do:** read the `detail` member and correct the request. A request that
repeats a transition the order has already gone through answers with
[`ORDER_STATE_CONFLICT`](#order-state-conflict) instead.

```json
{
  "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#invalid-request",
  "title": "Invalid request",
  "status": 400,
  "detail": "Field 'status' is required",
  "instance": "/orders/48f08af0-e7a5-3451-8ed7-87cc9d131ad1",
  "code": "INVALID_REQUEST"
}
```

## <a id="resource-not-found"></a>Resource not found

**`RESOURCE_NOT_FOUND`** &mdash; HTTP 404 Not Found

The order does not exist.

**What to do:** check the identifier of the order. The `instance` member of the
response holds the path that was requested.

```json
{
  "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#resource-not-found",
  "title": "Resource not found",
  "status": 404,
  "detail": "Order not found for ID: 48f08af0-e7a5-3451-8ed7-87cc9d131ad1",
  "instance": "/orders/48f08af0-e7a5-3451-8ed7-87cc9d131ad1",
  "code": "RESOURCE_NOT_FOUND"
}
```

## <a id="order-state-conflict"></a>Invalid order state

**`ORDER_STATE_CONFLICT`** &mdash; HTTP 409 Conflict

The order is not in a status that allows the requested transition. The order follows
the state machine below, and only the transitions that start from its current status
are accepted.

```
PLACED ──> ADJUSTED ──> ACCEPTED ──> COMPLETED
   │           │            │
   └───────────┴────────────┴────> CANCELLED
```

**What to do:** read the order with `GET /orders/{id}` and request a transition that is
allowed from its current status. The `detail` member names the current status.

```json
{
  "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#order-state-conflict",
  "title": "Invalid order state",
  "status": 409,
  "detail": "Order in status COMPLETED can't be cancelled",
  "instance": "/orders/48f08af0-e7a5-3451-8ed7-87cc9d131ad1",
  "code": "ORDER_STATE_CONFLICT"
}
```

## <a id="concurrent-modification"></a>Concurrent modification

**`CONCURRENT_MODIFICATION`** &mdash; HTTP 409 Conflict

The order was modified by another request between the moment this request read it and
the moment it wrote it. The optimistic concurrency control of the event store rejected
the write, so nothing was changed.

**What to do:** read the order again and retry the transition against its new state.
The change is a lost update, not a failure of the request.

```json
{
  "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#concurrent-modification",
  "title": "Concurrent modification",
  "status": 409,
  "detail": "Actual version doesn't match expected version 3",
  "instance": "/orders/48f08af0-e7a5-3451-8ed7-87cc9d131ad1",
  "code": "CONCURRENT_MODIFICATION"
}
```

## <a id="idempotency-conflict"></a>Idempotency key conflict

**`IDEMPOTENCY_CONFLICT`** &mdash; HTTP 409 Conflict

The `Idempotency-Key` of the request was already used for another order.

**What to do:** use a fresh `Idempotency-Key` for every distinct command. A key
identifies exactly one command, so it must never be reused for another one. A key that
is reused for the same command with a different payload answers with
[`IDEMPOTENCY_KEY_MISMATCH`](#idempotency-key-mismatch) instead.

```json
{
  "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#idempotency-conflict",
  "title": "Idempotency key conflict",
  "status": 409,
  "detail": "Idempotency-Key already used for a different order",
  "instance": "/orders/48f08af0-e7a5-3451-8ed7-87cc9d131ad1",
  "code": "IDEMPOTENCY_CONFLICT"
}
```

## <a id="idempotency-request-in-progress"></a>Request is already in progress

**`IDEMPOTENCY_REQUEST_IN_PROGRESS`** &mdash; HTTP 409 Conflict

A request with the same `Idempotency-Key` is still being processed. The server holds a
lease on the key for the duration of the command.

**What to do:** retry after the number of seconds of the `Retry-After` header, which is
`1`. The response of the first request is returned once it completes.

```json
{
  "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#idempotency-request-in-progress",
  "title": "Request is already in progress",
  "status": 409,
  "detail": "Request is currently being processed. Please retry shortly.",
  "instance": "/orders",
  "code": "IDEMPOTENCY_REQUEST_IN_PROGRESS"
}
```

## <a id="validation-failed"></a>Validation failed

**`VALIDATION_FAILED`** &mdash; HTTP 422 Unprocessable Content

The request is well-formed, but its content violates a business rule: the price is not
positive, the coordinates are out of range, the driver is the rider, or the requested
transition requires a field that the request does not carry.

**What to do:** read the `detail` member, it names the rule that was broken, and send a
request that satisfies it. This error is about the values of the request, not about its
shape, so a retry with the same values fails the same way.

```json
{
  "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#validation-failed",
  "title": "Validation failed",
  "status": 422,
  "detail": "Price must be strictly positive",
  "instance": "/orders",
  "code": "VALIDATION_FAILED"
}
```

## <a id="idempotency-key-mismatch"></a>Idempotency key mismatch

**`IDEMPOTENCY_KEY_MISMATCH`** &mdash; HTTP 422 Unprocessable Content

The `Idempotency-Key` of the request was already used, but with a different payload. The
server stores a hash of the payload with the key, so it can detect the reuse.

**What to do:** never reuse a key for a different payload. If the request is the same,
retry it with the same key and the same payload: the response of the first request is
returned with the `Idempotent-Replayed: true` header.

```json
{
  "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#idempotency-key-mismatch",
  "title": "Idempotency key mismatch",
  "status": 422,
  "detail": "Idempotency-Key reused with different payload",
  "instance": "/orders",
  "code": "IDEMPOTENCY_KEY_MISMATCH"
}
```
