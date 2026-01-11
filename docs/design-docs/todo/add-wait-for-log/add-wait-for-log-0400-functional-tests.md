# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Functional Tests

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0100-dsl-user-description.md` - DSL syntax and expected behavior
- `add-wait-for-log-0200-implementation.md` - Implementation details

Reference project testing standards:
- `docs/project-standards/testing/functional-testing.md`

## Purpose

Track functional test implementation progress. Test requirements are derived from the implementation plan (0200).

## Checklist

- [ ] Functional tests for `waitForLog` DSL configuration
- [ ] Functional tests for validation error messages
- [ ] Functional tests for property wiring to tasks
- [ ] Functional tests for combined wait blocks (`waitForRunning` + `waitForHealthy` + `waitForLog`)
- [ ] All functional tests pass with Gradle TestKit