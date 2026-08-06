---
packages:
  orgmemory: patch
---

## Changes

An Assistant conversation is now stored once. The transcript that already served
history, replay, rename, and delete is also what the model reads back as prior
context, replacing a second copy that was kept in a table with no organization,
no owner, and no link to the conversation it belonged to.

Prior context is now read in whole question-and-answer turns rather than by
counting messages. The question of the turn currently being answered can no
longer be sent to the model twice, a turn that failed before answering no longer
occupies the window, and the window can no longer begin partway through an
exchange. Deleting a conversation removes its context in the same operation
instead of relying on a separate call.
