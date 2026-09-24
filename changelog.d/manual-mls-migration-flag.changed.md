Added `allowManualMigration` to MLS migration configuration DTOs, domain models, and persisted configuration, defaulting to `false` when absent. The flag is preserved through configuration mappings without changing automatic migration scheduling.

  - ABI: consumers compiled against the previous configuration constructors must be recompiled.
  - Source: existing constructor calls remain compatible through the default value.
