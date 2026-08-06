# Git Workflow

## Rule

Never commit directly to `main`/`master`. Before starting any feature or
bug fix, create a new branch first and do the work there.

## Branch naming convention

Each branch type has its own convention doc, kept alongside a matching
subdirectory:

| Type | Pattern | Convention doc |
|---|---|---|
| Feature | `feature/<short-kebab-case-description>` | [`feature/naming-convention.md`](feature/naming-convention.md) |
| Bug fix | `bug/<short-kebab-case-description>` | [`bug/naming-convention.md`](bug/naming-convention.md) |

## Workflow

1. `git checkout master && git pull` (start from an up-to-date `master`).
2. `git checkout -b feature/<desc>` or `git checkout -b bug/<desc>`.
3. Do the work, commit on that branch.
4. Open a PR into `master` — do not push directly to `master`.

Referenced from [`CLAUDE.md`](../../CLAUDE.md) §5 (Key Constraints).
