# 1. Record architecture decisions

Date: 2025-12-03

## Status

Accepted

## Context

We need to document architectural decisions made during the development of the project in Kalium.
This will help us keep track of the reasoning behind our decisions and provide context for future
developers working on the project.

## Decision

We will use Architecture Decision Records in the code and as part of the review process.
We will use the [Lightway ADR template](0000-template-lightway-adr.md) to keep the ADRs simple and
easy to maintain.

## Consequences

- We need to add a new folder to the repository, `docs/adr`, to keep the architecture decision
  records.
- Whenever a significant architecture decision is made, we need to create a new ADR file in that folder.
- We need to review the ADRs periodically to ensure they are still relevant and up-to-date.
- This will help us maintain a clear record of our architecture decisions and the reasoning behind them,
  which will be useful for future reference and onboarding new team members.
