# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Overview

## Purpose

The purpose of this document is to provide an overview for the new functionality and explain all the supporting
documents that, together, represent the full design document.

## Checklist

### Design Document Completion

- [ ] DSL / User Description complete (0100)
- [ ] Implementation plan complete (0200)
- [ ] Documentation notes prepared (0600)

### Test Planning

Test requirements are derived from the implementation plan (0200) and existing project testing standards
(`docs/project-standards/testing/`). The test documents (0300, 0400, 0500) serve as placeholders for tracking
test implementation progress during development:

- [ ] Unit tests implemented per implementation plan (0300)
- [ ] Functional tests implemented per implementation plan (0400)
- [ ] Integration tests implemented per implementation plan (0500)

## Overview

The `waitForLog` block is a new readiness check mechanism for the `dockerTest` DSL that waits for specific log output
from containers before considering them ready. It complements the existing `waitForHealthy` (waits for Docker health
check to pass) and `waitForRunning` (waits for container state to be "running") blocks. Unlike those blocks which
accept a simple list of service names, `waitForLog` requires per-service configuration mapping each service to one or
more regex patterns that must appear in its logs. This feature is useful for services that don't have health checks or
require application-specific readiness indicators beyond basic container status.

## Design Documents

The design document consists of these documents:

1. `add-wait-for-log-0000-overview.md`: This document, which provides an overview of the design.
2. `add-wait-for-log-0100-dsl-user-description.md`: Explains the functionality that should be achieved and how the user
   will interact with the new functionality, specifically describing the new DSL additions.
3. `add-wait-for-log-0200-implementation.md`: Defines the implementation plan to achieve the desired functionality.
4. `add-wait-for-log-0300-unit-tests.md`: Defines unit tests.
5. `add-wait-for-log-0400-functional-tests.md`: Defines functional tests.
6. `add-wait-for-log-0500-integration-tests.md`: Defines integration tests.
7. `add-wait-for-log-0600-documentation.md`: Notes on documentation updates/additions/changes (informal notes, not a
   formal plan).

**Note**: Unit tests, functional tests, and integration tests do not have separate plan documents. Test requirements
should be derived from the implementation plan and existing project testing standards documented in
`docs/project-standards/testing/`.

## Review Notes

### Implementation Plan Review (2026-01-10)

The implementation plan (0200) was reviewed and updated to address the following findings:

1. **Full Lifecycle Support Confirmed**: The `waitForLog` feature supports both `Lifecycle.CLASS` and
   `Lifecycle.METHOD` from the initial release. Both documents (0100 and 0200) were updated to:
   - Remove all validation code that would reject METHOD lifecycle
   - Remove Section 12.6 (Lifecycle.METHOD Validation) as it is no longer needed
   - Update Section 12.5 to implement `performWaitForLog()` for both METHOD and CLASS extensions
   - Update Phase 5 integration tests checklist to include METHOD lifecycle tests for `waitForLog`

2. **JUnitComposeService Implementation**: Section 12.5.5 was updated with explicit implementation code
   for the `waitForLogPatterns()` delegation method, including verification commands and test requirements.

3. **System Property Format Verification**: Added verification step in Pre-Implementation checklist to
   confirm that existing `waitForRunning` and `waitForHealthy` system property formats match the parsing
   code in Section 12.5.

4. **Guava Dependency**: Changed from verification to explicit action item in Pre-Implementation checklist.

5. **Code Style Clarification**: Added note in Section 12.5 about Java vs Groovy code style.

6. **Breaking Change Visibility**: Updated Phase 6 Documentation checklist to explicitly call out the
   breaking change for CHANGELOG.md.
