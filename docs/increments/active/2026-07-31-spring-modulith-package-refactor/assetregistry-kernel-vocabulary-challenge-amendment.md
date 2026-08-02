# Asset Registry Kernel Vocabulary Challenge Amendment

## New Executable Evidence

The first binding slice was implemented as characterization tests followed by
moving `AssetType`, `AssetPortfolioState`, `AssetRole`, and the three Asset
exceptions into a closed `assetregistry.kernel` module.

The two focused boundary tests passed, but the repository's existing
`ApplicationModules.verify()` failed with this exact violation:

```text
Invalid sub-module reference from module 'assistant' to module
'assetregistry.kernel' (via
c.o.c.a.AssistantAssetToolService -> c.o.c.a.k.AssetType)!
```

This is consistent with the increment's earlier catalog challenge: an unrelated
top-level module cannot consume a nested module directly. `AssetType` is used by
the top-level Assistant module as well as Asset Registry and HTTP adapters.

## Required Correction

Re-open only the vocabulary ownership and PR-1 sequence. Preserve the binding
transaction-cluster decision for Asset/role/outbox/readiness unless this new
evidence disproves it.

Adversarially choose between at least:

1. Parent-owned `assetregistry::api` named interface in an ordinary subpackage,
   with Kernel depending on that named interface when the ledger moves.
2. Keeping vocabulary in the Asset Registry root package despite the directory
   cleanup goal.
3. An explicit parent facade/value translation that prevents Assistant from
   importing Kernel.
4. Any demonstrably valid alternative.

Do not propose opening Kernel, ignoring `modules.verify()`, duplicating enums,
or adding an allowlist that Spring Modulith cannot honor. State:

- revised exact package and ownership for all six types;
- exact annotations and dependency directions;
- revised PR 1 public-surface test and path cap;
- whether Kernel should be introduced in PR 1 or PR 2;
- necessary updates to the earlier verdict;
- strongest counterargument and rejected alternative.

Operate read-only. Inspect the failed implementation and existing parent named
interfaces such as `knowledge::catalog` and `knowledge::search`. Return a
binding amendment, not general advice.
