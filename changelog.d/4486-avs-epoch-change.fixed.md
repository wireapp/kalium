Updated AVS to 10.5.19 to fix handling of late MLS epoch changes during calls. Android and Apple consumers use the matching bindings and runtime.

  - ABI and source: no Kalium API changes.
  - Behavior: includes the upstream AVS calling fixes.
  - Migration: no code changes required; rebuild and embed the matching Apple AVS runtime.
