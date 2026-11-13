On iOS, the app no longer crashes on activation after a video call and the release of its last account. `AVSFlowManager` now owns AVS's only event loop, as in the native iOS client; app extensions and macOS keep wcall's own loop.

  - ABI: no change
  - Source: no change
  - Migration: none
