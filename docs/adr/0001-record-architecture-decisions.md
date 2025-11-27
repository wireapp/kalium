# 1. Record architecture decisions

Date: 2025-11-18

## Status

Accepted

## Context

We need to document architectural decisions made in Kalium to maintain a clear record of why
certain technical choices were made. This will help current and future team members understand
the reasoning behind important decisions and provide context for future changes.

## Decision

We will use Architecture Decision Records in the code and as part of the review process.
We will use the [Lightway ADR template](0000-template-lightway-adr.md) to keep the ADRs simple and
easy to maintain.

## Consequences

- We need to add a new folder to the repository, `docs/adr`, to keep the architecture decision
  records.
- Whenever a new refactoring, library, or significant architectural change is introduced, a new ADR
  should be created.
- You can always request in the Pull request review process to add a new ADR, if you think it's
  necessary.
- This provides better documentation and knowledge sharing across the team.