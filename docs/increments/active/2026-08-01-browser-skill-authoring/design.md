# Browser Skill authoring

Date: 2026-08-01

## Outcome

Make the existing Assets surface the only place where employees discover and
manage reusable organizational capabilities, while giving Skill authors three
clear browser entry paths:

```text
Assets -> Add asset -> Skill
                       |- Start from scratch
                       |- Upload a skill
                       `- Import from GitHub
```

The creation surface ends at the ordinary governed Asset workspace. It is not
a second Skill catalog and does not replace `/assets?type=SKILL` or
`/assets?scope=MINE&type=SKILL`.

## Product problem

The current `/assets/new` page presents four selectable profiles but no forward
action. It teaches implementation status instead of helping the actor create an
Asset. As more profiles arrive, a full-page chooser would become slower and
would continue to duplicate navigation that belongs in the Assets header.

The current server can import one bounded Skill ZIP into a private Draft, but
the browser cannot use it. A Skill Draft also cannot replace its package after
creation, and GitHub import has no product contract yet.

## Reference study

Onyx Craft informed the interaction model, not the visual theme or domain
boundary. At pinned reference commit
`618b5031bf21463f44e3bed9eb9d5073b806fec0`, and again in current upstream
source reviewed on 2026-08-01, its Skill page exposes a compact Create menu with
Start from scratch, Upload a skill, and Import from GitHub. Upload accepts a
`SKILL.md`, ZIP, or folder, inspects the bundle before editing, and the editor
groups Details, Instructions, Supporting files, and Sharing. GitHub import
previews discovered Skill directories and allows a subset to be imported.

OrgMemory adapts those task choices to its governed Asset Registry:

- keep the existing OrgMemory shell, tokens, shadcn/Radix primitives, and
  responsive PageLayout;
- keep `/assets` as the aggregate catalog and owned workspace;
- create one Asset Draft per selected Skill and preserve Knowledge Space,
  classification, authorization, immutable release, and audit semantics;
- do not copy Onyx's standalone `/skills` catalog or personal-sharing model.

Relevant reference files:

- `tmp/onyx/web/src/views/SkillsPage.tsx`
- `tmp/onyx/web/src/sections/modals/skills/CreateSkillModal.tsx`
- `tmp/onyx/web/src/sections/modals/skills/ImportSkillsFromGitHubModal.tsx`
- `tmp/onyx/web/src/views/SkillEditorPage.tsx`

## Product decisions

### One Assets entry point

Replace the current full-page profile chooser with an `Add asset` dropdown in
the Assets header. Each supported profile may own its own creation route. Skill
opens a creation-only page at `/assets/new/skill`; unsupported profiles remain
disabled in the menu instead of navigating to dead screens.

The Skill creation page offers three task choices. It contains no browse list,
search, ratings, install counts, or ownership tabs because those already belong
to `/assets`.

### Start from scratch

The browser authors the portable Skill manifest fields and instruction body,
then builds a canonical package request through the server. The server remains
the authority for the generated ZIP, package validation, identity, storage,
authorization, and Draft creation. Supporting files may be added without
executing or rendering them.

### Upload and Draft editing

Upload accepts one `SKILL.md`, ZIP, or folder. Folder packaging is a browser
usability concern; server inspection remains authoritative. Inspection returns
safe metadata and a bounded file manifest before the user creates or replaces a
Draft package.

Replacing a mutable Skill Draft uses optimistic concurrency and creates a new
immutable object. Existing Revision and Release rows keep their original
package reference. Reference-aware cleanup may remove only an unreferenced
superseded Draft object; failure to clean up is an observable orphan, never a
reason to mutate or delete an immutable package.

### GitHub import

GitHub import accepts a repository URL plus optional revision and subpath,
previews the discovered `SKILL.md` roots, and lets the actor select one or more
Skills. Every imported Skill becomes its own governed Draft and records source
repository, resolved immutable commit SHA, and source path as provenance.

The browser never accepts, stores, or receives a GitHub token. Public imports
use anonymous server-side reads. Private imports may use an administrator-
managed GitHub App source connection that is already stored encrypted and is
never returned. The server resolves repository access, enforces bounded fetch
and archive rules, and rejects redirects or hosts outside the GitHub boundary.

### Lifecycle

Creation stops at Draft. Existing direct Skill publication remains an explicit
owner-class action and produces immutable Revision and Release records. This
increment does not add mandatory review, ratings, usage totals, rewards, or a
second marketplace.

## Delivery slices

1. Add the category menu and Skill creation hub, remove the dead chooser.
2. Add scratch authoring, upload inspection, Draft creation, and reference-safe
   package replacement.
3. Add GitHub preview/import, provenance, documentation, and full browser QA.

Each slice is one merge-commit PR below 100 changed files. The next slice starts
from the new `origin/main`; commits are never squashed.

## Scope limits

- no CLI redesign;
- no standalone Skill catalog or `/assets/skills` workspace;
- no Prompt Template, Work Instruction, or Capability Pack authoring;
- no automatic publication, review requirement, execution, or tool grant;
- no user-entered GitHub PAT;
- no ratings, usage counters, contribution rewards, or public marketplace.
