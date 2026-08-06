---
packages:
  orgmemory: minor
subject: Separate data clearance from user roles
---

## Improvements

The user "role" field is now a data clearance with two values, Standard and
Executive, matching what the system actually enforces: Executive widens
confidential and restricted document access, while action permissions stay
governed by organization roles. Administrators can now assign a user's
department (required for confidential document access), raising someone to
Executive asks for confirmation and states its reach, and every user can see
their own department and clearance in the account menu. Legacy titles such as
Team lead, Manager, Director, and the misleading Admin label are removed;
existing Executive users keep Executive and everyone else becomes Standard.
