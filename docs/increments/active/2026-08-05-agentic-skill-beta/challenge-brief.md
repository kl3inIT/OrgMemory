# Architecture Challenge Brief

## Proposal

Add a native, read-only Skill activation loop to the existing Assistant. Keep
the Asset Registry as the only governed Skill registry and reuse its exact
release authorization and package storage contracts.

## Material decisions under review

- domain ownership: runtime view inside the closed Skill package profile;
- authorization: live actor checks at search, activation, and resource read;
- execution boundary: no scripts or arbitrary tools without a sandbox;
- parity scope: progressive disclosure now, package execution only in clients.

## Strongest counterproposal

Introduce a separate general-purpose agent runtime now, compatible with
filesystem Skills and shell/code execution. This gives faster parity with
OpenCode-style agents, avoids coupling the Assistant to Asset Registry package
details, and could later host more autonomous loops.

## Evidence requested

- whether a second registry duplicates existing Skill identity and governance;
- whether server execution can be made safe without a sandbox;
- whether Spring AI 2.0 supports a bounded native tool loop;
- whether external agents already have an exact-package execution handoff.

