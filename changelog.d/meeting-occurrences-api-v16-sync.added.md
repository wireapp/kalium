Added API v16 meeting sync through `/meetings/list`; meetings are synced only when the negotiated backend API version supports v16 or newer.

  - ABI: additive
  - Source: additive
  - Behavior: meetings sync runs only on backends that support API v16; older backends remain no-op/unsupported for meetings.
