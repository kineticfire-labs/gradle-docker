# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Overview

## Purpose

The purpose of this document is to provide an overview for the new functionality and explain all the supporting
documents that, together, represent the full design document.

## Checklist

### Design Document Completion

- [ ] DSL / User Description complete (0100)
- [ ] Implementation plan complete (0200)
- [ ] Documentation notes prepared (0600)

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
6. `add-wait-for-log-0500-functional-tests.md`: Defines integration tests.
7. `add-wait-for-log-0600-documentation.md`: Notes on documentation updates/additions/changes (informal notes, not a
   formal plan).

**Note**: Unit tests, functional tests, and integration tests do not have separate plan documents. Test requirements
should be derived from the implementation plan and existing project testing standards documented in
`docs/project-standards/testing/`.
