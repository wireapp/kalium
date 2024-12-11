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

**Output:** the results will be in `backup/build` directory. iOS needs the whole
`backup.xcframework` directory, Web/JS needs the whole directory that contains `package.json`

--------

# The artifact format

The table below represents the format of the backup file.
The first 1024 bytes are reserved for the backup header. Lots of blank space is left for future
proofing in case we want to add more optional fields.
The following 24 bytes are reserved for
the [xChaCha20Poly1305 encryption header](https://libsodium.gitbook.io/doc/secret-key_cryptography/secretstream#usage).
If the archive is not encrypted, this will be filled with 0x00.
The remaining of the file stores the actual backed up data, be it encrypted or not.
Big endian is used.

| Index             | Name             | Length           | Value                              | Description                                                                                                                                                                                                                         |
|-------------------|------------------|------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| (Start of Header) |                  |                  |                                    |                                                                                                                                                                                                                                     |
| 0                 | fileMagicNumber  | 4                | 0x57 0x42 0x55 0x58                | [Magic number](https://en.wikipedia.org/wiki/File_format#Magic_number) to distinguish our file format. Last X letter denotes this backup is supported across different platforms, as opposed to previous Backup versions. X = Cross |
| 4                 |                  | 1                | 0x00                               | Empty bit. Non-readable value so that the file is not identified as a text-file by most software.                                                                                                                                   |
| 5                 | formatVersion    | 2                | Unsigned Short                     | Version of the file format. For example: `0x00 0x04` for version 4. Should be bumped when there are breaking changes in the format                                                                                                  |
| 7                 | hashSalt         | 16               | Blob of bytes                      | Salt for argon2 key derivation. Used for hashing UserID (author of this file) and to spice the user-created password (if the user chooses to encrypt the archive)                                                                   |
| 23                | hashedUserId     | 32               | Blob of bytes                      | The hashed ID of the user that authored this file.                                                                                                                                                                                  |
| 55                | hashOpsLimit     | 4                | Unsigned Integer                   | [opsLimit](https://libsodium.gitbook.io/doc/password_hashing/default_phf#key-derivation) for hashing                                                                                                                                |
| 59                | hashMemLimit     | 4                | Unsigned Integer                   | [memLimit](https://libsodium.gitbook.io/doc/password_hashing/default_phf#key-derivation) for hashing                                                                                                                                |
| 63                | isEncrypted      | 1                | Boolean                            | Is the file encrypted? `0x00 = false`, anything else is true. If not encrypted, the xChaCha20 header can be ignored and the archive can be read straight away without any decryption or asking the user for a password.             |
| 64                |                  | 960              | Empty (0x00) bytes. Reserved space | For future proofing. If we choose to add more metadata to the file and that shouldn't break backwards compatibility, we can add here. Otherwise we need to bump the `formatVersion` field                                           |
| (End of header)   |                  |                  |                                    |                                                                                                                                                                                                                                     |
| 1024              | encryptionHeader | 24               | Blob of bytes                      | [xChaCha20Poly1305 encryption header](https://libsodium.gitbook.io/doc/secret-key_cryptography/secretstream#usage), used by libsodium to decrypt the rest of the file. Should be filled with zeroed-bytes if `isEncrypted` is false |
| 1048              | backedUpData     | Rest of the file | The actual meat                    | The backed up data, be it encrypted or not. If encrypted, should be decrypted before attempting to read it.                                                                                                                         |

