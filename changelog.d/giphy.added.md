Added GIF search and sending through the backend's Giphy proxy: `UserSessionScope.giphy` offers `searchGifs`, `getTrendingGifs`, `getGifSuggestions`, `downloadGif` and `sendGifMessage`, and `AuthenticatedNetworkContainer.giphyApi` exposes the proxy endpoints. Search and trending use the pg rating and skip GIFs over 3 MiB. A backend without the proxy returns `GetGifsResult.Unavailable`. GIF files download directly from Giphy's CDN without Wire credentials. Sending posts an optional caption text message, which carries the reply quote, followed by the GIF as an `image/gif` asset, which is how web and the old iOS app send GIFs.

  - ABI: additive
  - Source: additive; custom `AuthenticatedNetworkContainer` implementations need a `giphyApi` member
