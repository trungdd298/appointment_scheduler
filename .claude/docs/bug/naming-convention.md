# Bug Branch Naming Convention

## Pattern

```
bug/<short-kebab-case-description>
```

## Rules

- Lowercase, words separated by hyphens.
- Describes the defect being fixed, not the fix itself (e.g. what's
  broken, not how it's being repaired).
- No ticket numbers, dates, or author initials — the description alone
  should identify the bug.
- Short but specific enough to disambiguate from other bug branches:
  prefer `bug/fix-build-gradle-deps` over `bug/fix-build`.

## Examples

```
bug/fix-build-gradle-deps
bug/dealership-hours-missing-from-schema
bug/expired-hold-not-releasing-bay
bug/technician-qualification-check-skipped
```

Parent doc: [`git-workflow.md`](../git-workflow.md).
