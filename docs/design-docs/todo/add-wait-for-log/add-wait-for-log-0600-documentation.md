# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Documentation

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0100-dsl-user-description.md` - DSL syntax and user-facing behavior
- `add-wait-for-log-0200-implementation.md` - Implementation details

## Purpose

Define documentation updates required for the `waitForLog` feature. This includes updates to internal developer
documentation for plugin usage.

**Target audience**: Internal developers using the plugin DSL (not external users).
**Documentation style**: Informal, concise, example-based, from the user's perspective.

## Review Notes (2026-01-12)

This documentation plan was reviewed and updated to address the following findings:

1. **Nested code fence issue in Section 2**: Fixed markdown rendering problem where diagram was inside nested code
   blocks. Changed to use indented code block format.

2. **Missing checklist items**: Added items for:
   - Verbose output format verification (Section 6a)
   - Periodic progress logging output format (Section 6b)
   - Performance Considerations documentation (Section 14)
   - Known Limitations / Container Restart During Wait (Section 15)
   - Test Framework Extensions integration verification (Section 16)

3. **Implementation file paths for verification**: Section 7 now specifies actual relative paths to implementation
   files for error message verification.

4. **Section numbering**: Added explicit section numbers for CHANGELOG (Section 17), README (Section 18), and new
   sections 14-16.

5. **Cross-reference anchor verification**: Verified that `#container-readiness-waiting-for-services` anchor matches
   the actual heading in `usage-docker-test.md` (line 438).

6. **Checklist reorganization**: Added "Additional Documentation Items" section to checklist for Performance
   Considerations and Known Limitations.

## Review Notes (2026-01-13)

This documentation plan was reviewed a second time and updated to address additional findings:

1. **Section 1 verification checklist expanded**: Added verification items for:
   - Wait Options Overview table includes `waitForLog` entry
   - Execution order statement reflects NEW order (`waitForRunning` → `waitForHealthy` → `waitForLog`)
   - Regex escaping guide table is present and accurate

2. **Section 2 and 3 markdown issues**: Removed nested code fences that would cause rendering issues. Sections now
   use proper indented code block format.

3. **Removed Section 1a**: Breaking change notes not needed - plugin has not been deployed yet, no users to migrate.

4. **Added Section 6c**: Configuration warnings documentation (`pollSeconds > timeoutSeconds`, orphaned
   `rejectPatterns`).

5. **Added Section 6d**: Total timeout calculation documentation for combined wait blocks.

6. **Added Section 19**: Troubleshooting section updates for `waitForLog`-specific issues.

7. **Section 10-13 clarification**: Added note clarifying these sections document `dockerProject` DSL test block
   properties, not the nested `waitForLog` block (which is already documented in `usage-docker-test.md`).

8. **Removed `usage-docker.md` from scope**: Confirmed this file documents Docker image operations (build, tag, save,
   publish) and does NOT need updates for `waitForLog` which is a compose/testing feature.

## Review Notes (2026-01-13 - Third Review)

This documentation plan was reviewed a third time to identify integration test README files requiring updates:

1. **Scanned `plugin-integration-test/` directory**: Identified 23 README.md files across the integration test
   structure that document integration test scenarios, wait mechanisms, and DSL usage.

2. **Added Sections 20-27**: New sections for integration test README updates:
   - Section 20: `plugin-integration-test/README.md` - Main aggregator README
   - Section 21: `plugin-integration-test/dockerTest/README.md` - dockerTest mid-level aggregator
   - Section 22: `plugin-integration-test/dockerTest/verification/README.md` - Verification tests overview
   - Section 23: `plugin-integration-test/dockerTest/verification/mixed-wait/README.md` - Mixed wait documentation
   - Section 24: `plugin-integration-test/dockerTest/examples/README.md` - Examples overview
   - Section 25: `plugin-integration-test/dockerProject/README.md` - dockerProject DSL test configuration
   - Section 26: `plugin-integration-test/dockerWorkflows/README.md` - Workflows wait mechanism support
   - Section 27: `plugin-integration-test/buildSrc/README.md` - Conditional, if new validators added

3. **Updated checklist**: Added "Integration Test README Updates" section with 8 file update items and 17 specific
   sub-tasks.

4. **Key findings from README analysis**:
   - All wait mechanism documentation currently only mentions `waitForHealthy` and `waitForRunning`
   - Decision guide tables need `waitForLog` column added
   - Verification scenario tables need new `waitForLog` test scenarios listed
   - `dockerProject` feature matrix needs `waitForLog` properties added

5. **Excluded from scope** (re-evaluated in fourth review):
   - Individual scenario README files for NEW scenarios (e.g., `wait-log-basic/README.md`) - these will be created
     fresh during integration test implementation (covered in 0500-integration-tests.md)
   - Top-level project `README.md` - already covered in Section 18

## Review Notes (2026-01-13 - Fourth Review)

This documentation plan was reviewed a fourth time to identify additional integration test README files requiring
updates. The review scanned all 23 README.md files in `plugin-integration-test/` directory structure.

1. **Identified 23 README.md files** across the integration test structure

2. **Re-evaluated individual verification scenario READMEs**: Found that `wait-healthy/README.md` and
   `wait-running/README.md` contain comparison tables that should reference `waitForLog` as a third option.

3. **Added Section 28**: Individual verification scenario comparison table updates for:
   - `plugin-integration-test/dockerTest/verification/wait-healthy/README.md`
   - `plugin-integration-test/dockerTest/verification/wait-running/README.md`

4. **Added Section 29**: Clarification that NEW scenario READMEs (wait-log-*) will be created during integration
   test implementation, not as part of this documentation plan.

5. **Added Section 30**: Explicit list of files NOT requiring updates with justifications:
   - `docker/README.md` (docker task features, not compose/test)
   - Individual example READMEs (covered by examples/README.md decision guide)
   - `basic/README.md`, `lifecycle-class/README.md`, `lifecycle-method/README.md` (different concerns)
   - `existing-images/README.md`, `logs-capture/README.md` (unrelated to wait mechanisms)

6. **Updated checklist**: Added checklist items for Section 28 (comparison table updates)

## Review Notes (2026-01-13 - Fifth Review)

This documentation plan was reviewed a fifth time to verify coverage of port de-confliction tables, feature matrices,
and decision guide tables in integration test READMEs.

1. **Port De-confliction Tables Gap (DOC-19)**: The main `plugin-integration-test/README.md` contains detailed port
   allocation tables (lines 210-239) for Server/Service Ports. New `waitForLog` integration test scenarios will need
   port allocations added to these tables.

2. **dockerProject Feature Matrix Gap (DOC-20)**: Section 25 mentions adding `waitForLog` properties but the Test
   Configuration feature matrix table (lines 446-456) needs a specific `waitForLog` row with checkmarks indicating
   which scenarios use it.

3. **dockerTest README Decision Guide Table (DOC-21)**: Section 21 mentions adding to "decision guide table" but the
   exact table format (lines 199-205) needs explicit specification showing the third `waitForLog` column.

4. **examples/README.md Decision Guide Format (DOC-22)**: Section 24 references the decision guide table but doesn't
   specify the exact format for adding the `waitForLog` column to the table at lines 174-181.

5. **Added to Section 30 (files NOT requiring updates)**:
   - `multi-service/README.md` - Documents complex orchestration patterns, not wait mechanism comparison

6. **Updated Sections**:
   - Section 20: Added explicit port allocation table update requirement
   - Section 21: Added explicit decision guide table format with `waitForLog` column
   - Section 24: Added explicit "Choosing CLASS vs METHOD Lifecycle" table update
   - Section 25: Added explicit Test Configuration feature matrix table update

**Updated Plan Status:**
- **Plan Status: In progress - Sixth review added additional docs files**

## Review Notes (2026-01-13 - Sixth Review)

A sixth review was conducted to scan the entire codebase for additional README.md files and documentation that needs
updates for the `waitForLog` feature.

1. **`docs/usage/README.md` Gap (DOC-23)**: The usage documentation index file references the Docker Orchestration
   Guide but only mentions "Health checks and readiness waiting" - should include log pattern waiting. The quick
   example only shows `waitForHealthy`.

2. **`docs/usage/spock-junit-test-extensions.md` Gap (DOC-24)**: This file documents the test framework extensions:
   - "Key Features" section (line ~37) states "Health/Readiness Waiting: Waits for containers to reach RUNNING or
     HEALTHY status" - needs to include log pattern matching
   - System Properties table (line ~108) only lists `waitForHealthy` properties - needs `waitForLog` properties added

3. **IDE Templates Gap (DOC-25)**: The `docs/ide-templates/README.md` and `gradle-docker-templates.xml` files contain
   `dockerorch` template that only shows `waitForHealthy` - optionally could include `waitForLog` example.

4. **Section 30 Correction**: `multi-service/README.md` was listed as excluded but this file does not exist. The
   `plugin-integration-test/dockerTest/verification/multi-service/` directory exists but has no README.md file.

**New Sections Added:**
- Section 31: `docs/usage/README.md` - Usage documentation index updates
- Section 32: `docs/usage/spock-junit-test-extensions.md` - Test extensions guide updates
- Section 33: IDE Templates (optional, low priority)

**Updated Plan Status:**
- **Plan Status: In progress - Seventh review adds broken link fixes and CLAUDE.md update**

## Review Notes (2026-01-13 - Seventh Review)

A seventh review was conducted to identify broken links and additional documentation files needing updates.

1. **Pre-existing Broken Links (DOC-26)**: 39 files reference `usage-docker-orch.md` which does not exist - the correct
   file is `usage-docker-test.md`. While this is a pre-existing issue (not caused by `waitForLog`), these broken links
   should be fixed as part of this documentation update effort since users navigating to learn about wait mechanisms
   will encounter broken links.

   **Affected files in plugin-integration-test/ that should be fixed:**
   - `dockerTest/README.md` (line ~15)
   - `dockerTest/examples/README.md` (links in "Documentation" section)
   - `dockerTest/examples/database-app/README.md` (line ~198)
   - `dockerTest/examples/web-app/README.md` (line ~103)
   - `dockerTest/examples/web-app-junit/README.md` (line ~106)
   - `dockerTest/examples/isolated-tests/README.md` (line ~110)
   - `dockerTest/examples/isolated-tests-junit/README.md` (line ~116)
   - `dockerTest/examples/stateful-web-app/README.md` (line ~136)
   - `dockerWorkflows/README.md`

2. **CLAUDE.md Update (DOC-27)**: The `CLAUDE.md` file in the project root references `usage-docker-orch.md` in the
   "Adhere to Plugin Usage" section (line ~89). This should be updated to reference `usage-docker-test.md`.

3. **Verification of Section 30 Exclusions**: Re-verified that files in Section 30 are correctly excluded:
   - Individual example READMEs: Confirmed these only show specific configurations; the decision guide in
     `examples/README.md` (Section 24) is the appropriate place for `waitForLog` comparison
   - These files DO need the broken link fix (from `usage-docker-orch.md` to `usage-docker-test.md`)

**New Sections Added:**
- Section 34: Broken link fixes for integration test READMEs
- Section 35: CLAUDE.md update for correct documentation reference

**Updated Plan Status:**
- **Plan Status: In progress - Eighth review adds additional gaps**

## Review Notes (2026-01-13 - Eighth Review)

An eighth review was conducted to verify complete coverage of comparison tables, DSL examples, and
cross-references in existing documentation.

1. **Section 23 Enhancement (DOC-28)**: The `mixed-wait/README.md` file contains detailed comparison tables at
   lines 289-324 comparing "vs. Wait-Healthy Test", "vs. Wait-Running Test", and "vs. Basic Test". These tables
   should include `waitForLog` comparisons when the new wait-log scenarios are implemented.

2. **Section 26 Enhancement (DOC-29)**: The `dockerWorkflows/README.md` file contains DSL examples (line ~93-100)
   showing `test { stack = ...}` configuration. The plan should specify adding `waitForLog` as an option in these
   examples.

3. **Section 34 Expansion (DOC-30)**: The broken link fix should also cover files in `docs/` directory that reference
   `usage-docker-orch.md`, not just `plugin-integration-test/` files. Files to check in `docs/`:
   - Any README or markdown files that cross-reference `usage-docker-orch.md`

**Updates Made:**
- Enhanced Section 23 to specify comparison table updates
- Enhanced Section 26 to specify DSL example updates
- Added Section 36 for docs/ directory broken link fixes
- Updated checklist with new sub-tasks

**Updated Plan Status:**
- **Plan Status: In progress - Ninth review adds existing-images README**

## Review Notes (2026-01-13 - Ninth Review)

A ninth review was conducted to scan the `plugin-integration-test/` directory for any README files that were
incorrectly excluded from the documentation plan.

1. **existing-images/README.md Gap (DOC-31)**: The `existing-images/README.md` file was incorrectly listed in
   Section 30 as "Unrelated to wait mechanisms". However, this README contains:
   - A "Mixed Wait Strategy" section (lines 93-127) that explicitly discusses combining `waitForHealthy` and
     `waitForRunning`
   - A "Wait Configuration" section (lines 124-127) comparing the two wait strategies
   - A "Use Case" section (lines 337-360) discussing when to use each wait strategy

   This file SHOULD be updated to include `waitForLog` as a third wait strategy option.

**Updates Made:**
- Removed `existing-images/README.md` from Section 30 (files not requiring updates)
- Added Section 37 with detailed update instructions for `existing-images/README.md`
- Updated checklist with Section 37 items

**Updated Plan Status:**
- **Plan Status: In progress - Tenth review adds requirements doc and source code comments**

## Review Notes (2026-01-13 - Tenth Review)

A tenth review was conducted to scan the entire codebase for any additional documentation files needing updates,
including files outside the `plugin-integration-test/` and `docs/usage/` directories.

1. **Requirements/Use Case Document Gap (DOC-32)**: The file `docs/design-docs/requirements/use-cases/uc-7-proj-dev-compose-orchestration.md` is an active requirements document (Status: Implemented) that:
   - Contains a broken link to `usage-docker-orch.md` (line 31)
   - Mentions "Complex waiting strategies (log pattern matching)" in Phase 3 but with placeholder language
   - Shows outdated DSL example `successWhenLogsMatch` which doesn't match actual `waitForLog` DSL syntax

2. **Source Code Comment Broken Links (DOC-33)**: Four source code files contain comments referencing the old
   `usage-docker-orch.md` filename. While low priority, these should be fixed for consistency.

3. **Additional Design Docs (DOC-34)**: Two files in `docs/design-docs/` outside the `done/` directory contain
   broken links that should be fixed for completeness.

**Updates Made:**
- Added Section 38: Requirements/Use Case document update
- Added Section 39: Source code comment broken links (optional)
- Added Section 40: Additional design-docs broken links (low priority)
- Updated checklist with new sections

**Updated Plan Status:**
- **Plan Status: In progress - Eleventh review adds specification documents**

## Review Notes (2026-01-13 - Eleventh Review)

An eleventh review was conducted to scan the entire codebase for specification documents and architectural documentation
that may need updates for the `waitForLog` feature.

1. **Functional Specifications Gap (DOC-35)**: The file `docs/design-docs/specifications/functional-specifications/functional-specifications.md`
   contains functional requirements fr-26 (waitForRunning) and fr-27 (waitForHealthy) but no requirement for waitForLog.
   A new functional requirement should be added to formally document the waitForLog capability.

2. **Technical Specifications Gap (DOC-36)**: The file `docs/design-docs/specifications/technical-specifications/technical-specifications.md`
   contains extensive technical implementation documentation including:
   - WaitConfig class definition (lines 582-597)
   - waitForServices method specification (lines 437, 692, 1868)
   - DSL examples showing waitForRunning/waitForHealthy (lines 270-291)
   - Convention defaults for poll and timeout (lines 2214-2219)
   This document needs updates to document the WaitForLogConfig class, waitForLogPatterns method, and related
   implementation details.

3. **DSL Architecture Rationale Gap (DOC-37)**: The file `docs/design-docs/dsl-architecture-rationale.md` explains the
   four-DSL architecture with examples, but current examples only show `waitForHealthy` (lines 159, 264). These examples
   should be updated to show `waitForLog` as an available wait mechanism option.

**Updates Made:**
- Added Section 41: Functional Specifications document update
- Added Section 42: Technical Specifications document update
- Added Section 43: DSL Architecture Rationale document update
- Updated checklist with "Specification Documents (Sections 41-43)" section

**Updated Plan Status:**
- **Plan Status: COMPLETE - Ready for implementation**

The documentation plan (0600) now contains 43 sections covering all required updates, including:
- 6 usage documentation files (added README.md and spock-junit-test-extensions.md)
- 11 integration test README files (9 updates + clarification for new files + exclusion list)
- 1 requirements/use case document update
- 3 specification/architecture documents (functional specs, technical specs, DSL architecture rationale)
- Enhanced comparison table coverage for mixed-wait and individual verification scenarios
- DSL example updates for dockerWorkflows
- 2 project-level files (CHANGELOG, README)
- 1 IDE templates section (optional)
- 1 broken link fix section (affecting 9+ files in plugin-integration-test/)
- 1 CLAUDE.md update
- 1 docs/ broken link fix section
- 1 source code comment broken link section (optional)
- 1 additional design-docs broken link section (low priority)
- Explicit documentation of files NOT requiring updates
- Explicit port allocation and feature matrix table updates

## Checklist

### Documentation File Updates

- [ ] Update `docs/usage/usage-docker-test.md`
  - [ ] Verify `waitForLog` section completeness (Section 1)
    - [ ] Wait Options Overview table includes `waitForLog` entry
    - [ ] Execution order is: `waitForRunning` → `waitForHealthy` → `waitForLog`
    - [ ] Regex escaping guide table present and accurate
  - [ ] Add execution order diagram (Section 2)
  - [ ] Add Docker Compose v2+ requirement note (Section 3)
  - [ ] Add Lifecycle support clarification (Section 4)
  - [ ] Add `progressIntervalSeconds` to example (Section 5)
  - [ ] Add reject pattern error output format (Section 6)
  - [ ] Verify verbose output format matches DSL description (Section 6a)
  - [ ] Verify periodic progress logging output format (Section 6b)
  - [ ] Add configuration warnings documentation (Section 6c)
  - [ ] Add total timeout calculation documentation (Section 6d)
  - [ ] Verify error message examples match implementation (Section 7)
  - [ ] Verify Test Framework Extensions integration section (Section 16)
  - [ ] Add troubleshooting entries for waitForLog issues (Section 19)

- [ ] Update `docs/usage/usage-docker-workflows.md`
  - [ ] Verify Quick Start example with waitForLog option (Section 8)
  - [ ] Add cross-reference to usage-docker-test.md for wait block details (Section 9)

- [ ] Update `docs/usage/usage-docker-project.md`
  - [ ] Expand `waitForLog` property documentation in test block (Section 10)
  - [ ] Add `rejectPatterns` property (Section 11)
  - [ ] Add `caseInsensitive`, `verbose`, `progressIntervalSeconds` properties (Section 12)
  - [ ] Add waitForLog example (Section 13)

- [ ] Update `docs/usage/README.md` (Section 31)
  - [ ] Update Docker Orchestration Guide "Key topics" to include log pattern waiting
  - [ ] Add `waitForLog` to quick example comment
  - [ ] Verify Documentation Map references are accurate

- [ ] Update `docs/usage/spock-junit-test-extensions.md` (Section 32)
  - [ ] Update "Key Features" to include log pattern waiting
  - [ ] Add `waitForLog` system properties to table
  - [ ] Update any examples showing wait configuration

**Note**: `docs/usage/usage-docker.md` does NOT require updates. That file documents Docker image operations
(build, tag, save, publish). The `waitForLog` feature is a compose/testing concern documented in
`usage-docker-test.md`.

### Additional Documentation Items

- [ ] Add Performance Considerations to `docs/usage/usage-docker-test.md` (Section 14)
- [ ] Add Known Limitations (Container Restart During Wait) to `docs/usage/usage-docker-test.md` (Section 15)

### IDE Templates (Optional - Low Priority)

- [ ] Update `docs/ide-templates/README.md` (Section 33 - Optional)
  - [ ] Add `waitForLog` to `dockerorch` template example
  - [ ] Consider adding `waitForLog`-specific template
- [ ] Update `docs/ide-templates/gradle-docker-templates.xml` if template changes made

### Integration Test README Updates

- [ ] Update `plugin-integration-test/README.md` (Section 20)
  - [ ] Add `waitForLog` to "What Gets Tested" section
  - [ ] Update integration test structure if needed
  - [ ] Add port allocations for new wait-log scenarios to Server/Service Ports table (DOC-19)

- [ ] Update `plugin-integration-test/dockerTest/README.md` (Section 21)
  - [ ] Add `waitForLog` to purpose section (point 4)
  - [ ] Add `waitForLog` verification scenarios to table
  - [ ] Add `waitForLog` to Wait Mechanisms section with description
  - [ ] Add `waitForLog` column to decision guide table (DOC-21)

- [ ] Update `plugin-integration-test/dockerTest/verification/README.md` (Section 22)
  - [ ] Add log pattern matching to purpose list
  - [ ] Add `waitForLog` scenarios to verification table

- [ ] Update `plugin-integration-test/dockerTest/verification/mixed-wait/README.md` (Section 23)
  - [ ] Add reference to `waitForLog` as third combinable option
  - [ ] Add "vs. Wait-Log Tests" comparison table to "Differences from Other Verification Tests" section
  - [ ] Add cross-reference to `wait-log-mixed/` scenario at end of README

- [ ] Update `plugin-integration-test/dockerTest/examples/README.md` (Section 24)
  - [ ] Add `waitForLog` to Wait Mechanisms section with description
  - [ ] Add `waitForLog` column to "Choosing CLASS vs METHOD Lifecycle" table (DOC-22)
  - [ ] Add `waitForLog` to "When to Use Test Framework Extensions" decision guide

- [ ] Update `plugin-integration-test/dockerProject/README.md` (Section 25)
  - [ ] Add `waitForLog` row to Test Configuration feature matrix (DOC-20)
  - [ ] Add `rejectPatterns`, `caseInsensitive`, `verbose`, `progressIntervalSeconds` rows
  - [ ] Add `waitForLog` DSL example to at least one scenario description

- [ ] Update `plugin-integration-test/dockerWorkflows/README.md` (Section 26)
  - [ ] Add note about `waitForLog` support in workflow test steps
  - [ ] Update DSL examples to show `waitForLog` as an option in dockerTest stack configuration
  - [ ] Add port allocation for wait-log workflow scenarios if new scenarios added

- [ ] Update `plugin-integration-test/buildSrc/README.md` (Section 27 - Conditional)
  - [ ] Add new validation utilities for `waitForLog` if created

- [ ] Update individual verification scenario comparison tables (Section 28)
  - [ ] Update `wait-healthy/README.md` comparison table to include `waitForLog`
  - [ ] Update `wait-running/README.md` comparison table to include `waitForLog`
  - [ ] Add cross-references to new wait-log scenarios

- [ ] Update `plugin-integration-test/dockerTest/verification/existing-images/README.md` (Section 37)
  - [ ] Update "Mixed Wait Strategy" section to mention `waitForLog` as third option
  - [ ] Update "Wait Configuration" section with note about `waitForLog`
  - [ ] Update "Use Case" section bullet 3 to include `waitForLog`
  - [ ] Add cross-reference to wait-log scenarios at end of README

### Project-Level Documentation

- [ ] Update `CHANGELOG.md` (Section 17)
  - [ ] Document new `waitForLog` feature with full lifecycle support (CLASS and METHOD)
  - [ ] Document execution order: `waitForRunning` → `waitForHealthy` → `waitForLog`

- [ ] Update `README.md` (Section 18)
  - [ ] Add `waitForLog` to feature list

### Broken Link Fixes (DOC-26)

- [ ] Fix broken links from `usage-docker-orch.md` to `usage-docker-test.md` (Section 34)
  - [ ] Fix `plugin-integration-test/dockerTest/README.md`
  - [ ] Fix `plugin-integration-test/dockerTest/examples/README.md`
  - [ ] Fix `plugin-integration-test/dockerTest/examples/database-app/README.md`
  - [ ] Fix `plugin-integration-test/dockerTest/examples/web-app/README.md`
  - [ ] Fix `plugin-integration-test/dockerTest/examples/web-app-junit/README.md`
  - [ ] Fix `plugin-integration-test/dockerTest/examples/isolated-tests/README.md`
  - [ ] Fix `plugin-integration-test/dockerTest/examples/isolated-tests-junit/README.md`
  - [ ] Fix `plugin-integration-test/dockerTest/examples/stateful-web-app/README.md`
  - [ ] Fix `plugin-integration-test/dockerWorkflows/README.md`

### CLAUDE.md Update (DOC-27)

- [ ] Update `CLAUDE.md` (Section 35)
  - [ ] Fix reference from `usage-docker-orch.md` to `usage-docker-test.md`

### Additional Broken Link Fixes in docs/ (DOC-30)

- [ ] Fix broken links in `docs/` directory (Section 36)
  - [ ] Run `rg "usage-docker-orch" docs/` to find affected files
  - [ ] Replace `usage-docker-orch.md` → `usage-docker-test.md` in all affected files

### Requirements/Use Case Document Update (Section 38)

- [ ] Update `docs/design-docs/requirements/use-cases/uc-7-proj-dev-compose-orchestration.md`
  - [ ] Fix broken link to `usage-docker-orch.md` (line 31)
  - [ ] Update Phase 3 description to mention `waitForLog` (lines 113-117)
  - [ ] Update DSL example to use actual `waitForLog` syntax (lines 218-221)

### Source Code Comment Broken Links (Section 39 - Optional)

- [ ] Fix source code comments referencing `usage-docker-orch.md`
  - [ ] `plugin/src/main/groovy/.../DockerComposeClassExtension.groovy`
  - [ ] `plugin/src/main/groovy/.../DockerComposeMethodExtension.groovy`
  - [ ] `plugin/src/main/groovy/.../ComposeAnnotationHintListener.groovy`
  - [ ] `plugin/src/test/groovy/.../ComposeAnnotationHintListenerTest.groovy`

### Additional Design Docs Broken Links (Section 40 - Low Priority)

- [ ] Fix broken links in design-docs outside of `done/` directory
  - [ ] `docs/design-docs/project-reviews/2025-12-06-project-review.md`
  - [ ] `docs/design-docs/design-decisions/requires-image-defer.md`

### Specification Documents (Sections 41-43)

- [ ] Update `docs/design-docs/specifications/functional-specifications/functional-specifications.md` (Section 41)
  - [ ] Add functional requirement fr-33/fs-33 for waitForLog feature (log pattern waiting with timeout)

- [ ] Update `docs/design-docs/specifications/technical-specifications/technical-specifications.md` (Section 42)
  - [ ] Add WaitForLogConfig class documentation
  - [ ] Add waitForLogPatterns method specification
  - [ ] Update DSL examples to include waitForLog block
  - [ ] Add convention defaults for waitForLog properties
  - [ ] Update JSON state file documentation with waitForLog metadata

- [ ] Update `docs/design-docs/dsl-architecture-rationale.md` (Section 43)
  - [ ] Update Scenario 2 example (line ~159) to show waitForLog option
  - [ ] Update dockerProject example (line ~264) to mention waitForLog as alternative

### Final Verification
- [ ] All documentation is complete and accurate
- [ ] Examples are tested and working
- [ ] No broken links
- [ ] Line length ≤ 120 characters
- [ ] Consistent terminology across all files

---

## Section 1: Verify `waitForLog` Section in usage-docker-test.md

**Location**: `docs/usage/usage-docker-test.md` lines ~511-627

**Current state**: The `waitForLog` section already exists and covers most requirements.

**Verification checklist**:
- [x] Properties table with all 7 properties (`waitForServices`, `rejectPatterns`, `timeoutSeconds`, `pollSeconds`,
      `caseInsensitive`, `verbose`, `progressIntervalSeconds`)
- [x] Pattern Matching semantics
- [x] Common Patterns for Popular Services
- [x] Performance Note
- [x] Decision Guide table
- [x] Combined Usage Example
- [x] Required Properties section with error messages
- [ ] Wait Options Overview table (~line 448) includes `waitForLog` row
- [ ] Execution order statement (~line 453-454) reflects NEW order: `waitForRunning` → `waitForHealthy` → `waitForLog`
- [ ] Regex escaping guide table present (matching DSL 0100, lines 341-350)

**Action**: Verify existing content matches implementation. Update execution order if it still shows old order.

---

## Section 2: Add Execution Order Diagram

**Location**: `docs/usage/usage-docker-test.md` - "Wait Options Overview" section (~lines 443-458)

**Current content** (lines 453-454):

    **Execution Order**: When multiple wait blocks are configured, they execute in order:
    `waitForRunning` → `waitForHealthy` → `waitForLog`

**Action**: Replace with visual diagram. Copy the following markdown exactly (the diagram uses 4-space indentation
to render as a code block):

    **Execution Order**: When multiple wait blocks are configured, they execute in sequence:

        ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
        │  waitForRunning │ --> │  waitForHealthy │ --> │   waitForLog    │
        │   (optional)    │     │   (optional)    │     │   (optional)    │
        └─────────────────┘     └─────────────────┘     └─────────────────┘
                 │                      │                      │
                 ▼                      ▼                      ▼
           Container process      Docker HEALTHCHECK      Log patterns
           has started            returns healthy         found in output

    If any wait block fails, subsequent blocks are skipped and the failure is reported immediately.

---

## Section 3: Add Docker Compose v2+ Requirement Note

**Location**: `docs/usage/usage-docker-test.md` - After the `waitForLog` section (~line 627)

**Action**: Add the following content:

    #### Docker Compose v2+ Requirement

    The `waitForLog` feature requires Docker Compose v2.x or later. Docker Compose v1 (Python-based
    `docker-compose` command) uses a different JSON format for `docker compose ps` output and is not compatible.

    | Docker Compose Version | Command | Support |
    |------------------------|---------|---------|
    | v2.x+ | `docker compose` | ✅ Fully supported |
    | v1.x | `docker-compose` | ❌ Not supported |

    **Check your version**:

        docker compose version  # Should show v2.x or higher

    If using Docker Compose v1, either upgrade (Docker Desktop 3.4+ includes v2) or use
    `waitForHealthy`/`waitForRunning`.

---

## Section 4: Add Lifecycle Support Clarification

**Location**: `docs/usage/usage-docker-test.md` - After the `waitForLog` section

**Action**: Add clarification:

    #### Lifecycle Support

    The `waitForLog` block supports both CLASS and METHOD lifecycles:

    | Lifecycle | Behavior | Use Case |
    |-----------|----------|----------|
    | CLASS | Containers start once, all tests share same containers | Faster, tests share state |
    | METHOD | Fresh containers for each test method | Full isolation between tests |

    **Example:**

        tasks.named('integrationTest') {
            usesCompose(stack: "myTest", lifecycle: "class")  // or "method"
        }

---

## Section 5: Add `progressIntervalSeconds` to Example

**Location**: `docs/usage/usage-docker-test.md` - Main `waitForLog` DSL example (~lines 517-545)

**Current example** shows `progressIntervalSeconds.set(30)` - verify it's present with explanation.

**Action**: Ensure the example includes:

```groovy
waitForLog {
    waitForServices.set([...])
    progressIntervalSeconds.set(30)  // Log summary every 30s (0 = disabled)
}
```

---

## Section 6: Add Reject Pattern Error Output Format

**Location**: `docs/usage/usage-docker-test.md` - After "Required Properties" section (~line 688)

**Action**: Add error output example:

```markdown
For reject pattern match:

    Reject pattern matched in service 'app' - failing immediately.
      Matched reject pattern: 'Exception'
      Log line: "[2025-01-03 10:15:35] ERROR Exception during startup: NullPointerException"

      Service patterns status:
        [FOUND]     'Database connection established' - matched at 12s
        [NOT FOUND] 'Started Application'

    Hint: A reject pattern indicates the service encountered an error during startup.
          Check the full container logs for details.
```

---

## Section 6a: Verify Verbose Output Format

**Location**: `docs/usage/usage-docker-test.md` - After waitForLog properties table or in a "Progress Logging" section

**Required content** (from DSL description 0100, lines 430-455): Verify documentation includes verbose output format:

```
[waitForLog] Polling for log patterns (attempt 1/30, elapsed: 0s)...
[waitForLog] Service 'db': 0/2 patterns matched
[waitForLog] Service 'app': 0/2 patterns matched

[waitForLog] Polling for log patterns (attempt 5/30, elapsed: 8s)...
[waitForLog] Service 'db': 1/2 patterns matched
  [FOUND] 'PostgreSQL init process complete' - matched at 4s
[waitForLog] Service 'app': 0/2 patterns matched

[waitForLog] Polling for log patterns (attempt 10/30, elapsed: 18s)...
[waitForLog] Service 'db': 2/2 patterns matched - READY
[waitForLog] Service 'app': 1/2 patterns matched
  [FOUND] 'Database connection established' - matched at 15s

[waitForLog] Polling for log patterns (attempt 12/30, elapsed: 22s)...
[waitForLog] Service 'app': 2/2 patterns matched - READY
[waitForLog] All services ready after 22 seconds
```

**Action**: If not present, add a "Progress Logging" subsection with this output format example.

---

## Section 6b: Verify Periodic Progress Logging Output Format

**Location**: `docs/usage/usage-docker-test.md` - In "Progress Logging" section or near `progressIntervalSeconds`

**Required content** (from DSL description 0100, lines 475-483): Verify documentation includes periodic progress
output format:

```
[waitForLog] Waiting for log patterns in 1 service (timeout: 300s)...
[waitForLog] Progress at 30s: app 1/3 patterns matched
[waitForLog] Progress at 60s: app 2/3 patterns matched
[waitForLog] Progress at 90s: app 2/3 patterns matched
[waitForLog] All services ready after 95 seconds
```

**Action**: If not present, add example showing periodic progress logging behavior when `progressIntervalSeconds > 0`.

---

## Section 6c: Add Configuration Warnings Documentation

**Location**: `docs/usage/usage-docker-test.md` - After the waitForLog properties table or in a "Validation" section

**Required content** (from DSL description 0100, lines 257-261 and 534-541):

1. **pollSeconds > timeoutSeconds warning**:

       **⚠️ Configuration Warning**: If `pollSeconds` is greater than `timeoutSeconds`, a warning is logged because
       only one poll attempt will occur before timeout. Example: with `timeoutSeconds=5` and `pollSeconds=10`, the
       wait will timeout after the first (and only) poll attempt at t=0 if patterns aren't already present.

2. **Orphaned rejectPatterns warning**:

       If `rejectPatterns` contains services that are not in `waitForServices`, a warning is logged:

           [waitForLog] WARNING: rejectPatterns contains services not in waitForServices: [unknown-service].
           These reject patterns will never be checked.

       This is a warning (not an error) because the configuration is technically valid, but likely indicates a
       misconfiguration.

**Action**: Verify these warnings are documented. If not present, add them after the properties table.

---

## Section 6d: Add Total Timeout Calculation Documentation

**Location**: `docs/usage/usage-docker-test.md` - After combined usage example or in a "Timeout" section

**Required content** (from DSL description 0100, lines 622-636):

    ### Total Timeout Calculation

    When combining multiple wait blocks, each block has its own independent timeout. The total maximum wait time
    is the sum of all configured timeouts:

        waitForRunning {
            waitForServices.set(['nginx'])
            timeoutSeconds.set(30)    // Max 30 seconds for running check
        }
        waitForHealthy {
            waitForServices.set(['app', 'db'])
            timeoutSeconds.set(60)    // Max 60 seconds for health checks
        }
        waitForLog {
            waitForServices.set(['app': ['Ready to serve']])
            timeoutSeconds.set(120)   // Max 120 seconds for log patterns
        }
        // Total maximum wait time: 30 + 60 + 120 = 210 seconds

    Plan CI pipeline timeouts accordingly to account for the cumulative wait time.

**Action**: Verify this content exists. If not present, add it near the combined usage example.

---

## Section 7: Verify Error Message Examples

**Location**: `docs/usage/usage-docker-test.md` - "Required Properties" section (~lines 628-715)

**Action**: Verify documented error messages are accurate. The documentation should include examples of:

1. **Empty `waitForServices` error**
2. **Empty pattern list error**
3. **Invalid regex pattern error**
4. **Unknown service name error**
5. **Timeout error** (with pattern match status and recent log lines)
6. **Service crash error**

Ensure error message examples help users understand what went wrong and how to fix it.

---

## Section 8: Verify Quick Start in usage-docker-workflows.md

**Location**: `docs/usage/usage-docker-workflows.md` - Quick Start section (~lines 73-131)

**Current state**: Shows `waitForHealthy` in Quick Start example.

**Action**: Add comment showing `waitForLog` as an option:

```groovy
dockerTest {
    composeStacks {
        appTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            projectName = 'my-app-test'

            // Wait options: waitForHealthy, waitForRunning, waitForLog
            // See usage-docker-test.md for full details on all wait options
            waitForHealthy {
                waitForServices.set(['app'])
                timeoutSeconds.set(60)
            }
        }
    }
}
```

This is already present - verify accuracy.

---

## Section 9: Add Cross-Reference in usage-docker-workflows.md

**Location**: `docs/usage/usage-docker-workflows.md` - Near end of "test Step" section (~line 232)

**Action**: Add cross-reference:

```markdown
See [Container Readiness: Waiting for Services](usage-docker-test.md#container-readiness-waiting-for-services) for
complete documentation on `waitForHealthy`, `waitForRunning`, and `waitForLog` configuration options.
```

---

## Section 10: Expand waitForLog in usage-docker-project.md test block

**Location**: `docs/usage/usage-docker-project.md` - test block properties table (~lines 147-158)

**Context**: The `dockerProject` DSL provides a simplified, single-block configuration. Its `test { }` block exposes
flat properties that configure the underlying compose stack's wait behavior. These are NOT the same as the nested
`waitForLog { }` block in `dockerTest` DSL - they are simplified property accessors.

**Current state**: Shows `waitForLog` with `Map<String, List<String>>` type and brief description.

**Action**: Verify the property description is accurate:

    | `waitForLog` | `Map<String, List<String>>` | `[:]` | Services with log patterns to wait for |

---

## Section 11: Add rejectPatterns to usage-docker-project.md

**Location**: `docs/usage/usage-docker-project.md` - test block properties table

**Action**: Add `rejectPatterns` property:

```markdown
| `rejectPatterns` | `Map<String, List<String>>` | `[:]` | Patterns that trigger immediate failure |
```

---

## Section 12: Add Additional waitForLog Properties to usage-docker-project.md

**Location**: `docs/usage/usage-docker-project.md` - test block properties table

**Action**: Add missing properties:

```markdown
| `caseInsensitive` | `Boolean` | `false` | Case-insensitive pattern matching |
| `verbose` | `Boolean` | `false` | Detailed progress logging |
| `progressIntervalSeconds` | `Integer` | `0` | Log progress summary at this interval (0 = disabled) |
```

---

## Section 13: Add waitForLog Example to usage-docker-project.md

**Location**: `docs/usage/usage-docker-project.md` - After the existing waitForLog example (~line 173)

**Current state**: Has a simple example.

**Action**: Verify the example is complete:

```groovy
test {
    compose.set('src/integrationTest/resources/compose/app.yml')
    waitForLog.set([
        'app': ['Started Application in .* seconds'],
        'db': ['ready to accept connections']
    ])
    rejectPatterns.set([
        'app': ['FATAL', 'Exception']
    ])
    timeoutSeconds.set(120)
    caseInsensitive.set(false)
    verbose.set(true)
}
```

---

## Section 14: Add Performance Considerations

**Location**: `docs/usage/usage-docker-test.md` - After the main `waitForLog` section, or as a subsection

**Current state**: Check if a "Performance Note" already exists (~line 580-583). If present, verify completeness.

**Required content** (from DSL description 0100, lines 388-411):

```markdown
### Performance Considerations

**Important**: The `waitForLog` feature fetches **all container logs** on each poll iteration to ensure patterns
that appeared early in startup are not missed. For containers with high log volume, this can cause performance
degradation.

**Recommendations for High-Volume Logging Services:**

1. **Use longer poll intervals**: Set `pollSeconds.set(5)` or higher to reduce log fetch frequency
2. **Choose early patterns**: Select patterns that appear early in startup to minimize wait time
3. **Consider health checks**: For services with high log volume, prefer `waitForHealthy` when possible
4. **Limit monitored services**: Only include services that truly need log-based readiness detection

**Example for high-volume services:**

    waitForLog {
        waitForServices.set([
            'high-volume-app': ['Application started']  // Single, early pattern
        ])
        pollSeconds.set(5)      // Less frequent polling
        timeoutSeconds.set(120) // Allow more time between polls
    }
```

**Action**: Verify this content exists. If not present or incomplete, add it.

---

## Section 15: Add Known Limitations (Container Restart During Wait)

**Location**: `docs/usage/usage-docker-test.md` - After Performance Considerations, or in a "Limitations" section

**Action**: Add user-facing limitation note:

    ### Known Limitations

    #### Container Restart During Wait

    If a container crashes and restarts during the wait period (e.g., due to `restart: always` in
    docker-compose.yml), previously matched patterns remain "matched" even after restart.

    **Workarounds**:
    - Use `waitForHealthy` for services that need stability verification
    - Remove `restart: always` from compose files used for testing

---

## Section 16: Verify Test Framework Extensions Integration

**Location**: `docs/usage/usage-docker-test.md` - In the Test Framework Extensions section

**Action**: Verify documentation shows how to use `waitForLog` with test framework extensions:

    // build.gradle
    dockerTest {
        composeStacks {
            myTest {
                files.from('compose.yml')
                waitForLog {
                    waitForServices.set(['app': ['Ready to serve']])
                    timeoutSeconds.set(60)
                }
            }
        }
    }

    tasks.named('integrationTest') {
        usesCompose(stack: "myTest", lifecycle: "class")
    }

    // Test class
    @ComposeUp
    class MyAppIT extends Specification {
        // Tests run after waitForLog completes
    }

---

## Section 17: CHANGELOG Entry

**Location**: `CHANGELOG.md`

**Action**: Add entry under appropriate version:

    ### Added
    - **`waitForLog` block in `dockerTest` DSL**: Wait for specific log patterns to appear in container output
      before considering services ready. Supports:
      - Per-service pattern configuration with AND logic (all patterns must match)
      - Reject patterns for immediate failure on error conditions
      - Case-insensitive matching option
      - Verbose and periodic progress logging
      - Full lifecycle support (CLASS and METHOD)

---

## Section 18: README Entry

**Location**: `README.md` - Features section

**Action**: Add to feature list:

```markdown
- **Container readiness checks**: Wait for containers to be running, healthy, or emit specific log patterns
  (`waitForRunning`, `waitForHealthy`, `waitForLog`)
```

---

## Section 19: Add Troubleshooting Entries for waitForLog

**Location**: `docs/usage/usage-docker-test.md` - "Troubleshooting Guide" section

**Action**: Add concise troubleshooting entries:

    #### 9. waitForLog Timeout With Correct Pattern

    **Symptom:** Timeout even though the log message appears in container logs.

    **Common causes:**
    - **Case sensitivity**: Use `caseInsensitive.set(true)` if needed
    - **Unescaped regex characters**: Use `\\[` to match literal `[`
    - **Anchors (`^`, `$`)**: Patterns match substrings, not full lines

    **Debug:** Set `verbose.set(true)` to see match progress.

    #### 10. Unknown Service Name Error

    **Symptom:** "Service(s) not found in compose project"

    **Solution:** Check spelling of service names in `waitForServices` against your compose file.

---

## Section 20: Update plugin-integration-test/README.md

**Location**: `plugin-integration-test/README.md`

**Current state**: Documents integration test structure but only mentions `waitForHealthy` and `waitForRunning`.

**Action**: Update the following sections:

1. **"What Gets Tested" section** (~line 20): Add `waitForLog` to the list of validated wait mechanisms.

2. **Integration Test Structure tree**: If listing verification scenarios, add `wait-log/` entry.

3. **Server/Service Ports - De-conflict table** (~lines 231-239): Add port allocations for new `waitForLog` scenarios.
   Reserve ports following the convention `9ssp` where `ss` is the scenario number.

   Example additions to the table:
   ```
   | Scenario | Port | Description |
   |----------|------|-------------|
   | wait-log-basic | 90XX | waitForLog basic pattern matching |
   | wait-log-reject | 90YY | waitForLog reject pattern scenarios |
   | wait-log-mixed | 90ZZ | Combined wait mechanisms |
   ```
   (Note: Exact port numbers to be assigned during integration test implementation based on available range)

**Example addition to "What Gets Tested"**:

    - **Validating wait mechanisms** - `waitForHealthy`, `waitForRunning`, and `waitForLog` functionality

---

## Section 21: Update plugin-integration-test/dockerTest/README.md

**Location**: `plugin-integration-test/dockerTest/README.md`

**Current state**: Comprehensive documentation but `waitForLog` is not mentioned in several key sections.

**Actions**:

1. **"Purpose" section** (~line 10): Add `waitForLog` to point 4:

        4. **Validating wait mechanisms** - `waitForHealthy`, `waitForRunning`, and `waitForLog` functionality

2. **"Verification Tests" table** (~line 50): Add new `waitForLog` scenarios:

    | Wait Log Basic   | `verification/wait-log-basic/`   | ⏳ New      | CLASS     | waitForLog, log pattern matching, timeout handling          |
    | Wait Log Reject  | `verification/wait-log-reject/`  | ⏳ New      | CLASS     | rejectPatterns, immediate failure on error patterns         |
    | Wait Log Mixed   | `verification/wait-log-mixed/`   | ⏳ New      | CLASS     | Combined waitForLog + waitForHealthy + waitForRunning       |

3. **"Wait Mechanisms" section** (~line 183): Add `waitForLog` option:

        **waitForLog:**
        - Container logs contain specific patterns matching configured regex
        - Use for: services without health checks that emit startup messages
        - Most flexible but requires knowledge of expected log output

4. **"Decision guide" table** (~lines 199-205): Update table to add `waitForLog` column. Current table only has
   `waitForRunning` and `waitForHealthy` columns. New table format:

    | Factor | waitForRunning | waitForHealthy | waitForLog |
    |--------|----------------|----------------|------------|
    | **Service has health check** | Optional | ✅ Required | Optional |
    | **Service needs initialization** | ❌ Not reliable | ✅ Recommended | ✅ Recommended |
    | **Service has startup log message** | N/A | N/A | ✅ Required |
    | **Speed** | ⚡ Fastest | ⏱️ Waits for health | ⏱️ Waits for pattern |
    | **Test reliability** | ⚠️ May fail if not ready | ✅ Runs when ready | ✅ Runs when pattern found |
    | **Examples** | Static files, proxies | Databases, web apps, APIs | Legacy apps, custom services |

    **Note**: This is a NEW column in an existing table (not a new row). The current table at lines 199-205 only has
    two columns (`waitForRunning`, `waitForHealthy`) and needs a third `waitForLog` column added.

---

## Section 22: Update plugin-integration-test/dockerTest/verification/README.md

**Location**: `plugin-integration-test/dockerTest/verification/README.md`

**Current state**: Documents verification scenarios but does not include `waitForLog`.

**Actions**:

1. **"Purpose" section** (~line 10): Update to include log pattern matching:

        - ✅ Containers start and reach correct states (RUNNING, HEALTHY)
        - ✅ Container logs match expected patterns (waitForLog)

2. **"Verification Scenarios" table**: Add new `waitForLog` rows:

    | `wait-log-basic/` | Basic log pattern matching | CLASS | waitForLog, pattern matching, timeout |
    | `wait-log-reject/` | Reject pattern failure | CLASS | rejectPatterns, immediate failure |
    | `wait-log-options/` | waitForLog configuration options | CLASS | caseInsensitive, verbose, progressIntervalSeconds |
    | `wait-log-mixed/` | Combined wait mechanisms | CLASS | All three wait types together |

---

## Section 23: Update plugin-integration-test/dockerTest/verification/mixed-wait/README.md

**Location**: `plugin-integration-test/dockerTest/verification/mixed-wait/README.md`

**Current state**: Documents `waitForHealthy` + `waitForRunning` combinations. Contains comparison tables at lines
289-324 that compare "vs. Wait-Healthy Test", "vs. Wait-Running Test", and "vs. Basic Test".

**Actions**:

1. **"What This Test Validates" section**: Add note about `waitForLog`:

        > **Note**: This test demonstrates combining `waitForHealthy` and `waitForRunning`. For combining all three
        > wait mechanisms (`waitForRunning` + `waitForHealthy` + `waitForLog`), see `wait-log-mixed/`.

2. **"Differences from Other Verification Tests" section** (lines 289-324): Add `waitForLog` comparisons. After
   the existing "vs. Basic Test" table, add a new comparison table:

    ### vs. Wait-Log Tests

    | Aspect | Mixed-Wait Test | Wait-Log Test |
    |--------|-----------------|---------------|
    | **Wait Mechanisms** | waitForHealthy + waitForRunning | waitForLog (patterns) |
    | **Readiness Signal** | Container state (HEALTHY/RUNNING) | Log output patterns |
    | **Health Checks** | Required for app, none for db | Not required |
    | **Use Case** | State-based readiness | Log-based readiness |
    | **Timeout Handling** | Per-mechanism timeout | Single pattern timeout |

3. **Add cross-reference** to new wait-log-mixed scenario at end of README:

        > **See also**: For combining all three wait mechanisms, see `../wait-log-mixed/README.md`.

---

## Section 24: Update plugin-integration-test/dockerTest/examples/README.md

**Location**: `plugin-integration-test/dockerTest/examples/README.md`

**Current state**: Documents examples but only mentions `waitForHealthy` and `waitForRunning`.

**Actions**:

1. **"Wait Mechanisms" section** (~line 183): Add `waitForLog` as an option:

        **waitForLog:**
        - Wait for specific log patterns to appear in container output
        - Use for: services without health checks, legacy applications, services with specific startup messages
        - Configuration: Regex patterns per service

2. **"Choosing CLASS vs METHOD Lifecycle" table** (~lines 174-181): Add a note about `waitForLog` support:

    After the existing table, add:

        > **Note**: All three wait mechanisms (`waitForRunning`, `waitForHealthy`, `waitForLog`) work with both CLASS
        > and METHOD lifecycles. Choose the wait mechanism based on your service's readiness signals, not lifecycle.

3. **"When to Use Test Framework Extensions" section** (~lines 142-158): Add `waitForLog` to the list of supported
   configuration options.

4. **Add new table row for wait mechanism selection** showing when to use each:

    | Scenario | waitForRunning | waitForHealthy | waitForLog |
    |----------|----------------|----------------|------------|
    | Database with health check | ⚠️ | ✅ Recommended | Optional |
    | Legacy app with "Ready" log | ❌ | ❌ | ✅ Recommended |
    | Nginx static files | ✅ Recommended | Optional | ❌ |
    | App with startup delay but no health check | ❌ | ❌ | ✅ Recommended |
    | Microservice with gRPC health check | Optional | ✅ Recommended | Optional |

---

## Section 25: Update plugin-integration-test/dockerProject/README.md

**Location**: `plugin-integration-test/dockerProject/README.md`

**Current state**: Documents `dockerProject` DSL test configuration but `waitForLog` is incomplete.

**Actions**:

1. **"Test Configuration" feature matrix table** (~lines 446-456): This table currently shows checkmarks for each
   scenario (s1-s10) and features like `waitForHealthy` and `waitForRunning`. Add new rows for `waitForLog` and
   related properties:

    | Feature | s1 | s2 | s3 | s4 | s5 | s6 | s7 | s8 | s9 | s10 |
    |---------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
    | waitForHealthy | ✓ | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
    | waitForRunning | | ✓ | | | | | | | | |
    | **waitForLog** | | | | | | | | | | | ← New row (to be checked in future scenarios)
    | **rejectPatterns** | | | | | | | | | | | ← New row
    | **caseInsensitive** | | | | | | | | | | | ← New row
    | **verbose** | | | | | | | | | | | ← New row
    | **progressIntervalSeconds** | | | | | | | | | | | ← New row

    **Note**: Checkmarks will be added when scenarios that use `waitForLog` are implemented.

2. **DSL Examples**: Add `waitForLog` example to the DSL documentation section showing usage:

    ```groovy
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForLog.set([
            'app': ['Started Application in .* seconds']
        ])
        rejectPatterns.set([
            'app': ['Exception', 'ERROR']
        ])
        caseInsensitive.set(false)
        verbose.set(true)
        progressIntervalSeconds.set(30)
    }
    ```

3. **Add scenario description** showing `waitForLog` usage (if a new scenario is added for it):

    **Example scenario description format:**

        ### scenario-N-waitforlog-mode

        **Purpose:** Demonstrates `dockerProject` DSL with waitForLog readiness checking.

        **Features Tested:**
        - `test.waitForLog` - Log pattern-based readiness
        - `test.rejectPatterns` - Immediate failure patterns
        - `test.caseInsensitive` - Pattern matching options

---

## Section 26: Update plugin-integration-test/dockerWorkflows/README.md

**Location**: `plugin-integration-test/dockerWorkflows/README.md`

**Current state**: Documents workflow scenarios but does not mention `waitForLog`. Contains DSL examples (lines ~93-100)
showing `test { stack = ...}` configuration that only demonstrate existing wait mechanisms.

**Actions**:

1. **"Scenario Details" section**: Note that workflow test steps can use any wait mechanism:

        > **Wait Mechanisms**: The `test { }` block in workflow pipelines supports all wait mechanisms:
        > `waitForHealthy`, `waitForRunning`, and `waitForLog`. Configure these in the underlying
        > `dockerTest.composeStacks` definition.

2. **Update DSL examples** (lines ~93-100): Add comment showing `waitForLog` as an option in the dockerTest stack
   configuration:

    ```groovy
    // In dockerTest.composeStacks:
    myTest {
        // ...
        // Wait options: waitForHealthy, waitForRunning, or waitForLog
        // Configure the appropriate wait mechanism for your service's readiness signals
        waitForHealthy { ... }  // or waitForLog { ... }
    }
    ```

3. **Add port allocation for wait-log workflow scenarios** to Port Allocations table if new workflow scenarios are
   added that use `waitForLog`.

---

## Section 27: Update plugin-integration-test/buildSrc/README.md (Conditional)

**Location**: `plugin-integration-test/buildSrc/README.md`

**Current state**: Documents Docker image testing library for integration tests.

**Action**: If new validation utilities are added for `waitForLog` (e.g., `WaitForLogValidator`), document them:

1. **"Available Functions" section**: Add any new validation functions for log pattern verification.

2. **"Validation Tools Used" section** (if applicable): Document new validator classes.

**Note**: This update is conditional - only needed if new buildSrc utilities are added for `waitForLog` testing.

---

## Section 28: Update Individual Verification Scenario Comparison Tables

**Location**: Individual verification scenario READMEs that contain comparison tables

**Files to update**:
- `plugin-integration-test/dockerTest/verification/wait-healthy/README.md`
- `plugin-integration-test/dockerTest/verification/wait-running/README.md`

**Current state**: These READMEs contain comparison tables that reference each other (e.g., "Differences from Wait-Healthy
Test") but do not mention `waitForLog`.

**Actions**:

1. **wait-healthy/README.md**: Add `waitForLog` comparison to the "Differences from Basic Verification Test" or similar
   comparison table:

   | Aspect | Basic Test | Wait-Healthy Test | Wait-Log Test |
   |--------|-----------|------------------|---------------|
   | **Wait Strategy** | waitForHealthy | waitForHealthy | waitForLog |
   | **Container Check** | HEALTHY | HEALTHY | Log patterns |
   | **Use Case** | Basic validation | Health check required | Startup messages |

2. **wait-running/README.md**: Update "Key Differences from Wait-Healthy Test" table to include `waitForLog`:

   | Aspect | Wait-Healthy Test | Wait-Running Test | Wait-Log Test |
   |--------|------------------|-------------------|---------------|
   | **Health Check** | Configured | NO health check | Optional |
   | **Wait Configuration** | `waitForHealthy` | `waitForRunning` | `waitForLog` |
   | **Container State** | HEALTHY | RUNNING | Pattern matched |
   | **Use Case** | Full readiness | Quick start | Startup messages |

3. **Add cross-reference to new wait-log scenarios** at the end of each README:

       > **See also**: For log-based readiness checking, see `../wait-log-basic/README.md` and related scenarios.

**Note**: New `wait-log-*/README.md` files will be created during integration test implementation (per 0500 plan).
These are NEW scenarios, not updates to existing documentation files.

---

## Section 29: Clarification - New Scenario READMEs

**Not covered in this documentation plan**: The following README files will be **created during integration test
implementation** as part of the 0500 integration test plan:

- `plugin-integration-test/dockerTest/verification/wait-log-basic/README.md`
- `plugin-integration-test/dockerTest/verification/wait-log-reject/README.md`
- `plugin-integration-test/dockerTest/verification/wait-log-options/README.md`
- `plugin-integration-test/dockerTest/verification/wait-log-mixed/README.md`
- `plugin-integration-test/dockerTest/verification/wait-log-method/README.md`
- (and other wait-log scenarios per 0500 plan)

These are **new files**, not updates to existing documentation. They will follow the same structure and pattern as
existing verification scenario READMEs (`wait-healthy/README.md`, `wait-running/README.md`).

---

## Section 30: Files Explicitly NOT Requiring Updates

The following files were evaluated and do **NOT** require updates for the `waitForLog` feature:

1. **`plugin-integration-test/docker/README.md`**
   - **Reason**: Documents `docker` task features (build, tag, save, publish). The `waitForLog` feature is a
     compose/testing concern, not a docker image task concern.

2. **Individual example scenario READMEs** (e.g., `database-app/README.md`, `web-app/README.md`)
   - **Reason**: These demonstrate specific use cases. The main `examples/README.md` (Section 24) contains the
     decision guide which is the appropriate place for `waitForLog` documentation.

3. **`plugin-integration-test/dockerTest/verification/basic/README.md`**
   - **Reason**: Documents basic compose up/down functionality. No wait mechanism comparison needed.

4. **`plugin-integration-test/dockerTest/verification/lifecycle-class/README.md`**
5. **`plugin-integration-test/dockerTest/verification/lifecycle-method/README.md`**
   - **Reason**: These document lifecycle mechanics, not wait mechanisms. New `waitForLog` + METHOD lifecycle
     scenarios will have their own READMEs.

6. **`plugin-integration-test/dockerTest/verification/logs-capture/README.md`**
   - **Reason**: Focuses on log capture functionality, not wait mechanism comparison.

**Note**: `existing-images/README.md` was previously listed here but has been moved to Section 37 as it DOES contain
wait strategy documentation that needs updating.

7. **`plugin-integration-test/dockerTest/verification/multi-service/`** (directory)
   - **Reason**: This directory exists but has NO README.md file. No update needed.

8. **`plugin-integration-test/docker/scenario-99/README.md`**
   - **Reason**: Documents Docker Hub publishing integration tests. Unrelated to compose/wait mechanisms.

---

## Section 31: Update docs/usage/README.md

**Location**: `docs/usage/README.md`

**Current state**: This is the usage documentation index file. It references the "Docker Orchestration Guide" but:
- "Key topics" section (line ~30) mentions "Health checks and readiness waiting" but not log pattern waiting
- Quick example (lines ~45-55) only shows `waitForHealthy`
- Documentation Map doesn't specifically mention `waitForLog`

**Actions**:

1. **Update "Key topics" section** for Docker Orchestration Guide (~line 30):

   Current:
   ```
   - Health checks and readiness waiting
   ```

   Update to:
   ```
   - Health checks, readiness waiting, and log pattern matching
   ```

2. **Update Quick example** (~line 50) to add comment showing wait options:

   ```groovy
   dockerTest {
       composeStacks {
           myTest {
               files.from('src/integrationTest/resources/compose/app.yml')
               // Wait options: waitForHealthy, waitForRunning, waitForLog
               waitForHealthy {
                   waitForServices.set(['my-service'])
                   timeoutSeconds.set(60)
               }
           }
       }
   }
   ```

3. **Verify Documentation Map** shows `waitForLog` is covered in `usage-docker-test.md`.

---

## Section 32: Update docs/usage/spock-junit-test-extensions.md

**Location**: `docs/usage/spock-junit-test-extensions.md`

**Current state**: This file documents test framework extensions but only mentions `waitForHealthy` and `waitForRunning`:
- "Key Features" section (line ~37) lists "Health/Readiness Waiting: Waits for containers to reach RUNNING or HEALTHY
  status"
- System Properties table (line ~108) only lists `waitForHealthy` properties

**Actions**:

1. **Update "Key Features" section** (~line 37):

   Current:
   ```
   - **Health/Readiness Waiting**: Waits for containers to reach RUNNING or HEALTHY status
   ```

   Update to:
   ```
   - **Health/Readiness Waiting**: Waits for containers to reach RUNNING, HEALTHY, or emit specific log patterns
   ```

2. **Add `waitForLog` system properties to table** (~line 108). After the `waitForHealthy` entries, add:

   | System Property | Value | Description |
   |----------------|-------|-------------|
   | `docker.compose.waitForLog.services` | JSON map | Services with log patterns |
   | `docker.compose.waitForLog.rejectPatterns` | JSON map | Patterns that trigger failure |
   | `docker.compose.waitForLog.timeoutSeconds` | `60` | Log pattern timeout |
   | `docker.compose.waitForLog.pollSeconds` | `2` | Poll interval |
   | `docker.compose.waitForLog.caseInsensitive` | `false` | Case-insensitive matching |
   | `docker.compose.waitForLog.verbose` | `false` | Verbose logging |
   | `docker.compose.waitForLog.progressIntervalSeconds` | `0` | Progress summary interval |

3. **Add `waitForLog` example** to the build.gradle example (~line 50):

   ```groovy
   dockerTest {
       composeStacks {
           webAppTest {
               // ... existing config ...

               // Or use log pattern matching
               waitForLog {
                   waitForServices.set(['web-app': ['Started Application']])
                   timeoutSeconds.set(60)
               }
           }
       }
   }
   ```

---

## Section 33: Update IDE Templates (Optional - Low Priority)

**Location**: `docs/ide-templates/README.md` and `docs/ide-templates/gradle-docker-templates.xml`

**Current state**: The `dockerorch` template example only shows `waitForHealthy`.

**Actions** (optional):

1. **Update `dockerorch` template example** in README.md to show `waitForLog` as an option:

   ```groovy
   dockerTest {
       composeStacks {
           myTest {
               files.from('src/integrationTest/resources/compose/app.yml')

               // Option 1: Wait for health check
               waitForHealthy {
                   waitForServices.set(['my-service'])
                   timeoutSeconds.set(60)
               }

               // Option 2: Wait for log patterns
               // waitForLog {
               //     waitForServices.set(['my-service': ['Ready to accept connections']])
               //     timeoutSeconds.set(60)
               // }
           }
       }
   }
   ```

2. **Consider adding `waitForLog`-specific template** (e.g., `dockerorchlog`) that defaults to `waitForLog`
   configuration instead of `waitForHealthy`.

**Note**: This is low priority as developers can easily add `waitForLog` manually. The template is primarily for
quick starts with the most common pattern (`waitForHealthy`).

---

## General Guidelines

- Follow existing documentation patterns in the project
- Include working code examples
- Document all configuration options with defaults
- Include troubleshooting guidance
- Keep line length to 120 characters
- Use informal, concise, example-based style
- Write from the user's perspective using the DSL

---

## Section 34: Fix Broken Links from `usage-docker-orch.md` to `usage-docker-test.md`

**Issue**: 39 files in the codebase reference `usage-docker-orch.md` which does not exist. The correct file is
`usage-docker-test.md`. This is a pre-existing issue that should be fixed as part of this documentation update.

**Affected files in `plugin-integration-test/`:**

1. `dockerTest/README.md` (line ~15)
2. `dockerTest/examples/README.md` (multiple links in "Documentation" sections)
3. `dockerTest/examples/database-app/README.md` (line ~198)
4. `dockerTest/examples/web-app/README.md` (line ~103)
5. `dockerTest/examples/web-app-junit/README.md` (line ~106)
6. `dockerTest/examples/isolated-tests/README.md` (line ~110)
7. `dockerTest/examples/isolated-tests-junit/README.md` (line ~116)
8. `dockerTest/examples/stateful-web-app/README.md` (line ~136)
9. `dockerWorkflows/README.md`

**Action**: In each file, replace:
- `usage-docker-orch.md` → `usage-docker-test.md`

**Example fix:**

Current:
```markdown
- [dockerTest DSL Usage Guide](../../../../docs/usage/usage-docker-orch.md) - Complete DSL reference
```

Fixed:
```markdown
- [dockerTest DSL Usage Guide](../../../../docs/usage/usage-docker-test.md) - Complete DSL reference
```

**Note**: This fix also affects files in `docs/` that reference the old filename, but those are separate from the
`waitForLog` documentation effort. A full codebase search shows 39 affected files - prioritize fixing the
`plugin-integration-test/` files listed above as part of this documentation update.

---

## Section 35: Update CLAUDE.md for Correct Documentation Reference

**Location**: `CLAUDE.md` (project root)

**Current state**: Line ~89 in the "Adhere to Plugin Usage" section references `usage-docker-orch.md`:

```markdown
### Adhere to Plugin Usage
- Follow plugin usage:
   - for 'docker' DSL (e.g., tasks for build, tag, save, publish): `docs/usage/usage-docker.md`.
   - for 'dockerTest' DSL (e.g., using 'docker compose' for image testing): `docs/usage/usage-docker-orch.md`.
```

**Action**: Update the reference to use the correct filename:

```markdown
### Adhere to Plugin Usage
- Follow plugin usage:
   - for 'docker' DSL (e.g., tasks for build, tag, save, publish): `docs/usage/usage-docker.md`.
   - for 'dockerTest' DSL (e.g., using 'docker compose' for image testing): `docs/usage/usage-docker-test.md`.
```

**Note**: This is a critical fix because `CLAUDE.md` is the primary guidance document for AI coding agents working on
this project. A broken reference could lead to confusion when implementing features related to the `dockerTest` DSL.

---

## Section 36: Fix Broken Links in docs/ Directory

**Issue**: Files in the `docs/` directory also reference `usage-docker-orch.md`. While Section 34 covers
`plugin-integration-test/` files, this section covers `docs/` files.

**Priority files to fix in `docs/usage/` (active user documentation):**

1. `docs/usage/usage-docker-project.md`
2. `docs/usage/usage-docker.md`
3. `docs/usage/README.md`
4. `docs/usage/spock-junit-test-extensions.md`
5. `docs/usage/provider-patterns.md`

**Lower priority files in `docs/design-docs/` (historical design documents):**

Files in `docs/design-docs/done/` are historical documents that may intentionally reference the old name since that
was the name at the time the design was completed. These can be left as-is or updated for consistency:

- `docs/design-docs/done/rename-dockerorch-dockertest.md` - Documents the actual rename
- `docs/design-docs/done/docker-project/docker-project-dsl.md`
- `docs/design-docs/done/docker-orch-usage-readme-cleanup.md`
- Other files in `done/` subdirectory

**Action**: In each affected file in `docs/usage/`, replace:
- `usage-docker-orch.md` → `usage-docker-test.md`

**Verification command** (run from project root):
```bash
rg "usage-docker-orch" docs/usage/
```

**Note**: Focus on active documentation in `docs/usage/`. Historical design documents in `docs/design-docs/done/`
can be updated for consistency but are lower priority since they document past work.

---

## Section 37: Update plugin-integration-test/dockerTest/verification/existing-images/README.md

**Location**: `plugin-integration-test/dockerTest/verification/existing-images/README.md`

**Current state**: This README contains detailed wait strategy documentation but only mentions `waitForHealthy` and
`waitForRunning`. Key sections that need updating:
- "Mixed Wait Strategy" section (lines ~93-127) - Shows combining `waitForHealthy` and `waitForRunning` but not
  `waitForLog`
- "Wait Configuration" section (lines ~124-127) - Only lists two strategies
- "Use Case" section (lines ~337-360) - Discusses wait strategy selection without mentioning `waitForLog`

**Actions**:

1. **Update "Mixed Wait Strategy" section** (~line 93-127) to mention `waitForLog` as third option:

   After the existing DSL example, add:

   ```markdown
   > **Note**: For services that don't have health checks but emit startup log messages, you can also use
   > `waitForLog` to wait for specific patterns. See `../wait-log-basic/README.md` for details.
   ```

2. **Update "Wait Configuration" section** (~line 124-127) to include note about `waitForLog`:

   Current table only shows `waitForHealthy` and `waitForRunning`. Add a note:

   ```markdown
   > **Additional Option**: For log-based readiness detection, see `waitForLog` in the wait-log scenarios.
   ```

3. **Update "Use Case" section** (~line 337-360) bullet point 3 to include `waitForLog`:

   Current:
   ```markdown
   3. **Use mixed wait strategies appropriately**
      - Custom apps need health checks → use `waitForHealthy`
      - Public images without health checks → use `waitForRunning`
   ```

   Update to:
   ```markdown
   3. **Use mixed wait strategies appropriately**
      - Custom apps need health checks → use `waitForHealthy`
      - Public images without health checks → use `waitForRunning`
      - Services with startup log messages → use `waitForLog`
   ```

4. **Add cross-reference** at end of README:

   ```markdown
   > **See also**: For log-based readiness checking, see `../wait-log-basic/README.md` and related scenarios.
   ```

**Reason for inclusion**: This README was incorrectly excluded in an earlier review. It contains substantial wait
strategy documentation including a "Mixed Wait Strategy" section that explicitly compares `waitForHealthy` and
`waitForRunning`. Users reading this README to understand wait strategies should be informed about `waitForLog`.

---

## Section 38: Update docs/design-docs/requirements/use-cases/uc-7-proj-dev-compose-orchestration.md

**Location**: `docs/design-docs/requirements/use-cases/uc-7-proj-dev-compose-orchestration.md`

**Current state**: This is an active requirements document (Status: Implemented) that:
- Contains a broken link to `usage-docker-orch.md` (line 31)
- Mentions "Complex waiting strategies (log pattern matching)" in Phase 3 (line 114)
- Shows outdated DSL example `successWhenLogsMatch` (line 220) which doesn't match actual `waitForLog` DSL

**Actions**:

1. **Fix broken link** (line 31):

   Current:
   ```markdown
   See [usage-docker-orch.md](../../../usage/usage-docker-orch.md) for complete usage documentation.
   ```

   Update to:
   ```markdown
   See [usage-docker-test.md](../../../usage/usage-docker-test.md) for complete usage documentation.
   ```

2. **Update Phase 3 description** (lines 113-117) to reflect actual implementation:

   Current:
   ```markdown
   ### Phase 3: Advanced Orchestration
   - Method-level lifecycle management
   - Complex waiting strategies (log pattern matching)
   - Advanced polling configurations
   - Enhanced error handling and retries
   ```

   Update to:
   ```markdown
   ### Phase 3: Advanced Orchestration
   - Method-level lifecycle management
   - `waitForLog` - log pattern matching for readiness detection
   - Advanced polling configurations
   - Enhanced error handling and retries
   ```

3. **Update DSL example** (lines 218-221) to use actual `waitForLog` syntax:

   Current:
   ```groovy
   waitForHealthy {
     services       = ["db","api"]
     timeoutSeconds = 90
     pollSeconds    = 2
     successWhenLogsMatch = [ "api": "Started .* in .* seconds" ]  # Phase 3 feature
   }
   ```

   Update to:
   ```groovy
   waitForHealthy {
     services       = ["db","api"]
     timeoutSeconds = 90
     pollSeconds    = 2
   }
   waitForLog {
     waitForServices = [ "api": ["Started .* in .* seconds"] ]  // Log pattern matching
     timeoutSeconds = 120
   }
   ```

**Note**: This is an active requirements document marked "Implemented", not a historical design document. It should
reflect the actual implementation to serve as accurate documentation for the feature.

---

## Section 39: Source Code Comment Broken Links (Optional)

**Location**: Multiple source code files contain comments referencing `usage-docker-orch.md`

**Affected files**:
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeClassExtension.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtension.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/ComposeAnnotationHintListener.groovy`
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/ComposeAnnotationHintListenerTest.groovy`

**Action**: Replace `usage-docker-orch.md` → `usage-docker-test.md` in source code comments.

**Priority**: Low - Source code comments are less visible to users than documentation files. However, developers
reading the source code for context will encounter broken links if not fixed.

**Verification command**:
```bash
rg "usage-docker-orch" plugin/src/
```

---

## Section 40: Additional Design Docs with Broken Links (Low Priority)

**Location**: Files in `docs/design-docs/` outside of `done/` directory

**Affected files**:
- `docs/design-docs/project-reviews/2025-12-06-project-review.md`
- `docs/design-docs/design-decisions/requires-image-defer.md`

**Current state**: These files reference `usage-docker-orch.md` which no longer exists.

**Action**: Replace `usage-docker-orch.md` → `usage-docker-test.md` in each file.

**Priority**: Low - These are internal design documents not typically accessed by end users. However, developers
reviewing design history may encounter broken links.

**Note**: Section 36 already covers `docs/usage/` files. This section explicitly covers design-docs files that are
NOT in the `done/` subdirectory and therefore are not "historical" documents.

---

## Section 41: Update Functional Specifications Document

**Location**: `docs/design-docs/specifications/functional-specifications/functional-specifications.md`

**Current state**: This document lists functional requirements including:
- fr-26/fs-26: Wait for services to reach `running` state with configurable timeout
- fr-27/fs-27: Wait for services to reach `healthy` state with configurable timeout

No functional requirement exists for waiting on log patterns.

**Actions**:

1. **Add new functional requirement** for `waitForLog` feature. Insert after fr-27:

   ```markdown
   | uc-7        | fr-28          | fs-28            | The plugin shall wait for services to emit specific log patterns with configurable timeout | Draft  |
   ```

   **Note**: This requires renumbering existing fr-28 through fr-32 to fr-29 through fr-33.

2. **Alternative (less disruptive)**: Add as fr-32a or use next available number if requirements were added since
   original document:

   ```markdown
   | uc-7        | fr-33          | fs-33            | The plugin shall wait for services to emit specific log patterns with configurable timeout and regex support | Draft  |
   ```

**Priority**: Moderate - The functional specifications document is a requirements traceability document. Adding the
`waitForLog` requirement ensures the feature is formally documented as a system capability.

---

## Section 42: Update Technical Specifications Document

**Location**: `docs/design-docs/specifications/technical-specifications/technical-specifications.md`

**Current state**: This document contains extensive technical implementation documentation including:
- WaitConfig class definition (lines 582-597)
- waitForServices method specification (lines 437, 692, 1868)
- DSL examples showing waitForRunning/waitForHealthy (lines 270-291)
- Convention defaults for poll and timeout (lines 2214-2219)
- No documentation for waitForLog feature

**Actions**:

1. **Add WaitForLogConfig class documentation** near WaitConfig (after line ~597):

   ```groovy
   class WaitForLogConfig {
       String projectName
       Map<String, List<Pattern>> servicePatterns  // service -> compiled regex patterns
       Map<String, List<Pattern>> rejectPatterns   // service -> patterns that cause immediate failure
       int timeoutSeconds
       int pollSeconds
       boolean caseInsensitive
       boolean verbose
       int progressIntervalSeconds
   }
   ```

2. **Add waitForLogPatterns method specification** near waitForServices method (after line ~692):

   ```groovy
   /**
    * Wait for log patterns to appear in container logs.
    * @param config The WaitForLogConfig with services and patterns
    * @return WaitForLogResult with match status and timing information
    */
   WaitForLogResult waitForLogPatterns(WaitForLogConfig config);
   ```

3. **Update DSL examples** (lines 270-291) to include waitForLog:

   ```groovy
   dockerTest {
       composeStacks {
           stack("dbOnly") {
               files = [file("compose-db.yml")]
               waitForRunning {
                   services = ["another"]
                   timeoutSeconds = 60
               }
               waitForHealthy {
                   services = ["db"]
                   timeoutSeconds = 60
               }
               waitForLog {
                   waitForServices = ['app': ['Started Application in .* seconds']]
                   rejectPatterns = ['app': ['Exception', 'FATAL']]
                   timeoutSeconds = 120
                   pollSeconds = 2
                   caseInsensitive = false
                   verbose = true
                   progressIntervalSeconds = 30
               }
           }
       }
   }
   ```

4. **Add convention defaults for waitForLog** (after line ~2219):

   ```groovy
   stackSpec.waitForLog.timeoutSeconds.convention(60)
   stackSpec.waitForLog.pollSeconds.convention(2)
   stackSpec.waitForLog.caseInsensitive.convention(false)
   stackSpec.waitForLog.verbose.convention(false)
   stackSpec.waitForLog.progressIntervalSeconds.convention(0)
   ```

5. **Update JSON state file documentation** to include waitForLog metadata:

   ```json
   {
     "stackName": "myStack",
     "services": { ... },
     "waitForLog": {
       "configuredServices": ["app", "db"],
       "allPatternsMatched": true,
       "totalWaitTimeMs": 15230
     }
   }
   ```

**Priority**: Moderate - The technical specifications document is the implementation blueprint. It should reflect
all implemented features for developer reference.

---

## Section 43: Update DSL Architecture Rationale Document

**Location**: `docs/design-docs/dsl-architecture-rationale.md`

**Current state**: This document explains the four-DSL architecture and provides examples. Current examples only show
`waitForHealthy`:
- Line 159: `waitForHealthy.set(['postgres', 'redis'])`
- Line 264: `waitForHealthy.set(['app', 'db'])`

No mention of `waitForLog` as a wait mechanism option.

**Actions**:

1. **Update Scenario 2: Shared Compose Stacks example** (line ~159):

   Current:
   ```groovy
   dockerTest {
       composeStacks {
           sharedInfra {
               files.from('compose/shared-services.yml')
               waitForHealthy.set(['postgres', 'redis'])
           }
       }
   }
   ```

   Update to show all wait options:
   ```groovy
   dockerTest {
       composeStacks {
           sharedInfra {
               files.from('compose/shared-services.yml')
               // Wait mechanisms (use one or more as appropriate):
               // - waitForRunning: container process started
               // - waitForHealthy: Docker HEALTHCHECK passed
               // - waitForLog: specific log patterns found
               waitForHealthy {
                   waitForServices.set(['postgres', 'redis'])
               }
               // Optional: wait for application-specific readiness
               waitForLog {
                   waitForServices.set(['app': ['Server started on port']])
               }
           }
       }
   }
   ```

2. **Update "Scenarios Where dockerProject Is Sufficient" example** (line ~264):

   Current:
   ```groovy
   test {
       compose.set('src/integrationTest/resources/compose/app.yml')
       waitForHealthy.set(['app', 'db'])
   }
   ```

   Add comment about wait options:
   ```groovy
   test {
       compose.set('src/integrationTest/resources/compose/app.yml')
       // Wait for containers - choose based on service capabilities:
       waitForHealthy.set(['app', 'db'])  // For services with health checks
       // Or: waitForLog.set(['app': ['Ready to accept connections']])  // For log-based readiness
   }
   ```

**Priority**: Moderate - This is an active architectural reference document. Users consulting it for DSL guidance
should see all three wait mechanisms as options.
