# Test Infrastructure

Testing support modules providing mocks, test utilities, and benchmarks.

**Modules:**
- `mocks` - Mock implementations for network models and external dependencies
- `data-mocks` - Mock data for testing (users, conversations, messages)
- `benchmarks` - JVM persistence benchmarks using JMH
- `android-benchmarks` - Android persistence benchmarks using SQLCipher
- `benchmark-fixtures` - Shared Kotlin source directory compiled into both benchmark runners; not a Gradle module
- `tango-tests` - End-to-end integration tests

**Dependencies:** Can depend on any production module for testing purposes

**Used by:** Test code in all modules

**Guidelines:** Keep test utilities separate from production code. Never import test modules in production code.
