# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Documentation

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0100-dsl-user-description.md` - DSL syntax and user-facing behavior
- `add-wait-for-log-0200-implementation.md` - Implementation details

## Purpose

Define documentation updates required for the `waitForLog` feature. This includes:
- User-facing usage documentation
- Changelog entries
- README updates
- API documentation (Javadoc/Groovydoc)

This document should contain documentation tasks ONLY.

## Checklist

- [ ] Update `docs/usage/usage-docker-orch.md`
  - [ ] Add `waitForLog` section with examples
  - [ ] Document execution order: `waitForRunning` -> `waitForHealthy` -> `waitForLog`
  - [ ] Document all configuration options
  - [ ] Add troubleshooting section
- [ ] Update `CHANGELOG.md`
  - [ ] New `waitForLog` feature description with full lifecycle support (CLASS and METHOD)
  - [ ] Document execution order change
- [ ] Update `README.md` feature list
- [ ] Document known limitations
- [ ] Document performance considerations

### Final Verification
- [ ] All documentation is complete and accurate
- [ ] Examples are tested and working
- [ ] No broken links

---

## Notes from Implementation

The following content was extracted from the implementation document (`add-wait-for-log-0200-implementation.md`)
and should be incorporated into the documentation updates below.

### Usage Documentation Updates (Section 14 from Implementation)

| File | Updates Required |
|------|------------------|
| `docs/usage/usage-docker-orch.md` | Add `waitForLog` section with examples |
| `CHANGELOG.md` | Document new `waitForLog` feature with full lifecycle support |
| `README.md` | Update feature list |

### Performance Considerations to Document (Section 15 from Implementation)

- Each poll iteration fetches complete log history (to ensure patterns that appeared early are not missed)
- Use longer `pollSeconds` for high-volume logging services
- Recommended minimum: `pollSeconds.set(2)`
- Consider `waitForHealthy` for high-volume services when possible
- The `progressIntervalSeconds` feature allows visibility into long waits without verbose per-poll logging

#### Service Name Validation

The implementation validates that all configured service names exist in the compose project at the start
of the wait loop. This provides:

1. **Early failure for typos**: A misspelled service name fails immediately with a clear error message
   listing available services, rather than timing out after 60+ seconds
2. **Better user experience**: The error message includes available service names and hints for resolution
3. **Debugging aid**: Helps identify configuration mismatches between `waitForLog` and docker-compose.yml

**Example error for unknown service:**
```
Service(s) not found in compose project 'myproject': [appp].
Available services: [app, db, redis]

Hint: Check for typos in service names in your waitForLog configuration.
```

#### Memory Usage Warning

**Important**: The `fetchServiceLogs()` method retrieves ALL container logs (`tailLines = 0`) on each poll
iteration. For containers with extremely high log volume (thousands of lines per second over extended
periods), this can cause memory pressure.

**Mitigations to document:**
1. Use longer `pollSeconds` values (e.g., 5-10 seconds) to reduce fetch frequency
2. Choose patterns that appear early in startup to minimize total wait time
3. For high-volume services, prefer `waitForHealthy` when health checks are available
4. Monitor JVM heap usage during integration tests with verbose logging services

**Future Enhancement**: Consider adding a `maxLogLines` property in a future release to cap log retrieval.

### Known Limitations to Document (Section 16 from Implementation)

#### Container Restart During Wait

**Limitation**: If a container crashes and restarts during the wait period (e.g., due to `restart: always`
policy in docker-compose.yml), the log output may reset but the pattern match state is NOT reset.

**Behavior**: Previously matched patterns remain marked as "matched" even if the container restarts and
those log lines are no longer present. This means:
- If Pattern A matched at t=5s, then the container restarts at t=10s, Pattern A is still considered matched
- The wait may succeed even though the final running container never produced the matched log line
- This could mask container instability issues

**Workarounds to document:**
1. Use health checks (`waitForHealthy`) for critical stability requirements
2. Consider removing `restart: always` during test execution
3. Use patterns that appear late in startup to reduce restart masking window

---

## Documentation Specifications

### 1. Usage Documentation (`docs/usage/usage-docker-orch.md`)

TODO: Define the content structure and examples to add

### 2. CHANGELOG Entry

TODO: Define the changelog entry format and content

### 3. README Updates

TODO: Define the feature list entry

---

## Documentation Style Guidelines

- Follow existing documentation patterns in the project
- Include working code examples
- Document all configuration options with defaults
- Include troubleshooting guidance
- Keep line length to 120 characters
