---
packages:
  orgmemory: patch
subject: Reliable graph indexing recovery and hardened graph export
---

## Fixes

Cancelling a graph indexing job while it is processing no longer wedges the
indexing queue after its lease expires; cancelled jobs settle into a terminal
state and the queue keeps flowing. Connector documents whose embedding or
publication step failed mid-run now retry cleanly instead of leaving a staged
revision that references deleted content. Short knowledge queries with an
empty trusted keyword plan no longer fail with an internal error. Graph CSV
exports neutralize leading spreadsheet formula characters so exported cells
cannot execute as formulas when opened in Excel.
