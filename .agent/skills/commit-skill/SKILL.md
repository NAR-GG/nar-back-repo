---
name: commit-message-convention
description: Enforces the team's git commit message convention with types, summary, and changes sections.
---
# Git Commit Message Convention

You must strictly follow this format for all git commits:

<type>: <subject>

## Summary
<Description of the purpose and context of the changes>


## Changes

- <Detailed list of changes>

- <Use bullet points>



### Types

- feat: New feature

- fix: Bug fix

- refactor: Code restructuring without behavior change

- chore: Build, config, dependencies, or auxiliary tool changes

- style: Formatting, missing semi-colons, etc. (no code change)

- docs: Documentation changes

- test: Adding or refactoring tests



### Rules

1. The Subject line must be concise and descriptive.

2. The `## Summary` section should explain *why* the change is necessary.

3. The `## Changes` section should list *what* was modified.

4. Use Korean for the content of the message (Subject, Summary, Changes) as per project history.