Added `Gif.creator`, the Giphy channel or user that made a GIF (`GifCreator` with `username`, `avatarUrl` and `isVerified`), so apps can show the creator attribution Giphy asks for. It is null when Giphy names no creator.

  - ABI: breaking for previously compiled consumers that construct `Gif` or call its generated data-class methods; the constructor gained `creator`.
  - Source: additive; `creator` defaults to null.
