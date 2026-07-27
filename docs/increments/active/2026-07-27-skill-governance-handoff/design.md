# Skill Governance Handoff

Status: active.

## Problem

Folder-first Skill publication now creates a governed Draft, but the authoring
journey stops at a text instruction:

```text
skill publish -> Draft -> "Continue in Governance"
```

The Governance workspace can compare submitted revisions, record review
decisions, publish approved revisions, and manage release availability. It does
not expose the existing Draft-to-review transition, and it renders mutation
controls without first asking the permission authority which actions the
current actor may perform.

## Outcome

- Return a same-origin Governance URL from `orgmemory skill publish`.
- Add a Draft section to the existing Governance workspace.
- Show bounded Skill package identity (digest, archive size, file count, and
  declared compatibility) without downloading or executing the package.
- Let an authorized author submit the exact Draft for review with a required
  change note.
- Ask Core for actor-specific Governance actions and render only authorized
  submit, review, publish, and withdrawal controls.
- Prove the handoff with focused service, CLI, component, and browser tests.

## Authorization Contract

The web client must not infer authority from business role labels or visible
role assignments. A new read-only action-discovery endpoint returns:

```text
canSubmitReview
canReview
canPublish
canWithdraw
```

Core first requires `can_view` for the Asset, then evaluates each action against
the same live OpenFGA boundary used by the mutation itself. A denied or
indeterminate action is reported as unavailable. The mutation endpoints remain
authoritative and repeat authorization; action discovery is UI affordance, not
an authorization grant.

The response contains no denial reason or hidden relationship data. An actor
who cannot view the Asset receives the same not-found behavior as the existing
Asset read.

## UI Contract

- Governance stays inside the shared wide `PageLayout`.
- Draft is the default tab when no revision has been submitted.
- The submit action requires a change note and an explicit confirmation.
- Review actions are hidden when `canReview` is false. An author cannot approve
  their own revision even if they otherwise have reviewer authority.
- Publish and availability actions are hidden unless their corresponding live
  action is available.
- Skill package details come from the already-authorized Draft payload. The UI
  never receives the internal object-storage key and never downloads package
  bytes for Governance display.

## Scope Decisions

- This increment closes the existing Draft -> review -> release handoff; it
  does not create a second Skill-specific lifecycle.
- No Draft package replacement, CLI update/remove, marketplace, ratings,
  browser ZIP upload, automatic approval, or MCP mutation.
- The project owner previously waived a separate Claude Fable 5 pass for this
  sequence. The strongest counterargument is to avoid a new action-discovery
  endpoint and simply let forbidden controls fail. That is rejected because it
  creates misleading UI, unnecessary mutation attempts, and role-derived
  authorization guesses in the browser.

