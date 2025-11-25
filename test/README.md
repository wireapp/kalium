# Test Infrastructure

Testing support modules providing mocks, test utilities, and benchmarks.

**Modules:**
- `mocks` - Mock implementations for network models and external dependencies
- `data-mocks` - Mock data for testing (users, conversations, messages)
- `benchmarks` - Performance benchmarks for critical operations
- `tango-tests` - End-to-end integration tests

**Dependencies:** Can depend on any production module for testing purposes
**Used by:** Test code in all modules
**Guidelines:** Keep test utilities separate from production code. Never import test modules in production code.
