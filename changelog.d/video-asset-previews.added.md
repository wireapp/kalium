Support optional, separately encrypted video image previews in asset messages. Upload parameters can include a preview; received previews are persisted and can be resolved without downloading the original video through `MessageScope.getAssetMessagePreview`.

Existing Kotlin/JVM constructor and copy entry points are retained, including the old Swift initializer and copy selectors, as deprecated compatibility overloads.
