---
packages:
  orgmemory: patch
subject: Tell people what to do when an Assistant turn fails
---

## Fixes

A failed Assistant turn now ends on a sentence naming what the person who hit it
can do next, instead of one generic message for every cause. An expired gateway
key, a rate limit, a model that is no longer offered, a gateway that did not
answer in time, and a busy assistant are now distinguishable and separately
actionable.

Every message remains a fixed sentence chosen from the failure's category, so a
misconfigured or unusually talkative AI gateway cannot surface its own text,
credentials, or prompt content in the browser. A failure that matches no known
category still ends on the previous generic message.
