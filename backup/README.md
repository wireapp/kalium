# Cross-platform Backup

This module implements the Wire Cross-platform Backup solution.
Its purpose is to create a common implementation to be used by iOS, Web, and Android.

## Capabilities

> [!TIP]
> The backup blob/file will be referred in this document as **backup artifact**, or simply 
> **artifact**.
> The clients (iOS, Web, and Android) will be referred as **callers**.

### Creation of backup artifact

The library should be able to transform data like messages, conversations, users into an artifact,
_i.e._ create a backup file.

### Restoration of original data

It should also be able to revert this process, _i.e._ read a backup artifact and understand all of
its contents, allowing the caller to get access to the original data.

### Optional Encryption

The artifact may _(or may not)_ be Encrypted. Clients using this library can provide an optional
passphrase to encrypt and decrypt the artifact.

### Optional UserID verification

When creating an artifact, callers need to provide a qualified user ID. This qualified user ID will
be stored within the artifact.
When restoring the original data, the library _can_ compare the user ID in the artifact with a user
ID provided by the caller. This may not be wanted in all features, _e.g._ in case of chat history
sharing across different users.

### Peak into artifact

Clients can ask the library if a piece of data is an actual cross-platform artifact.
The library should respond yes/no, and provide more details in case of a positive answer: is it
encrypted? Was it created by the same user that is restoring the artifact?

--------

# Development

Using Kotlin Multiplatform, it should at least be available for iOS, JS (with TypeScript types),
Android, and JVM.

### Building

Before we can automate the deployment of this library, we can manually generate and send library
artifacts (not backup artifacts) using the following Gradle tasks:

- iOS: `./gradlew :backup:assembleBackupDebugXCFramework`
- Web: `./gradlew :backup:jsBrowserDevelopmentLibraryDistribution`

**Output:** the results will be in `backup/build` directory. iOS needs the whole `backup.xcframework` directory, Web/JS needs the whole directory that contains `package.json`
