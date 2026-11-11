# EquipLink Database Schema

## 1. Overview

EquipLink stores its data in a **PostgreSQL** database named `equiplink` (see `src/database/Database.java`). The schema is created idempotently by `DatabaseInitializer.initializeDatabase()` (`src/database/DatabaseInitializer.java`), which runs every `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` statement inside a single transaction, and is seeded with default staff accounts by `DatabaseSeeder`.

The schema consists of seven tables:

| # | Table | Purpose |
|---|---|---|
| 1 | `users` | All accounts: borrowers, ICT staff, facility staff, admins, and the campus director. |
| 2 | `password_reset_requests` | Borrower-initiated password resets reviewed by staff. |
| 3 | `equipment` | Campus equipment inventory with quantities and custodian assignment. |
| 4 | `borrow_requests` | The borrowing request form and its overall approval status. |
| 5 | `request_groups` | Per-staff-role processing lane for each request (ICT and/or Facility). |
| 6 | `request_items` | Individual equipment lines within a request, tracked from request to return. |
| 7 | `notifications` | Per-user in-app notifications. |
| 8 | `request_history` | Immutable audit trail of every request status change. |

## 2. Entity Relationships

```
users ──1:N── borrow_requests ──1:N── request_groups ──1:N── request_items ──N:1── equipment
  │                 │                                        (requested_equipment_id)
  │                 └──1:N── notifications                   (assigned_equipment_id)
  │                 └──1:N── request_history
  │
  ├──1:N── password_reset_requests
  └──1:N── request_history (as actor_id)
```

- Each **borrow request** has one or two **request groups** — at most one per custodian role (`UNIQUE(request_id, custodian_role)`), so a request containing both ICT and facility equipment is split across both staff queues.
- Each **request item** belongs to exactly one group and references equipment twice: what was originally requested and, optionally, what was actually assigned in its place.
- `users` is referenced by nearly every table (borrower, staff, reviewer, actor, creator).

## 3. Tables

### 3.1 `users`

| Column | Type | Constraints / Default | Description |
|---|---|---|---|
| `user_id` | `BIGSERIAL` | `PRIMARY KEY` | Surrogate identifier. |
| `username` | `VARCHAR(60)` | `NOT NULL`, `UNIQUE` | Login name. |
| `email` | `VARCHAR(150)` | `NOT NULL`, `UNIQUE` | Account email. |
| `password_hash` | `TEXT` | `NOT NULL` | PBKDF2 hash (see `PasswordHasher`). |
| `password_salt` | `TEXT` | `NOT NULL` | Per-user salt. |
| `password_iterations` | `INTEGER` | `NOT NULL`, `CHECK (>= 100000)` | Minimum hashing cost. |
| `role` | `VARCHAR(20)` | `NOT NULL`, one of `BORROWER`, `ICT_STAFF`, `FACILITY_STAFF`, `ADMIN`, `DIRECTOR` | System role. |
| `borrower_type` | `VARCHAR(10)` | nullable, one of `STUDENT`, `FACULTY` | Required when role is `BORROWER`. |
| `full_name` | `VARCHAR(150)` | `NOT NULL` | Display name. |
| `department` | `VARCHAR(120)` | nullable | Department / office. |
| `active` | `BOOLEAN` | `NOT NULL DEFAULT TRUE` | Soft-disable switch. |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Registration time. |

**Check constraint `borrower_type_rule`:** `borrower_type` must be `STUDENT` or `FACULTY` when `role = 'BORROWER'`, and must be `NULL` for every other role.

### 3.2 `password_reset_requests`

| Column | Type | Constraints / Default | Description |
|---|---|---|---|
| `reset_request_id` | `BIGSERIAL` | `PRIMARY KEY` | Surrogate identifier. |
| `user_id` | `BIGINT` | `NOT NULL` → `users(user_id)` `ON DELETE CASCADE` | Account requesting the reset. |
| `status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'PENDING'`, one of `PENDING`, `APPROVED`, `REJECTED`, `COMPLETED` | Review state. |
| `requested_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Submission time. |
| `reviewed_by` | `BIGINT` | nullable → `users(user_id)` | Staff member who reviewed it. |
| `reviewed_at` | `TIMESTAMP` | nullable | Review time. |
| `review_note` | `TEXT` | nullable | Reviewer's note. |

**Indexes:**

- `idx_password_reset_one_open_request` — `UNIQUE` on `(user_id)` `WHERE status IN ('PENDING', 'APPROVED')`: a user can have only one open reset request at a time.
- `idx_password_reset_status_date` — on `(status, requested_at)` for the review queue.

### 3.3 `equipment`

| Column | Type | Constraints / Default | Description |
|---|---|---|---|
| `equipment_id` | `BIGSERIAL` | `PRIMARY KEY` | Surrogate identifier. |
| `equipment_code` | `VARCHAR(30)` | `NOT NULL`, `UNIQUE` | Human-readable inventory code. |
| `equipment_name` | `VARCHAR(120)` | `NOT NULL` | Display name. |
| `category` | `VARCHAR(60)` | `NOT NULL` | Equipment category. |
| `total_quantity` | `INTEGER` | `NOT NULL`, `CHECK (>= 0)` | Units owned. |
| `damaged_quantity` | `INTEGER` | `NOT NULL DEFAULT 0`, `CHECK (>= 0)` | Units currently damaged. |
| `lost_quantity` | `INTEGER` | `NOT NULL DEFAULT 0`, `CHECK (>= 0)` | Units lost. |
| `status` | `VARCHAR(30)` | `NOT NULL DEFAULT 'AVAILABLE'`, one of `AVAILABLE`, `UNDER_MAINTENANCE`, `RETIRED` | Inventory status. |
| `active` | `BOOLEAN` | `NOT NULL DEFAULT TRUE` | Soft-delete switch. |
| `custodian_role` | `VARCHAR(20)` | `NOT NULL`, one of `ICT_STAFF`, `FACILITY_STAFF` | Staff role responsible for this equipment. |
| `created_by` | `BIGINT` | nullable → `users(user_id)` | Creator of the record. |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Creation time. |

**Table-level check:** `damaged_quantity + lost_quantity <= total_quantity`.

Available quantity is derived: `total_quantity - damaged_quantity - lost_quantity` minus quantities currently committed to active requests.

### 3.4 `borrow_requests`

| Column | Type | Constraints / Default | Description |
|---|---|---|---|
| `request_id` | `BIGSERIAL` | `PRIMARY KEY` | Surrogate identifier. |
| `request_code` | `VARCHAR(30)` | `NOT NULL`, `UNIQUE` | Human-readable reference code. |
| `borrower_id` | `BIGINT` | `NOT NULL` → `users(user_id)` | Requesting borrower. |
| `venue` | `VARCHAR(150)` | `NOT NULL` | Event venue. |
| `event_date` | `DATE` | `NOT NULL` | Event date. |
| `start_time` | `TIME` | `NOT NULL` | Start of the borrowing period. |
| `end_time` | `TIME` | `NOT NULL` | End of the borrowing period. |
| `purpose` | `TEXT` | `NOT NULL` | Reason for borrowing. |
| `participants` | `VARCHAR(255)` | `NOT NULL` | Number/participant description. |
| `person_in_charge` | `VARCHAR(150)` | `NOT NULL` | Person responsible on site. |
| `position` | `VARCHAR(100)` | `NOT NULL` | Position of the person in charge. |
| `status` | `VARCHAR(30)` | `NOT NULL DEFAULT 'PENDING_STAFF'` | Overall workflow status (see §4.1). |
| `admin_status` | `VARCHAR(20)` | nullable | Admin decision: `APPROVED` or `REJECTED`. |
| `director_status` | `VARCHAR(20)` | nullable | Director decision: `APPROVED` or `REJECTED`. |
| `remarks` | `TEXT` | nullable | Reviewer remarks. |
| `has_issues` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` | Set when return inspection finds damage/missing items. |
| `cancelled_by` | `BIGINT` | nullable → `users(user_id)` | Who cancelled the request. |
| `cancelled_at` | `TIMESTAMP` | nullable | When it was cancelled. |
| `cancellation_reason` | `TEXT` | nullable | Why it was cancelled. |
| `submitted_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Submission time. |
| `updated_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Last modification time. |

**Table-level check:** `end_time > start_time`.

**Indexes:** `idx_requests_borrower` on `(borrower_id)`, `idx_requests_status` on `(status)`.

### 3.5 `request_groups`

One processing lane per custodian role within a request.

| Column | Type | Constraints / Default | Description |
|---|---|---|---|
| `group_id` | `BIGSERIAL` | `PRIMARY KEY` | Surrogate identifier. |
| `request_id` | `BIGINT` | `NOT NULL` → `borrow_requests(request_id)` `ON DELETE CASCADE` | Parent request. |
| `custodian_role` | `VARCHAR(20)` | `NOT NULL`, one of `ICT_STAFF`, `FACILITY_STAFF` | Staff queue this group belongs to. |
| `status` | `VARCHAR(20)` | `NOT NULL` | Group workflow status (see §4.2). |
| `assigned_by` | `BIGINT` | nullable → `users(user_id)` | Staff who assigned equipment. |
| `assigned_at` | `TIMESTAMP` | nullable | When assignment happened. |
| `released_by` | `BIGINT` | nullable → `users(user_id)` | Staff who released equipment. |
| `released_at` | `TIMESTAMP` | nullable | When release happened. |
| `inspected_by` | `BIGINT` | nullable → `users(user_id)` | Staff who inspected the return. |
| `inspected_at` | `TIMESTAMP` | nullable | When inspection happened. |
| `cancelled_by` | `BIGINT` | nullable → `users(user_id)` | Who cancelled the group. |
| `cancelled_at` | `TIMESTAMP` | nullable | When it was cancelled. |
| `staff_remarks` | `TEXT` | nullable | Staff notes (e.g. reason for requesting changes). |

**Constraints:** `UNIQUE(request_id, custodian_role)` — at most one group per staff role per request.

**Index:** `idx_groups_status_role` on `(custodian_role, status)`.

### 3.6 `request_items`

Individual equipment lines within a request group, tracked through the whole lifecycle.

| Column | Type | Constraints / Default | Description |
|---|---|---|---|
| `request_item_id` | `BIGSERIAL` | `PRIMARY KEY` | Surrogate identifier. |
| `request_id` | `BIGINT` | `NOT NULL` → `borrow_requests(request_id)` `ON DELETE CASCADE` | Parent request. |
| `group_id` | `BIGINT` | `NOT NULL` → `request_groups(group_id)` `ON DELETE CASCADE` | Processing group the line belongs to. |
| `requested_equipment_id` | `BIGINT` | `NOT NULL` → `equipment(equipment_id)` | Equipment originally requested. |
| `requested_quantity` | `INTEGER` | `NOT NULL`, `CHECK (> 0)` | Quantity asked for. |
| `assigned_equipment_id` | `BIGINT` | nullable → `equipment(equipment_id)` | Equipment actually provided (may differ from the request). |
| `assigned_quantity` | `INTEGER` | `NOT NULL DEFAULT 0`, `CHECK (>= 0)` | Quantity assigned by staff. |
| `released_quantity` | `INTEGER` | `NOT NULL DEFAULT 0`, `CHECK (>= 0)` | Quantity handed to the borrower. |
| `returned_quantity` | `INTEGER` | `NOT NULL DEFAULT 0`, `CHECK (>= 0)` | Quantity returned and accepted. |
| `damaged_quantity` | `INTEGER` | `NOT NULL DEFAULT 0`, `CHECK (>= 0)` | Units found damaged on return. |
| `missing_quantity` | `INTEGER` | `NOT NULL DEFAULT 0`, `CHECK (>= 0)` | Units found missing on return. |
| `condition_before` | `VARCHAR(80)` | nullable | Condition recorded at release. |
| `condition_after` | `VARCHAR(80)` | nullable | Condition recorded at return inspection. |
| `inspection_result` | `VARCHAR(80)` | nullable | Inspection outcome. |
| `staff_remarks` | `TEXT` | nullable | Staff notes for this line. |

### 3.7 `notifications`

| Column | Type | Constraints / Default | Description |
|---|---|---|---|
| `notification_id` | `BIGSERIAL` | `PRIMARY KEY` | Surrogate identifier. |
| `user_id` | `BIGINT` | `NOT NULL` → `users(user_id)` `ON DELETE CASCADE` | Recipient. |
| `request_id` | `BIGINT` | nullable → `borrow_requests(request_id)` `ON DELETE CASCADE` | Related request, if any. |
| `title` | `VARCHAR(150)` | `NOT NULL` | Short heading. |
| `message` | `TEXT` | `NOT NULL` | Body text. |
| `is_read` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` | Read flag. |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | When it was sent. |

**Index:** `idx_notifications_user_read` on `(user_id, is_read)`.

### 3.8 `request_history`

Append-only audit trail; rows are never updated, only inserted.

| Column | Type | Constraints / Default | Description |
|---|---|---|---|
| `history_id` | `BIGSERIAL` | `PRIMARY KEY` | Surrogate identifier. |
| `request_id` | `BIGINT` | `NOT NULL` → `borrow_requests(request_id)` `ON DELETE CASCADE` | Request the event belongs to. |
| `group_id` | `BIGINT` | nullable → `request_groups(group_id)` `ON DELETE CASCADE` | Group involved, when the event is group-scoped. |
| `actor_id` | `BIGINT` | nullable → `users(user_id)` | User who caused the event. |
| `event_type` | `VARCHAR(60)` | `NOT NULL` | Event name (see §4.3). |
| `from_status` | `VARCHAR(30)` | nullable | Status before the event. |
| `to_status` | `VARCHAR(30)` | nullable | Status after the event. |
| `remarks` | `TEXT` | nullable | Free-text details. |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Event time. |

**Index:** `idx_history_request` on `(request_id, created_at)`.

## 4. Value Domains and Lifecycles

### 4.1 `borrow_requests.status`

`PENDING_STAFF` → `NEEDS_ACTION` ↔ `PENDING_STAFF` → `PENDING_ADMIN` → `PENDING_DIRECTOR` → `APPROVED` → `RELEASED` → `COMPLETED`

| Status | Meaning |
|---|---|
| `PENDING_STAFF` | Submitted; waiting for ICT/facility staff to assign equipment. |
| `NEEDS_ACTION` | Staff requested changes; borrower must edit and resubmit. |
| `PENDING_ADMIN` | All groups assigned; waiting for administrator review. |
| `PENDING_DIRECTOR` | Admin approved; waiting for the campus director's final decision. |
| `APPROVED` | Director approved; staff may release equipment. |
| `RELEASED` | Equipment handed to the borrower. |
| `COMPLETED` | Equipment returned and inspection finished. |
| `REJECTED` | Rejected by admin or director (terminal). |
| `CANCELLED` | Cancelled by borrower or reviewer (terminal). |

### 4.2 `request_groups.status`

`PENDING` → `ASSIGNED` → `RELEASED` → `RETURNED`

| Status | Meaning |
|---|---|
| `PENDING` | Waiting for this staff role to process its items. |
| `ASSIGNED` | Equipment assigned (or the group is resolved). |
| `UNABLE` | This role cannot fulfill its items. |
| `RELEASED` | Equipment handed over to the borrower. |
| `RETURNED` | Equipment returned and inspected. |
| `CANCELLED` | Group cancelled with the request. |
| `NOT_REQUIRED` | Group no longer needed (e.g. request rejected before release). |

### 4.3 `request_history.event_type`

Events written by `BorrowRequestService`:

`SUBMITTED`, `BORROWER_EDITED`, `BORROWER_CANCELLED`, `EQUIPMENT_ASSIGNED`, `STAFF_REQUESTED_CHANGES`, `EQUIPMENT_RELEASED`, `EQUIPMENT_RETURNED`, `ADMIN_APPROVED`, `ADMIN_REJECTED`, `DIRECTOR_APPROVED`, `DIRECTOR_REJECTED`.

### 4.4 Other enumerations

| Domain | Values |
|---|---|
| `users.role` | `BORROWER`, `ICT_STAFF`, `FACILITY_STAFF`, `ADMIN`, `DIRECTOR` |
| `users.borrower_type` | `STUDENT`, `FACULTY` (borrowers only; faculty requests get priority in staff queues) |
| `equipment.status` | `AVAILABLE`, `UNDER_MAINTENANCE`, `RETIRED` |
| `equipment.custodian_role` / `request_groups.custodian_role` | `ICT_STAFF`, `FACILITY_STAFF` |
| `password_reset_requests.status` | `PENDING`, `APPROVED`, `REJECTED`, `COMPLETED` |
| `admin_status` / `director_status` | `APPROVED`, `REJECTED` |

## 5. Delete Behavior

| Parent → Child | On delete of parent |
|---|---|
| `users` → `password_reset_requests`, `notifications` | `CASCADE` |
| `users` → `borrow_requests`, `equipment`, `request_history.actor_id`, etc. | `RESTRICT` (default) — accounts are disabled via `active` instead of deleted |
| `borrow_requests` → `request_groups`, `request_items`, `notifications`, `request_history` | `CASCADE` |
| `request_groups` → `request_items`, `request_history.group_id` | `CASCADE` |
| `equipment` → `request_items` | `RESTRICT` (default) — preserves history; equipment is retired via `status`/`active` instead |

## 6. Notes

- All DDL runs inside one transaction with `setAutoCommit(false)`; failure rolls everything back.
- Passwords use PBKDF2 with a per-user salt and at least 100,000 iterations (`PasswordHasher`).
- `DatabaseSeeder` inserts four default accounts (system admin / ICT staff, facility staff, facility admin, campus director) and skips any username or email that already exists.
- Quantity-consistency rules are enforced in the application layer with row locks (`SELECT ... FOR UPDATE`) in `BorrowRequestService`, in addition to the column-level checks above.

---

**Project Name:** EquipLink – Campus Equipment Borrowing and Management System

**Document:** Database Schema

**Purpose:** To describe the PostgreSQL tables, relationships, constraints, and status lifecycles used by EquipLink.
