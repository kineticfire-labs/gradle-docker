# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Documentation Notes

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0100-dsl-user-description.md` - DSL syntax (source for user documentation)

## Purpose

Informal notes on documentation updates, additions, and changes required for the `waitForLog` feature.

This document does NOT contain a formal plan. Documentation should be written alongside implementation.

The overview of the design is at `add-wait-for-log-0000-overview.md`.

## Checklist

### Documentation Updates

- [ ] Update `docs/usage/usage-docker-orch.md` with `waitForLog` section
- [ ] Update `README.md` feature list
- [ ] Update `CHANGELOG.md` with new feature and breaking change

## Documentation Updates Required

The following documentation should be updated when implementing the `waitForLog` feature:

### User Documentation

| File | Updates Required |
|------|------------------|
| `docs/usage/usage-docker-orch.md` | Add `waitForLog` section with DSL syntax, configuration properties, usage examples, common patterns for popular services, and error handling |
| `README.md` | Update feature list to include log-based readiness checks |

### Project Documentation

| File | Updates Required |
|------|------------------|
| `CHANGELOG.md` | Document new `waitForLog` feature and breaking change to wait block execution order (`waitForRunning` -> `waitForHealthy` -> `waitForLog`) |

### Key Documentation Points

1. **DSL Syntax**: Show both `.set()` and direct assignment styles
2. **Pattern Matching**: Explain regex semantics, case sensitivity, AND vs OR matching
3. **Reject Patterns**: Explain fail-fast behavior with examples
4. **Progress Logging**: Document `verbose` and `progressIntervalSeconds` options
5. **Performance**: Warn about high-volume logging services
6. **Composability**: Show examples combining `waitForLog` with `waitForHealthy` and `waitForRunning`
7. **Error Messages**: Document timeout and reject pattern error output formats
8. **Lifecycle Limitation**: Document that `Lifecycle.METHOD` is not supported in initial release
