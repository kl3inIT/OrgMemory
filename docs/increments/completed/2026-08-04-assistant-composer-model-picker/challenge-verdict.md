# Assistant Composer And Conversation Model Picker Challenge Verdict

Increment status: completed 2026-08-04.

Date: 2026-08-04  
Commit reviewed: `657e770ad06744fa8633206c3bd98feb824cda7a`

## Verdict

`ACCEPT WITH MUST-FIXES`.

Conversation-scoped model selection is defensible only as a new server-owned
authorization boundary. The UI evidence does not authorize accepting a gateway
or arbitrary model string from the browser.

## Strongest Counterargument

The immediate UX problem can be solved by replacing `Permission-aware` with a
read-only effective-model indicator. Persistent selection widens the control
plane: the current port can express an exact route or conversation memory but
not both, the registry intentionally rejects an exact route different from the
administrator default, and key/model persistence alone cannot detect route or
catalog revocation followed by recreation. Decision 0006 explicitly deferred
per-user selection.

## Must-Fixes Accepted Into The Design

1. Add a fail-closed exact Assistant-route-authority plus conversation-memory
   port operation. Generic route methods remain unchanged and cannot discard
   the authority or memory.
2. Bind a selection to the organization route override identity and version,
   not only gateway/model text, so A -> B -> A never revives stale authority.
3. Make catalog activations soft-disabled and identity-bearing. A re-enabled
   model receives a new activation UUID, so old conversation references remain
   invalid. Repeat organization ownership in database keys and add concurrent
   disable/select/turn coverage.
4. Treat the deployment default as a synthetic read-only choice. It creates no
   fake organization profile or catalog row and offers no alternate choices.
5. Treat each catalog row as an administrator assertion of chat eligibility.
   Alternate selection is unavailable while the effective route has a
   model-specific reasoning option; only the exact default route may carry it.
6. Revalidate route generation, catalog activation, and profile ownership in
   the integration adapter immediately before model-client construction. Use a
   safe ordinary-user DTO, audit catalog changes and effective route identity
   without payloads, and record the decision superseding decision 0006 in part.

## Committed Recommendation

Proceed with the full governed picker rather than the cheaper read-only phase,
because the project owner explicitly requested a Northstar-like selection
experience. Implement every must-fix above before enabling selection.

The browser submits only an opaque catalog activation UUID, or `null` for the
current default. Core resolves this to a sealed server-created Assistant route
authority. An already-dispatched turn may complete against its validated
snapshot; the next turn must observe any control-plane change. Conversation
locking linearizes concurrent picker mutation and turn creation.

## Rejected Alternative

A read-only current-model pill plus composer polish is the cheaper safe phase.
It is rejected for this increment because it does not provide the requested
conversation model choice. A browser-supplied gateway/model pair is also
rejected because it bypasses the administrator-owned catalog boundary.

## Scope Limits

This verdict does not authorize cross-gateway selection, arbitrary model IDs,
provider discovery exposure, reasoning/temperature controls, automatic
fallback, recommendations, attachments, custom agents, or any weakening of
non-Assistant AI workload routing.
