# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Overview

## Purpose

The purpose of this document is to provide an overview for the new functionality and explain all the supporting
documents that, together, represent the full design document.

## Checklist

### Design Document Completion

- [x] DSL / User Description complete (0100)
- [x] Implementation plan complete (0200)
- [x] Documentation notes prepared (0600) - **Plan reviewed 2026-01-13 (47 gaps addressed across 11 reviews)**

### Test Planning

Test requirements are derived from the implementation plan (0200) and existing project testing standards
(`docs/project-standards/testing/`). The test documents (0300, 0400, 0500) serve as placeholders for tracking
test implementation progress during development:

- [ ] Unit tests implemented per implementation plan (0300) - **Plan reviewed 2026-01-11 (23 gaps fixed)**
- [ ] Functional tests implemented per implementation plan (0400) - **Plan reviewed 2026-01-11 (45 gaps addressed across 6 reviews - COMPLETE)**
- [ ] Integration tests implemented per implementation plan (0500) - **Plan reviewed 2026-01-11 (28 gaps addressed across 5 reviews - COMPLETE)**

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

### Integration Test Plan Review (2026-01-11)

The integration test plan (0500) was reviewed against the implementation plan (0200) and DSL user description (0100)
for 100% DSL/usage/options coverage. The review identified **8 gaps** in the first review:

**First Review Gaps (8):**

| Gap ID | Severity | Description |
|--------|----------|-------------|
| IT-1 | Critical | No test for service crash during wait (container exits unexpectedly) |
| IT-2 | Critical | No test for unknown service name validation (typo detection) |
| IT-3 | Critical | No test for regex patterns with special/JSON-sensitive characters |
| IT-4 | Moderate | No explicit test for empty compose project detection |
| IT-5 | Moderate | No test verifying orphaned rejectPatterns warning is logged |
| IT-6 | Moderate | No test for pollSeconds > timeoutSeconds warning edge case |
| IT-7 | Minor | Progress interval logging output format not verified |
| IT-8 | Minor | Container restart behavior during wait not documented/tested |

### Integration Test Plan Second Review (2026-01-11)

A second comprehensive review was conducted to ensure 100% DSL/usage/options coverage. The review identified
**11 additional gaps** (IT-9 through IT-19):

**Critical Gaps (3):**

| Gap ID | Description | Resolution |
|--------|-------------|------------|
| IT-9 | No explicit test for default convention values | Added `wait-log-defaults/` scenario |
| IT-10 | Error message format not verified against documented format | Added error format assertions |
| IT-11 | Configuration cache verification not explicit | Added `wait-log-config-cache/` scenario |

**Moderate Gaps (4):**

| Gap ID | Description | Resolution |
|--------|-------------|------------|
| IT-12 | Verbose output format not verified | Added verbose output assertions |
| IT-13 | State file waitForLog metadata not verified | Added state file assertions |
| IT-14 | System property JSON format not verified in METHOD lifecycle | Added property format assertions |
| IT-15 | No test for mixed ready states | Added `wait-log-mixed-ready/` scenario |

**Minor Gaps (4):**

| Gap ID | Description | Resolution |
|--------|-------------|------------|
| IT-16 | No test for Unicode regex patterns | Added to `wait-log-regex-special/` |
| IT-17 | No test for very long regex patterns | Added to `wait-log-regex-special/` |
| IT-18 | Reject pattern priority not verified | Added timing assertion |
| IT-19 | RECENT_LOG_LINES_FOR_ERROR constant not verified | Added to error message assertions |

### Integration Test Plan Third Review (2026-01-11)

A third comprehensive review was conducted to verify output format verification and error message structure
coverage. The review identified **5 additional gaps** (IT-20 through IT-24):

| Gap ID | Severity | Description |
|--------|----------|-------------|
| IT-20 | Minor | Verbose output format assertions incomplete (`[FOUND]`/`[NOT FOUND]` markers) |
| IT-21 | Minor | Progress interval output format not explicitly verified |
| IT-22 | Moderate | Error message structure verification incomplete (section headers, hints) |
| IT-23 | Minor | System property constant names not explicitly verified |
| IT-24 | Minor | State file waitForLog field structure not explicitly documented |

### Integration Test Plan Fourth Review (2026-01-11)

A fourth review was conducted to verify edge cases and assertion consistency. The review identified
**3 additional gaps** (IT-25 through IT-27):

| Gap ID | Severity | Description |
|--------|----------|-------------|
| IT-25 | Minor | RECENT_LOG_LINES_FOR_ERROR exact value (10) verification inconsistent across assertions |
| IT-26 | Minor | No test for first-poll immediate success (pattern already in logs at first check) |
| IT-27 | Minor | Multiple services with identical pattern strings not tested (per-service tracking) |

### Integration Test Plan Fifth Review (2026-01-11)

A fifth review was conducted to verify convention value assertions match DSL specifications. The review identified
**1 additional gap** (IT-28):

| Gap ID | Severity | Description |
|--------|----------|-------------|
| IT-28 | Minor | `progressIntervalSeconds` default assertion incorrect - test checked for `null` but DSL convention is `0` |

**Total Integration Test Coverage:**
- **28 gaps** addressed across 5 reviews
- **19 scenarios** covering CLASS and METHOD lifecycles
- **100% DSL/usage/options coverage** against implementation plan (0200)
- **Phase 7** added for explicit configuration cache verification
- Output format and error message structure now fully verified
- Edge cases (first-poll success, identical patterns per-service) now covered
- Default convention value assertions now correct

**Conclusion:**
- **Plan Status: COMPLETE - Ready for implementation**

The integration test plan (0500) now contains comprehensive specifications for 19 test scenarios covering:

### Documentation Plan Review (2026-01-12)

The documentation plan (0600) was reviewed and restructured to provide actionable, section-by-section guidance for
documentation updates. The review identified **8 gaps**:

| Gap ID | Severity | Description |
|--------|----------|-------------|
| DOC-1 | Moderate | Nested code fence issue in Section 2 (diagram rendering) |
| DOC-2 | Moderate | Missing verbose output format verification (Section 6a added) |
| DOC-3 | Moderate | Missing periodic progress logging output format (Section 6b added) |
| DOC-4 | Minor | Section 7 missing actual implementation file paths |
| DOC-5 | Moderate | Missing Performance Considerations section (Section 14 added) |
| DOC-6 | Moderate | Missing Known Limitations section (Section 15 added) |
| DOC-7 | Minor | Missing Test Framework Extensions integration verification (Section 16 added) |
| DOC-8 | Minor | Section numbering gaps for CHANGELOG/README (now Sections 17-18) |

**Key Findings:**

1. **File name verification**: Confirmed `usage-docker-test.md` is the correct filename (plan was accurate)

2. **Existing coverage identified**: The `usage-docker-test.md` file already contains a comprehensive `waitForLog`
   section (~lines 511-627) covering properties table, pattern matching, common patterns, and error messages

3. **Missing items identified** (original findings remain valid):
   - Docker Compose v2+ requirement note
   - Lifecycle support clarification table
   - Execution order visual diagram
   - Reject pattern error output format
   - `usage-docker-project.md` missing properties: `rejectPatterns`, `caseInsensitive`, `verbose`,
     `progressIntervalSeconds`
   - Cross-reference from `usage-docker-workflows.md` to wait block documentation

4. **CHANGELOG requirements**:
   - Breaking change: execution order changed from `waitForHealthy` → `waitForRunning` to
     `waitForRunning` → `waitForHealthy` → `waitForLog`

**Structure Applied:**
- 18 numbered sections with specific locations, current state, and actionable updates
- Verification checklists for existing content
- Ready-to-copy markdown content for each addition
- Clear file paths and line number references
- Implementation file paths for error message verification

**Updates Made:**
- Fixed Section 2 diagram markdown to avoid nested code fence rendering issues
- Added Section 6a: Verbose output format verification
- Added Section 6b: Periodic progress logging output format
- Updated Section 7 with actual implementation file paths
- Added Section 14: Performance Considerations
- Added Section 15: Known Limitations (Container Restart During Wait)
- Added Section 16: Test Framework Extensions integration verification
- Renumbered CHANGELOG and README entries as Sections 17-18
- Added Review Notes section to documentation plan
- Reorganized checklist with "Additional Documentation Items" section

**Conclusion:**
- **Plan Status: In progress - Third review added integration test README updates**

### Documentation Plan Third Review (2026-01-13)

The documentation plan (0600) was reviewed a third time to identify integration test README files requiring updates.
The review identified **8 additional documentation files**:

| Gap ID | Severity | Description |
|--------|----------|-------------|
| DOC-9 | Moderate | `plugin-integration-test/README.md` missing `waitForLog` in "What Gets Tested" |
| DOC-10 | Moderate | `plugin-integration-test/dockerTest/README.md` missing `waitForLog` in multiple sections |
| DOC-11 | Moderate | `plugin-integration-test/dockerTest/verification/README.md` missing `waitForLog` scenarios |
| DOC-12 | Minor | `plugin-integration-test/dockerTest/verification/mixed-wait/README.md` missing `waitForLog` reference |
| DOC-13 | Moderate | `plugin-integration-test/dockerTest/examples/README.md` missing `waitForLog` in decision guide |
| DOC-14 | Moderate | `plugin-integration-test/dockerProject/README.md` missing `waitForLog` properties in matrix |
| DOC-15 | Minor | `plugin-integration-test/dockerWorkflows/README.md` missing `waitForLog` support note |
| DOC-16 | Minor | `plugin-integration-test/buildSrc/README.md` - conditional if new validators added |

**Updates Made:**
- Added Sections 20-27 for integration test README updates
- Added "Integration Test README Updates" section to checklist with 17 sub-tasks
- Added third review notes to documentation plan

**Updated Plan Status:**
- **Plan Status: COMPLETE - Ready for implementation**

The documentation plan (0600) now contains actionable specifications for updating 4 usage documentation files,
8 integration test README files, plus CHANGELOG and README, with 27 sections covering all required updates.

### Documentation Plan Fourth Review (2026-01-13)

The documentation plan (0600) was reviewed a fourth time to ensure complete coverage of all integration test README
files. The review scanned all 23 README.md files in `plugin-integration-test/` directory structure.

**Files added to plan:**

| Gap ID | Severity | Description |
|--------|----------|-------------|
| DOC-17 | Minor | `wait-healthy/README.md` comparison table needs `waitForLog` column |
| DOC-18 | Minor | `wait-running/README.md` comparison table needs `waitForLog` column |

**New sections added:**
- Section 28: Individual verification scenario comparison table updates
- Section 29: Clarification - NEW scenario READMEs created during integration test implementation
- Section 30: Files explicitly NOT requiring updates (with justifications)

**Files explicitly excluded (with justification):**
- `docker/README.md` - Documents docker task features (build/tag/save/publish), not compose/test features
- Individual example READMEs - Covered by examples/README.md decision guide
- `basic/README.md`, `lifecycle-class/README.md`, `lifecycle-method/README.md` - Different concerns
- `existing-images/README.md`, `logs-capture/README.md` - Unrelated to wait mechanisms

**Conclusion:**
- **Plan Status: COMPLETE - Ready for implementation**

The documentation plan (0600) now contains 30 sections covering:
- 4 usage documentation files
- 10 integration test README files (8 updates + clarification for new files + exclusion list)
- 2 project-level files (CHANGELOG, README)
- Explicit documentation of files NOT requiring updates

### Documentation Plan Fifth Review (2026-01-13)

A fifth review was conducted to verify coverage of port de-confliction tables, feature matrices, and decision guide
tables in integration test READMEs. The review identified **4 additional gaps** (DOC-19 through DOC-22):

| Gap ID | Severity | Description |
|--------|----------|-------------|
| DOC-19 | Moderate | Port de-confliction tables need wait-log scenario port allocations |
| DOC-20 | Moderate | dockerProject Test Configuration feature matrix needs `waitForLog` rows |
| DOC-21 | Minor | dockerTest README decision guide table needs explicit `waitForLog` column format |
| DOC-22 | Minor | examples/README.md decision guide table needs explicit `waitForLog` column format |

**Updates Made:**
- Added port allocation table update requirement to Section 20
- Added explicit Test Configuration feature matrix format to Section 25
- Added explicit decision guide table format to Sections 21 and 24
- Added `multi-service/README.md` and `docker/scenario-99/README.md` to Section 30 (files NOT requiring updates)
- Updated checklist with specific sub-tasks for matrix and table updates

**Updated Plan Status:**
- **Plan Status: In progress - Sixth review adds additional docs files**

### Documentation Plan Sixth Review (2026-01-13)

A sixth review was conducted to scan the entire codebase for additional README.md files and documentation that needs
updates for the `waitForLog` feature. The review identified **3 additional gaps** (DOC-23 through DOC-25):

| Gap ID | Severity | Description |
|--------|----------|-------------|
| DOC-23 | Moderate | `docs/usage/README.md` - Usage documentation index file missing `waitForLog` references |
| DOC-24 | Moderate | `docs/usage/spock-junit-test-extensions.md` - Test extensions guide missing `waitForLog` properties |
| DOC-25 | Low | IDE templates (`docs/ide-templates/`) only show `waitForHealthy` examples |

**Additional Finding:**
- Section 30 incorrectly listed `multi-service/README.md` as excluded, but this file does not exist (directory exists
  without README). Corrected to note directory has no README.

**New Sections Added:**
- Section 31: `docs/usage/README.md` - Usage documentation index updates
- Section 32: `docs/usage/spock-junit-test-extensions.md` - Test extensions guide updates
- Section 33: IDE Templates (optional, low priority)

**Updated Plan Status:**
- **Plan Status: In progress - Seventh review adds broken link fixes and CLAUDE.md update**

### Documentation Plan Seventh Review (2026-01-13)

A seventh review was conducted to identify broken links and additional documentation files needing updates.

| Gap ID | Severity | Description |
|--------|----------|-------------|
| DOC-26 | Moderate | Pre-existing broken links: 39 files reference `usage-docker-orch.md` which doesn't exist |
| DOC-27 | Moderate | `CLAUDE.md` references `usage-docker-orch.md` instead of `usage-docker-test.md` |

**Key Findings:**

1. **Broken links identified**: 39 files across the codebase reference `usage-docker-orch.md` which does not exist.
   The correct file is `usage-docker-test.md`. While this is a pre-existing issue, it should be fixed as part of
   this documentation update since users navigating to learn about wait mechanisms will encounter broken links.

2. **Priority files for broken link fixes** (in `plugin-integration-test/`):
   - `dockerTest/README.md`
   - `dockerTest/examples/README.md`
   - All individual example READMEs (database-app, web-app, web-app-junit, isolated-tests, isolated-tests-junit,
     stateful-web-app)
   - `dockerWorkflows/README.md`

3. **CLAUDE.md update required**: The project's primary AI coding agent guidance document references the wrong
   documentation file, which could cause confusion during feature implementation.

**Updates Made:**
- Added Section 34: Broken link fixes for integration test READMEs
- Added Section 35: CLAUDE.md update for correct documentation reference
- Updated checklist with "Broken Link Fixes" and "CLAUDE.md Update" sections

**Updated Plan Status:**
- **Plan Status: In progress - Eighth and ninth reviews add additional gaps**

### Documentation Plan Eighth Review (2026-01-13)

An eighth review verified complete coverage of comparison tables, DSL examples, and cross-references. See
`add-wait-for-log-0600-documentation.md` for full details (DOC-28 through DOC-30).

### Documentation Plan Ninth Review (2026-01-13)

A ninth review was conducted to scan the `plugin-integration-test/` directory for incorrectly excluded README files.

| Gap ID | Severity | Description |
|--------|----------|-------------|
| DOC-31 | Moderate | `existing-images/README.md` incorrectly excluded - contains wait strategy documentation |

**Key Finding**: The `existing-images/README.md` file was incorrectly listed in Section 30 as "Unrelated to wait
mechanisms". However, this README contains a "Mixed Wait Strategy" section (lines 93-127), a "Wait Configuration"
section (lines 124-127), and a "Use Case" section (lines 337-360) that all discuss wait strategy selection. This file
MUST be updated to include `waitForLog` as a third wait strategy option.

**Updates Made:**
- Removed `existing-images/README.md` from Section 30 (files not requiring updates)
- Added Section 37 with detailed update instructions for `existing-images/README.md`
- Updated checklist with Section 37 items

**Updated Plan Status:**
- **Plan Status: In progress - Tenth review adds requirements doc and source code comments**

### Documentation Plan Tenth Review (2026-01-13)

A tenth review was conducted to scan the entire codebase for any additional documentation files needing updates,
including files outside the `plugin-integration-test/` and `docs/usage/` directories.

| Gap ID | Severity | Description |
|--------|----------|-------------|
| DOC-32 | Moderate | `uc-7-proj-dev-compose-orchestration.md` - active requirements doc with broken link and outdated DSL |
| DOC-33 | Low | Source code comments reference old `usage-docker-orch.md` filename |
| DOC-34 | Low | Design-docs files outside `done/` directory have broken links |

**Key Findings**:

1. **Requirements/Use Case Document**: The file `docs/design-docs/requirements/use-cases/uc-7-proj-dev-compose-orchestration.md`
   is marked "Status: Implemented" but contains:
   - Broken link to `usage-docker-orch.md` (line 31)
   - Outdated placeholder for `waitForLog` feature ("Complex waiting strategies")
   - Example DSL using non-existent `successWhenLogsMatch` property instead of actual `waitForLog` syntax

2. **Source Code Comments**: Four source files have documentation comments referencing the old filename

3. **Additional Design Docs**: Two files in `docs/design-docs/` outside `done/` have broken links

**Updates Made:**
- Added Section 38: Requirements/Use Case document update
- Added Section 39: Source code comment broken links (optional)
- Added Section 40: Additional design-docs broken links (low priority)
- Updated checklist with new sections

**Updated Plan Status:**
- **Plan Status: In progress - Eleventh review adds specification documents**

### Documentation Plan Eleventh Review (2026-01-13)

An eleventh review was conducted to scan the entire codebase for specification documents and architectural documentation
that may need updates for the `waitForLog` feature. The review identified **3 additional gaps** (DOC-35 through DOC-37):

| Gap ID | Severity | Description |
|--------|----------|-------------|
| DOC-35 | Moderate | Functional Specifications missing waitForLog requirement (fr-26/27 exist for running/healthy) |
| DOC-36 | Moderate | Technical Specifications missing WaitForLogConfig, method specs, DSL examples |
| DOC-37 | Moderate | DSL Architecture Rationale examples only show waitForHealthy, not waitForLog |

**Key Findings:**

1. **Functional Specifications Gap**: `docs/design-docs/specifications/functional-specifications/functional-specifications.md`
   has fr-26 (waitForRunning) and fr-27 (waitForHealthy) but no requirement for waitForLog.

2. **Technical Specifications Gap**: `docs/design-docs/specifications/technical-specifications/technical-specifications.md`
   contains extensive wait mechanism documentation (WaitConfig class, waitForServices method, DSL examples, convention
   defaults) that needs updating for waitForLog.

3. **DSL Architecture Rationale Gap**: `docs/design-docs/dsl-architecture-rationale.md` has DSL examples at lines 159
   and 264 showing only `waitForHealthy` - should also mention `waitForLog` as an option.

**Updates Made:**
- Added Section 41: Functional Specifications document update
- Added Section 42: Technical Specifications document update
- Added Section 43: DSL Architecture Rationale document update
- Updated checklist with "Specification Documents (Sections 41-43)" section

**Updated Plan Status:**
- **Plan Status: COMPLETE - Ready for implementation**

The documentation plan (0600) now contains 43 sections covering:
- 6 usage documentation files (added README.md and spock-junit-test-extensions.md)
- 11 integration test README files (9 updates + clarification for new files + exclusion list)
- 1 requirements/use case document update
- 3 specification/architecture documents (functional specs, technical specs, DSL architecture rationale)
- 2 project-level files (CHANGELOG, README)
- 1 IDE templates section (optional)
- 1 broken link fix section (affecting 9+ files in plugin-integration-test/)
- 1 CLAUDE.md update section
- 1 docs/ broken link fix section
- 1 source code comment broken link section (optional)
- 1 additional design-docs broken link section (low priority)
- Explicit documentation of files NOT requiring updates
- Explicit port allocation and feature matrix table updates

---

The integration test plan (0500) now contains comprehensive specifications for 19 test scenarios covering:
- Basic and advanced pattern matching
- Reject patterns and error handling
- All DSL options (verbose, caseInsensitive, progressIntervalSeconds)
- Combined usage with waitForRunning and waitForHealthy
- METHOD and CLASS lifecycle support
- Service crash detection and unknown service validation
- Configuration cache compatibility
- Default convention values
- Mixed ready states
- Edge cases and boundary conditions
- Output format verification (verbose, progress interval)
- Error message structure verification (timeout, crash, reject)
- First-poll immediate success (IT-26)
- Identical patterns per-service tracking (IT-27)
- Exact constant value verification for RECENT_LOG_LINES_FOR_ERROR (IT-25)
