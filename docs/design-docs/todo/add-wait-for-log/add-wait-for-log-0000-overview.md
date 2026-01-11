# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Overview

## Purpose

The purpose of this document is to provide an overview for the new functionality and explain all the supporting
documents that, together, represent the full design document.

## Checklist

### Design Document Completion

- [x] DSL / User Description complete (0100)
- [x] Implementation plan complete (0200)
- [ ] Documentation notes prepared (0600)

### Test Planning

Test requirements are derived from the implementation plan (0200) and existing project testing standards
(`docs/project-standards/testing/`). The test documents (0300, 0400, 0500) serve as placeholders for tracking
test implementation progress during development:

- [ ] Unit tests implemented per implementation plan (0300) - **Plan reviewed 2026-01-11 (23 gaps fixed)**
- [ ] Functional tests implemented per implementation plan (0400) - **Plan reviewed 2026-01-11 (45 gaps addressed across 6 reviews - COMPLETE)**
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

### Unit Test Plan Review (2026-01-11)

The unit test plan (0300) was reviewed against the implementation plan (0200) for 100% unit test coverage. The review
identified **23 gaps** organized by severity:

**Critical Gaps (5)** - Must fix before implementation:
1. JSON format mismatch in mock outputs (Gap #1)
2. Method signature mismatch in validateServicesExist() (Gap #2)
3. Missing getServiceExitCode() explicit tests (Gap #3)
4. fetchServiceLogs() return type mismatch (Gap #4)
5. Phase 0 LogsConfig modification tests missing (Gap #18)

**Moderate Gaps (7)** - Should fix before implementation:
5. LogPatternMatcher.compilePattern() null input test (Gap #5)
6. parseIntProperty() whitespace handling inconsistency (Gap #6)
7. parseBooleanProperty() non-standard values coverage (Gap #7)
13. waitForLogPatterns() null config validation (Gap #13)
14. InterruptedException handling test (Gap #14)
15. RejectCheckResult inner class tests (Gap #15)
16. WaitForLogResult secondary constructor (Gap #16)
17. ExecutionException unwrapping test (Gap #17)

**Minor Gaps (11)** - Can fix during implementation:
8. Warning test fragility (Gap #8)
9. Missing import statements (Gap #9)
10. getRecentLogs() test incomplete (Gap #10)
11. Empty compose project specific error message (Gap #11)
12. CheckAllServicesResult factory methods integration (Gap #12)
19. WaitForLogConfig.toString() format verification (Gap #19)
20. LogPatternMatcher private constructor coverage (Gap #20)
21. PatternMatch inner class test coverage (Gap #21)
22. RECENT_LOG_LINES_FOR_ERROR constant verification (Gap #22)
23. WaitForLogConfigBuilder private constructor coverage (Gap #23)

The unit test plan (0300) has been updated with:
- Checklist items cross-referencing all gaps
- Actionable test code corrections for all gaps
- Complete test specifications for 15+ test classes covering all implementation phases

### Functional Test Plan Review (2026-01-11)

The functional test plan (0400) was reviewed against the implementation plan (0200) for 100% functionality coverage.
The review identified **13 gaps** organized by severity:

**Critical Gaps (5)** - Tests listed in checklist but NO test code provided:
1. Gap #FT-1: Action configuration style test missing (only Closure style tested)
2. Gap #FT-2: Invalid regex pattern error test missing
3. Gap #FT-3: Orphaned rejectPatterns warning test missing
4. Gap #FT-4: pollSeconds > timeoutSeconds warning test missing
5. Gap #FT-5: Non-string pattern type validation test missing

**Moderate Gaps (4)** - Additional coverage needed:
6. Gap #FT-6: Multiple stacks with different waitForLog configurations not tested
7. Gap #FT-7: System property JSON format not verified for JUnit extension parsing
8. Gap #FT-8: Service names with special characters (hyphens, underscores) not tested
9. Gap #FT-9: Unicode patterns test listed in checklist but no explicit test provided

**Minor Gaps (4)** - Edge cases:
10. Gap #FT-10: Empty string pattern edge case
11. Gap #FT-11: Null pattern in list edge case
12. Gap #FT-12: waitForLog without java plugin scenario
13. Gap #FT-13: waitForLog property presence check (isPresent)

The functional test plan (0400) has been updated with:
- Review notes documenting all identified gaps
- Checklist items cross-referencing all gaps
- Complete test specifications added in Sections 8, 9, and 10 for all gaps
- 14 new test methods covering all critical, moderate, and minor gaps

### Functional Test Plan Fourth Review (2026-01-11)

A fourth comprehensive review was conducted to ensure 100% functional test coverage against the implementation plan.
The review identified **6 additional gaps** (FT-33 test was missing, plus FT-35 through FT-39):

| Gap ID | Severity | Description |
|--------|----------|-------------|
| FT-33 | Moderate | System property name constants verification - test was missing from previous review |
| FT-35 | Minor | Multiline pattern support with `(?s)` and `(?m)` regex flags |
| FT-36 | Moderate | rejectPatterns behavior with caseInsensitive flag |
| FT-37 | Minor | Provider-based pattern values (dynamic patterns from Gradle providers) |
| FT-38 | Minor | Mixed rejectPatterns configuration (subset of services have reject patterns) |
| FT-39 | Minor | Task description and group verification for composeUp tasks |

The functional test plan (0400) has been updated with:
- New Section 14 containing 6 complete test specifications for gaps FT-33 through FT-39
- Review notes documenting the fourth review findings
- Updated coverage summary showing comprehensive test coverage

### Functional Test Plan Fifth Review (2026-01-11)

A fifth comprehensive review was conducted to ensure complete functional test coverage against the implementation plan.
The review systematically compared every section of the implementation plan (0200) and DSL user description (0100)
against the existing functional tests, identifying **6 additional gaps** (FT-40 through FT-45):

| Gap ID | Severity | Description |
|--------|----------|-------------|
| FT-40 | Minor | Very long regex patterns (1000+ characters) boundary test |
| FT-41 | Minor | Same pattern in both waitForServices and rejectPatterns (edge case) |
| FT-42 | Moderate | Service names with JSON-sensitive characters (quotes in patterns) |
| FT-43 | Minor | Incremental map building with putAll/put methods |
| FT-44 | Moderate | Direct assignment syntax vs .set() method (both DSL styles per 0100) |
| FT-45 | Minor | Empty rejectPatterns map explicit vs convention behavior |

The functional test plan (0400) has been updated with:
- New Section 15 containing 6 complete test specifications for gaps FT-40 through FT-45
- Review notes documenting the fifth review findings
- Implementation verification checklist mapping all 0200 sections to functional tests
- Updated coverage summary showing 100% complete coverage

**Total Functional Test Coverage:**
- **45 gaps** addressed across 6 reviews
- **71+ test methods** covering all DSL configuration scenarios
- **100% functionality coverage** against implementation plan (0200)

### Functional Test Plan Sixth Review (2026-01-11)

A sixth and final comprehensive review was conducted to verify 100% functional test coverage by systematically
comparing every section of the implementation plan (0200) against existing functional test coverage.

**Review Findings:**
- All 17 implementation plan sections mapped to appropriate test coverage
- All 7 WaitForLogSpec properties have complete DSL, convention, wiring, and system property tests
- All 4 DSL syntax styles tested (`.set()`, direct assignment, Closure, Action)
- All validation and warning scenarios covered
- Configuration cache serialization fully tested

**Gaps Evaluated But Not Added:**
| Potential Gap | Reason Not Added |
|---------------|------------------|
| No waitForLog block at all | Implicitly tested by all base compose stack tests |
| rejectPatterns without waitForServices | Covered by existing validation tests |
| Invalid service name not in compose | Integration test concern (requires Docker) |

**Conclusion:**
- **No additional gaps identified** - the functional test plan is complete
- **Plan Status: COMPLETE - Ready for implementation**

The functional test plan (0400) now contains comprehensive specifications for 71+ test methods covering all
DSL configuration scenarios, validation errors, property wiring, system property propagation, configuration
cache compatibility, edge cases, and boundary conditions.
