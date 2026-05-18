# ADR 0001: Layered JavaFX Architecture

## Status

Accepted.

## Context

The application needs a desktop UI, SQL Server persistence, crawler workflows, AI analysis, scheduler execution, and report export. Without clear boundaries, UI controllers could become too large and difficult to test.

## Decision

Use a layered architecture:

- JavaFX controllers for UI interaction.
- Services for workflows.
- Repositories for JDBC access.
- Crawler adapters for platform-specific logic.
- Analyzer adapters for AI and rule-based analysis.
- Report exporters for PDF and Excel output.
- `ApplicationContext` as the composition root.

## Consequences

Benefits:

- Easier testing.
- Clearer responsibilities.
- Reduced coupling between UI, database, crawler, and AI provider code.
- Simpler documentation.

Tradeoffs:

- More classes than a small single-layer demo.
- Requires discipline to keep controllers thin.
