On Apple, a call no longer crashes the app when an SFT request fails or times out. The empty SFT response is now passed to AVS as a null buffer, so AVS handles the error as on Android.

  - ABI: no change
  - Source: no change
  - Migration: none
