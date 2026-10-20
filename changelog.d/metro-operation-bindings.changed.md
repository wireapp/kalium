### Changed
- Authentication, fresh feature accessors, backup, calling, Cells and long-work observation now resolve individual dependencies through Metro while retaining their existing owners and cached state.
- Account startup explicitly starts initial-sync work after registering the session owner.

### Migration
Existing SDK accessors and public constructors remain supported; no consumer migration is required for these compatibility facades.

### Compatibility
ABI: existing published signatures retained; internal runtime preparation is additive.
Source: existing public constructors and accessor types retained.
