Fixed certificate enrollment crashes caused by MLS initialization failures. Failed clients are not cached, and incoming events remain pending for retry when initialization fails.

This change reports persisted credential mismatches as failures; it does not repair them.
