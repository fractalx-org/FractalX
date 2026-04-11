# Branching & Release Strategy

## Long-lived branches

| Branch | Purpose |
|--------|---------|
| `main` | Stable, released code only. Tagged with `vX.Y.Z` on each release. |
| `develop` | Integration branch. All feature work merges here first. |

## Short-lived branches

| Branch pattern | Purpose | Merges into |
|----------------|---------|-------------|
| `feature/<name>` | New features | `develop` |
| `fix/<name>` | Bug fixes | `develop` (or `main` for hotfixes) |
| `release/X.Y.Z` | Release prep (version bump, changelog, final testing) | `main` + back-merge to `develop` |
| `hotfix/X.Y.Z` | Critical prod fixes | `main` + back-merge to `develop` |

## Release versioning

```
0.MAJOR.MINOR  →  breaking / significant feature / patch
```

- **Minor bump** (`0.3.2 → 0.3.3`): bug fixes, small additions
- **Mid bump** (`0.3.x → 0.4.0`): new significant feature set
- **Major bump** (`0.x → 1.0.0`): stable public API, production-ready

## Flow in practice

```
feature/auth-rewrite  ──┐
feature/plugin-api    ──┤──► develop ──► release/0.4.0 ──► main (tag v0.4.0)
fix/datasource-null   ──┘
```

## Guidelines

- Use tags (e.g. `v0.3.2`) for release snapshots, not archive branches
- Keep feature branches short-lived and close to `develop`
- Never commit directly to `main`
- All changes to `main` must go through a `release/X.Y.Z` or `hotfix/X.Y.Z` branch