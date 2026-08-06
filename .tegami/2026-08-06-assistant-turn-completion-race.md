---
packages:
  orgmemory: patch
subject: Stop losing an Assistant answer that was already on screen
---

## Fixes

An Assistant answer could disappear from a conversation after being delivered.
When two turns of the same conversation finished at the same moment, only one of
them was saved; the other was rolled back and was gone on the next reload, even
though the person asking had watched it arrive. Both are now kept.

Long answers with many sources also render far more cheaply. Each arriving
source used to discard and rebuild the entire answer shown so far, which made a
long, heavily cited reply progressively slower to display and could leave the
page unresponsive while it finished. The answer is now updated in place as its
sources arrive.
