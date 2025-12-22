# Unit Test Coverage Gaps

This document records unit test coverage gaps that cannot be practically closed due to language/framework limitations or
code design constraints.

## Package: com.kineticfire.gradle.docker.junit.service

### Current Coverage: 96.7% instruction, 95.2% branch

### Documented Gaps

#### 1. Groovy Closure Bytecode (`JUnitComposeService._parsePortMappings_closure6`)

**Gap**: 66% instruction coverage (41 of 121 instructions missed)

**Reason**: Groovy generates additional bytecode for closure handling including:
- Closure initialization and delegate resolution
- Dynamic type coercion and casting
- GString interpolation machinery
- Null safety checks

**Branch Coverage**: 100% (all logical branches are covered)

**Justification**: The bytecode instructions not covered are internal Groovy runtime machinery, not application logic.
All logical branches are tested. These instructions cannot be individually targeted by unit tests.

#### 2. Unreachable Branch in `captureLogs` - `tailLines > 0` (Line 228)

**Gap**: 1 of 2 branches missed for `if (config.tailLines > 0)`

**Reason**: The `LogsConfig` constructor normalizes `tailLines` using `Math.max(1, tailLines)`, ensuring `tailLines`
is always >= 1. Therefore, the else branch (`tailLines <= 0`) is unreachable.

**Location**: `JUnitComposeService.groovy:228`

**Justification**: This is defensive code that handles edge cases at the configuration layer. The validation in
`LogsConfig` ensures this branch is never reached in normal operation.

#### 3. Unreachable Branches in `captureLogs` - services check (Line 232)

**Gap**: 2 of 8 branches missed for `if (config.services && !config.services.isEmpty())`

**Reason**: The `LogsConfig` constructor normalizes null services to an empty list (`this.services = services ?: []`).
Combined with Groovy's short-circuit evaluation:
- `config.services` is always truthy (never null, at minimum an empty list)
- Some boolean evaluation branches are unreachable

**Location**: `JUnitComposeService.groovy:232`

**Justification**: Defensive code for null safety. The LogsConfig constructor guarantees services is never null.

#### 4. Unreachable Branch in `parseServiceState` - restart check (Line 310)

**Gap**: 1 of 6 branches missed for `lowerStatus.contains('restart') || lowerStatus.contains('restarting')`

**Reason**: The string 'restarting' contains 'restart', so:
- If status contains 'restarting', it also contains 'restart'
- The first condition is true, short-circuit skips the second
- A case where `contains('restart')` is false but `contains('restarting')` is true is impossible

**Location**: `JUnitComposeService.groovy:310`

**Justification**: Logical tautology in the OR condition. The second part is redundant for clarity but creates an
unreachable branch due to short-circuit evaluation.

#### 5. For Loop Compiler-Generated Branches (Lines 177, 267)

**Gap**: 1 of 4 branches missed for each `for` loop

**Reason**: Groovy/Java for-each loops generate 4 bytecode branches:
1. Iterator null check (entry)
2. hasNext() true (continue iteration)
3. hasNext() false (exit loop)
4. Collection null/empty handling

The null iterator branch is unreachable when iterating over guaranteed non-null collections.

**Location**: `JUnitComposeService.groovy:177, 267`

**Justification**: Standard compiler-generated branches for iteration. Collections are guaranteed non-null at these
points.

## Summary

| Gap Type | Instructions Missed | Branches Missed | Justification |
|----------|---------------------|-----------------|---------------|
| Groovy closure bytecode | 41 | 0 | Language runtime, not logic |
| LogsConfig normalization | 0 | 3 | Defensive code, unreachable |
| Short-circuit tautology | 0 | 1 | Logical OR with substring |
| For-loop generated code | 0 | 2 | Compiler-generated, collections non-null |

**Total**: 41 instructions, 6 branches - all documented as acceptable gaps.
