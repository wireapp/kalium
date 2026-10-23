Upgraded Kotlin to 2.4.20, KSP to 2.3.12, Mokkery to 3.5.1, and SKIE to 0.10.15. Migrated the ABI validation and Yarn lock configuration to the current Kotlin Gradle APIs and refreshed the JS toolchain lockfile.

Tests now use literal arguments and `matches` instead of Mokkery's obsolete `eq` and `matching` APIs. ABI references include Android and reflect the new compiler's generated JVM declarations. Public Kotlin source signatures are unchanged.
