# Testing Requirements

“Claude Code” execution guide (follow these steps exactly)

- Read this file. Identify impacted modules.
- Before coding: write/adjust unit tests for the intended behavior.
- Implement code. Run unit tests locally. Achieve 100% lines + branches.
- If impossible: add a gap entry and document compensating FT/IT, add coverage exclusion with the gap ID.
- Run all tests. Produce coverage.  Output a concise summary:
   - What changed, why.
   - Tests added/updated.
   - Coverage status (100% or gap IDs).
   - Links to reports and to any gap entries.

## Coverage
- **MUST** achieve **100% unit test coverage** for **lines and branches** across all code.
- **MUST** run a coverage tool (e.g., **JaCoCo** or language-equivalent) automatically in CI after `test`.
- **MUST** publish coverage reports (HTML + XML) as CI artifacts.
- **MUST** fail the build when coverage < 100%, **unless** explicitly excepted (see “Exceptions”).
- **MUST NOT** exclude code from coverage without a linked exception entry.

## Exceptions (unit-test gaps)
- **MUST** document every gap in `docs/design-docs/tech-debt/unit-test-gaps.md` (see "Gap entry template" below).
- **MUST** include: entity, extent of gap, reason, and compensating test plan (functional/integration).
- **MUST** reference the gap entry in the coverage config exclusion (comment or ID).
- **MUST** add/point to compensating tests that cover the behavior end-to-end.

**Gap entry template:**
```md
### <module>/<path>: <ClassOrNs>#<method|function>  <!-- GAP-ID: TG-YYYYMMDD-XXX -->
- Extent: <lines/branches uncovered, %>
- Reason: <why unit coverage is impractical or unsafe>
- Compensating tests: <links to FT/IT cases or plan>
- Owner: <name> | Target removal date: <YYYY-MM-DD>
```

## Unit tests
- MUST write tests for all functions/methods, including non-public (package/private or via test hooks).
- MUST assert behavior, edge cases, and error paths (happy + sad + boundary).
- MUST keep tests deterministic and isolated (no order dependence).
- MUST NOT hit the network, system clock, filesystem, or randomness directly; use fakes/mocks/seeds/clock.
- SHOULD use property-based tests where it adds coverage for input domains.
- SHOULD cover concurrency (race/ordering) via controlled executors or virtual tim

## Flakiness & reliability
- MUST NOT claim "finished" with flaky tests (e.g., intermittent failures).
- MUST quarantine only as a last resort, with a gap entry in `docs/design-docs/testing/unit-test-gaps.md`.
- SHOULD make tests parallel-safe; avoid shared mutable state.

## Generated & unreachable code
- MAY exclude machine-generated code (e.g., build/**, generated/**) with a comment explaining why.
- MUST document any unreachable branches (feature flags, platform guards) as gaps with a sunset plan.