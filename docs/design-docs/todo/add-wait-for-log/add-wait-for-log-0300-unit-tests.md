# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Unit Tests

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0200-implementation.md` - Implementation details and component structure

Reference project testing standards:
- `docs/project-standards/testing/unit-testing.md`

## Purpose

Track unit test implementation progress. Test requirements are derived from the implementation plan (0200).

## Checklist

- [ ] Unit tests for `WaitForLogSpec`
- [ ] Unit tests for `WaitForLogConfig`
- [ ] Unit tests for `WaitForLogResult`
- [ ] Unit tests for `LogPatternMatcher`
- [ ] Unit tests for `WaitForLogConfigBuilder`
- [ ] Unit tests for `ComposeStackSpec` (waitForLog extension)
- [ ] Unit tests for `ExecLibraryComposeService.waitForLogPatterns()`
- [ ] Unit tests for `ComposeUpTask` (waitForLog properties)
- [ ] 100% line and branch coverage achieved