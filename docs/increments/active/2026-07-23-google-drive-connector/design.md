# Google Drive Connector Design

## Why this one

The previous increment claimed that adding a source is an adapter package, a
`ConnectorSourceProfile` bean, a catalogue entry and a field descriptor — no
migration, no new endpoint, no change to `core`. That claim has never been tested,
because there has only ever been one source. A second adapter either demonstrates
it or exposes where it was wishful.

Google Drive is the right second source rather than a convenient one. It differs
from Slack on every axis the design abstracts over: OAuth-style credential
exchange instead of a bearer token, a file tree instead of a message stream,
per-object ACLs instead of channel membership, and content that has to be
converted before it is text. If the seams hold for Drive they were real seams.

## What the claim actually costs

Two places turn out not to generalise for free, and both were deferred honestly
in the last increment rather than discovered now.

**Credential probing.** `AdminConnectorController` imports `SlackCredentialProbe`
directly and refuses every other source by name. That was correct at one
implementation — an indirection over a single implementation is a guess about the
second. The second has arrived, so the port gets built now, from two real cases
rather than from one and an imagination.

**The wizard.** Its credential step is Slack-shaped: a `xoxb-` placeholder, a list
of Slack scopes, and the sentence "the bot has to be invited to each channel".
Drive needs a multi-line service account key and a different sentence. This is
the same problem the configuration step already solved with a descriptor, so it
gets the same answer.

Nothing else is expected to move. If something does, that is the finding.

## Credential

A Google service account JSON key, stored as one secret string, which is what the
existing per-connection credential model already holds.

The adapter signs an RS256 JWT with the key's private half and exchanges it at
Google's token endpoint for an access token. This needs no dependency: the JDK
signs `SHA256withRSA` and Jackson already reads the key. An OAuth authorisation
code flow would need a redirect endpoint, a client registration, and somewhere to
keep a refresh token — three things this increment would have to invent, for a
credential that is worse for a server-side crawl than a service account is.

Domain-wide delegation is optional and expressed as an impersonated subject in the
connection's own configuration, because whether a deployment has granted it is a
fact about their Workspace, not about this adapter.

## Connection key

Slack's key is the workspace id, which the probe reports so an administrator never
looks it up. Drive's equivalent is the Workspace domain, which
`about.get?fields=user` returns. One connection is one Google Workspace, which
parallels one Slack workspace and keeps the key human-recognisable.

## Access

`files.list` returns permissions inline when asked for them, so one page of files
carries its own ACL. Each Drive permission maps onto a grant:

| Drive permission | Grant |
| --- | --- |
| `type=user` with an email | `SOURCE_USER` |
| `type=group` with an email | `SOURCE_GROUP` |
| `type=domain` | `SOURCE_GROUP` keyed on the domain |
| `type=anyone` | nothing |

The domain group's membership is every user the crawl observed at that domain.
The Drive API cannot enumerate a domain's users — that is the Admin SDK — so this
under-grants: a file shared with everyone at a company is retrievable by the
employees this crawl has seen holding a permission somewhere, not by those it has
not. Under-granting is the direction a permission-aware system is allowed to be
wrong in, and it is recorded here so nobody reads the behaviour as a bug later.

`type=anyone` grants nothing on purpose. A public link is a statement about people
outside the organization, and translating it into an internal grant would widen
access on the strength of a setting that says nothing about who inside may read.

## Content

Google's own formats are exported to text; plain text files are downloaded; every
other type is skipped. A skipped file is not a gap in the enumeration — it was
never in this adapter's universe — so it does not withdraw the completeness claim.
A document later converted to a format this adapter does not index stops being
mentioned and is retired, which is correct: it is no longer answerable.

The content revision is a hash of the extracted text, as for Slack, so an edit
that does not change the text costs nothing and a `modifiedTime` that moves for a
permission change does not re-materialize.

## Completeness

The same caution as Slack, for the same reason: claiming completeness authorizes
retiring everything unmentioned. The claim is withdrawn when a folder filter is
configured, when a drive or folder could not be listed, or when a file bound was
hit — each of which is indistinguishable downstream from a mass deletion.
