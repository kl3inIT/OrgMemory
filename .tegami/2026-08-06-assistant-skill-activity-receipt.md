---
packages:
  orgmemory: patch
subject: Show Assistant Skill activity without a blank wait
---

## Improvements

The Assistant now keeps its progress state visible until answer text appears
and shows a compact, current-turn receipt when it successfully activates a
governed Skill. Skill titles are bounded plain text, denied or failed Skills
remain unnamed, and the receipt clears safely when a turn ends without an
answer.
