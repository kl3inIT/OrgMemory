---
packages:
  orgmemory: patch
subject: Clearer errors, consistent sizes, and a lighter web app
---

# Clearer errors, consistent sizes, and a lighter web app

## Improvements

Admin AI model screens now surface the real error details returned by the
server instead of a generic message. File sizes display with one consistent
unit convention across the app, and Skill upload limits read the same on
every surface. Source upload no longer applies the confidential-classification
department rule to unrelated classifications. The Sources screen loads the
knowledge-graph viewer only when its tab is opened, the assistant re-renders
far less while streaming, and several unused component kits and dependencies
were removed for a smaller bundle.
