# Prime Context for Claude Code

Use the command `tree -a -I '.claude|.serena|.gitignore|.idea|.gradle|build'` to get an understanding of the project 
structure.

Starting with reading the `CLAUDE.md` and `README.md` files, if they exist, to get an understanding of the project.

Read the design documents at `docs/design-docs/`.  It is very important to understand
- the design
- the current implementation status (what is planned vs. in-progress vs. implemented)
- the workflow defined by these documents:  use case → requirement → specification
- the format of these documents when creating or modifying them
- how to update the document metadata--especially the status, version, and date--when creating and modifying the 
documents

Read the project standards and practices at `docs/project-standards/`.

Read key source files:
- plugin source code: `plugin/src`
- integration test source code: `plugin-integration-test/src`

Explain back to me:
- Project structure
- Project purpose and goals
- Key files and their purposes
- Any important dependencies
- Any important configuration files
