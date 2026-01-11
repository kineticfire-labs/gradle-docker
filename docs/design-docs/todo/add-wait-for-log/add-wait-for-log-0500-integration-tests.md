# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Integration Tests

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0100-dsl-user-description.md` - DSL syntax and expected behavior
- `add-wait-for-log-0200-implementation.md` - Implementation details

Reference project testing standards:
- `docs/project-standards/testing/` - Integration testing requirements
- `plugin-integration-test/README.md` - Integration test layout

## Purpose

Track integration test implementation progress. Test requirements are derived from the implementation plan (0200).

## Checklist

- [ ] Create integration test scenario for `waitForLog`
- [ ] Test with real Docker containers
- [ ] Test timeout behavior
- [ ] Test reject pattern behavior
- [ ] Test verbose logging output
- [ ] Test progress interval logging
- [ ] Verify no lingering containers after tests
- [ ] Test combined wait blocks with real services