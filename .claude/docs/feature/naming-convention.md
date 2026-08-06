# Feature Branch Naming Convention

## Pattern

```
feature/<short-kebab-case-description>
```

## Rules

- Lowercase, words separated by hyphens.
- Describes the capability being added, not the implementation mechanics.
- No ticket numbers or dates — the description alone should identify what
  the branch adds.
- Short but specific enough to disambiguate from other feature branches:
  prefer `feature/hold-confirm-flow` over `feature/booking`.

## Examples

```
feature/hold-confirm-flow
feature/availability-search
feature/resource-unavailability-reconciliation
feature/react-booking-form
feature/vehicle-category-duration-override
```

Parent doc: [`git-workflow.md`](../git-workflow.md).
