# Guiding Development Philosophies

## Keep it Simple, Stupid (KISS)

The "Keep it Simple, Stupid" (KISS) principle means: keep things as simple as possible, but no  simpler.
* Keep solutions as simple as possible—no simpler.
* Avoid unnecessary complexity and over-engineering.
* Prefer clarity over clever tricks or hacks.
* Minimize moving parts: small functions, few dependencies.
* Implement only what’s needed for current requirements.
* Simple code is easier to read, test, debug, and extend.


## You Aren't Gonna Need It (YAGNI)

The "You Aren’t Gonna Need It" (YAGNI) principle means: don’t build features or write code until they are actually
needed.
* Build only what current requirements demand.
* Don’t add speculative features, hooks, or options.
* Delay abstractions until real duplication or need appears.
* Keep code minimal; remove unused params and config.
* Let tests and user feedback justify new functionality.
* Park ideas in the backlog—don’t preemptively code them.


## Don’t Repeat Yourself (DRY)

The "Don’t Repeat Yourself" (DRY) principle means: every piece of knowledge or logic in a system should have a single,
unambiguous representation.
* Maintain a single source of truth for each piece of logic or data.
* Eliminate duplication—no copy-paste or parallel implementations.
* Extract shared behavior into reusable functions, modules, or services.
* Centralize constants/schemas/business rules in one place.
* Refactor when repetition appears*(after 2–3 instances); avoid premature abstraction.
* Keep docs/tests in sync with the canonical implementation.


## SOLID

SOLID is a set of five principles in object-oriented design that help create software that is maintainable, flexible,
and scalable:
1. S — Single Responsibility: one reason to change per class/module
1. O — Open/Closed: open for extension, closed for modification
1. L — Liskov Substitution: subclasses (subfunctions, submodules, etc.) must be usable in place of their parent class/
   function/module/etc.
1. I — Interface Segregation: don’t force clients to depend on unused methods
1. D — Dependency Inversion: depend on abstractions, not concrete implementations

In short, SOLID encourages clean, modular, and extensible code.


## Separation of Concerns (SoC)

Separation of Concerns (SoC) means dividing a system into distinct parts, each responsible for a single concern or
function (e.g., UI, business logic, data) without overlapping too much with others. This reduces coupling, and by
isolating concerns, you reduce entanglement, making it easier to modify, test, and scale your system.
* Partition the system by concerns (UI, domain, data, etc.).
* Each module owns one responsibility; prevent cross-concern leakage.
* Define clear interfaces between concerns.
* Reduce coupling; increase cohesion.
* Enable independent changes and focused testing.
* Promote reuse of isolated components.
* Support parallel work across teams.
* Organize code as layered or feature-based modules.


In short, SoC makes complex systems manageable by dividing them into clear, independent pieces.


## Convention over Configuration (CoC)

Convention over Configuration (CoC) means using sensible defaults and established conventions so developers don’t need
to write extensive configuration. It reduces boilerplate, speeds up development, and lets developers focus on
application logic instead of setup details.

The benefit is less boilerplate code, faster development, and fewer mistakes, since developers can focus on what makes
their application unique instead of wiring up repetitive details.

Common examples:
1. Use Naming Conventions: Example: In frameworks like Rails or Django, naming a class User automatically maps it to a
   users table without extra setup. Stick to standard naming patterns to avoid redundant configuration
1. Adopt Framework Defaults: Example: Spring Boot auto-configures database connections, logging, and web servers.
   Override only when necessary instead of redefining defaults.
1. Leverage Standard Project Structures:  Example: Place code in conventional directories (`src/main/java`,
   `src/test/java` in Maven/Gradle) so build tools and IDEs work without custom paths.
1. Generally, follow established defaults, naming, and structure so the system can configure itself with minimal effort

In short, CoC reduces decision fatigue and encourages consistency across projects.


## Fail Fast

Fail Fast means that software should detect problems early, report them immediately, and stop execution rather than
continuing in an invalid state.
* Prefer sensible defaults; configure only when deviating.
* Follow standard naming and directory structures.
* Use framework conventions (routing, ORM mappings, lifecycle hooks).
* Generate scaffolds/templates; avoid hand-wiring boilerplate.
* Keep configuration minimal, centralized, and declarative.
* Document conventions once; enforce with linters and code reviews.
* Outcome: less boilerplate, faster delivery, fewer mistakes.

Common examples:
1. Reporting errors immediately when input is invalid, instead of silently ignoring it
1. Defensive programming checks (e.g., null checks, boundary checks)
1. Validate Inputs Immediately: Check function arguments, user input, and external data as soon as they’re received. If
   something is invalid, throw an exception right away instead of letting bad data propagate.
1.  Use Assertions / Preconditions: Assert assumptions about program state so violations are caught early during
    development
1. Design with failures, Not Silent Failures: If something unexpected happens, raise an error instead of returning a
   default or null value that hides the issue.

In short, Fail Fast makes errors visible and contained, reducing the risk of subtle bugs and making systems more robust


## Design for Change / Extensibility

Design for Change / Extensibility means building software so it can adapt to new requirements with minimal changes to
existing code.
* Enable new features via extension, not modification.
* Use modular design with clear, stable interfaces/contracts.
* Depend on abstractions; inject dependencies for swappability.
* Provide extension points (plugins, events, strategy/adapters).
* Favor composition over inheritance to add behavior safely.
* **Encapsulate variability; keep cores small and cohesive.
* Maintain backward compatibility; version public contracts.
* Balance with YAGNI—add extension hooks only when justified.


APPLY WITH CAUTION and balance with the YAGNI principle:
- Use good modular design and clean abstractions (so extending later is possible)
- Avoid implementing speculative features until there’s a real need (YAGNI)

That way, you’re prepared for change without wasting effort on imaginary futures.

In short: anticipate change by making the system easy to extend, not easy to rewrite.


## Simplicity Favors Regularity

Simplicity favors regularity means you get simpler, more reliable systems by making things uniform—use the same
patterns, shapes, and rules everywhere instead of many special-case designs. The idea (popularized in computer
architecture) applies directly to software: fewer “one-offs” → less to remember, fewer edge cases, and easier reasoning.

Why it helps
1. Lower cognitive load: Developers can predict how code works before reading every line.
1. Faster onboarding & changes: New features fit familiar templates; refactors touch fewer surprises.
1. Fewer bugs: Regular patterns reduce special-case logic (where defects hide).
1. Better tooling & automation: Consistency enables code generation, static checks, and shared utilities.
1. Easier testing: Standardized inputs/outputs make tests repeatable and reusable.

How to apply it:
1. Standard shapes for components: e.g., all handlers follow Input -> Domain -> Output with the same error type.
1. Consistent naming and layout: one project structure, one naming scheme (verbs for commands, nouns for models).
1. Uniform API contracts: same pagination, error envelope, and auth across endpoints.
1. One way to do common tasks: choose a single logging, config, and validation approach; avoid parallel, competing
   patterns.
1. Regular data models: shared primitives (IDs, timestamps, money types) with common validation and serialization.
1. Template the flow: scaffolds for “feature slices” that create controller/service/repository stubs with identical
   wiring.
1. Centralized cross-cutting: middleware for auth, tracing, and retries instead of bespoke code in each feature.
1. Repeatable function sizes: small, single-purpose functions with similar signatures; prefer guard clauses to deep
   nesting.

### Guardrails

Regularity isn’t rigidity: break the pattern only when the domain clearly demands it—and document the exception.
Otherwise, prefer the boring, familiar path; that’s where simplicity lives.


## Write Code For a Human to Understand

Follow the principle that "Programs must be written for people to read, and only incidentally for machines to execute."
as stated by Harold Abelson and Gerald Jay Sussman.
* Write for people first; machines are incidental.
* Make intent obvious: meaningful names, consistent structure, logical organization.
* Prefer clarity over cleverness or terse tricks.
* Keep functions small and focused; minimize nesting.
* Comment the 'why', not the what; use brief docstrings.
* Separate concerns and expose clear interfaces.
* Readable code is easier to debug, refactor, and extend.
* Prioritize readability to lower long-term maintenance cost.
* Unreadable code blocks collaboration and breeds errors.
* If code is hard to follow, refactor until it’s clear.

Techniques for Writing Human-Readable Code
1. Use Meaningful Names
    1. Choose descriptive variable, function, and class names (calculateInvoiceTotal instead of calcInv)
    1. Avoid cryptic abbreviations unless they’re universally understood
1. Write Focused, Small Functions
    1. Each function should do one thing well. Smaller units are easier to read, test, and reuse.
1. Document the “Why,” Not the “What”
    1. Use inline comments or docstrings to explain why something is done a certain way, not just restating the code.
    1. Example:
```
# Using binary search for efficiency since the list is sorted
index = binary_search(sorted_list, target)
```
1. Prefer Clarity Over Cleverness
    1. Avoid overly compact, tricky syntax. Favor code that is slightly longer but obvious to the reader
1. Use Consistent Formatting & Style
    1. Stick to a style guide (e.g., PEP 8 for Python, Google Java Style)
    1. Consistent indentation, spacing, and naming conventions make code predictable
1. Break Up Large Blocks
    1. Split long methods, conditionals, or expressions into smaller parts with clear names
    1. Example: Replace nested if statements with guard clauses or helper methods
1. Leverage Self-Describing Code Structures
    1. Replace “magic numbers” or unexplained values with constants or enums
    1. Example: MAX_RETRY_COUNT = 3 instead of just 3
1. Write Unit Tests with Clear Names
    1. Good test names document expected behavior (test_login_fails_with_invalid_password)
    1. Tests serve as living documentation for how code is meant to be used
1. Keep Related Code Together
    1. Group functions, classes, and files logically (e.g., “auth” vs. “billing”)
    1. Avoid scattering related logic across multiple places
1. Refactor Ruthlessly
    1. Continuously improve readability as you work — apply the Boy Scout Rule: leave the code cleaner than you found it

The key idea: Readable code communicates intent. Future readers (including you) should be able to grasp the purpose and
flow quickly, without guessing or reverse-engineering the logic.