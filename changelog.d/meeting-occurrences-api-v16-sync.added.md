Added API v16 meeting sync through `/meetings/list`; meetings are synced only when the negotiated backend API version supports v16 or newer.

  - ABI: additive
  - Source: additive
  - Behavior: meetings sync runs only on backends that support API v16; older backends remain no-op/unsupported for meetings.
  - Migration: meeting data is gated by API v16 support; a backend-provided meetings feature flag is expected later and is not used yet.
