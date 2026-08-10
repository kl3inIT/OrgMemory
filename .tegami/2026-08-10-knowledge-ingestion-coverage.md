---
packages:
  orgmemory: minor
subject: Ingest the document formats organizations already use
---

## Features

Knowledge document upload now accepts CSV, Excel, legacy Word and PowerPoint,
HTML exports, RTF, and OpenDocument files alongside the existing PDF, modern
Office, Markdown, and text formats. Upload and processing limits adapt to each
format instead of applying one global ceiling.

Document processing now preserves headings, paragraphs, tables, spreadsheet
headers, and PDF page provenance through a reusable parser boundary. The
versioned structured policy keeps retries deterministic while table fragments
retain the header context needed for useful answers.
