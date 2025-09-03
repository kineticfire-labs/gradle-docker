# Style Guide

## General Code Structure, Style, and Modularity

### File Rules
1. A file must never exceed 500 lines. As you approach 400, refactor into smaller modules/packages.
1. One purpose per file. Don’t mix unrelated responsibilities.

### Function Rules
1. A function must never exceed 30–40 lines. As you approach 25, split into helpers.
1. Each function should do one thing only.
1. No more than 4 parameters. If you need more, pass a map/config/object.
1. Cyclomatic complexity ≤ 10. If higher, refactor.
1. Nesting depth ≤ 3. Use guard clauses, early returns, or helper functions.

### Module/Class Rules
1. A class must never exceed 500 lines or 10–15 methods. Split by responsibility (SRP).
1. Encapsulate behavior. Keep data and methods cohesive.

### Line & Style Rules
1. Max line length: 120 characters. Wrap long expressions.
1. Use 'space' characters for indentation, never use hard 'tab' characters
1. Break long statements across multiple lines for readability.
1. Use meaningful names. Prefer clarity over brevity (e.g., calculateInvoiceTotal, not calcInv).

### Documentation & Comments
1. Document the “why,” not the “what.” Add intent, trade-offs, and non-obvious decisions.
1. Every file/class/function needs a short docstring/header. Purpose, inputs, outputs, side effects.
1. Keep the README.md and any module-specific README.md files up-to-date

### Testing & Structure
1. Write small, focused tests with descriptive names; tests are living docs.
1. Keep related code together (e.g., auth, billing), avoid “God” modules.

### Refactoring Mindset
1. If it’s hard to name, it’s doing too much—split it.
1. Leave the code cleaner than you found it (Boy Scout Rule).
1. Prefer clear code over clever code. Optimize only when needed.
1. Consistency beats cleverness.

If you ever find yourself pushing against these limits, it’s usually a signal your design can be split, clarified, or
refactored.

## Documentation Styles
- Use inline comments to explain complex code, non-obvious behavior, or design decisions (the "why")
- Wrap all lines in plain-text or Markdown docs at a maximum of 120 characters; if more, then manually wrap lines
- If a line is significantly shorter than 120 characters (e.g., after editing), reflow it and adjust subsequent lines to 
  maintain consistent wrapping.  
