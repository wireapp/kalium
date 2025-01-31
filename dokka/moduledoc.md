# Module logic
Higher level business logic operations, tying up single operations from other modules.

# Module backup
Multiplatform/Crossplatform backup implementation, meant to be used by all clients. 
Entrypoint: `MPBackupImporter` and `MPBackupExporter` classes.

# Module calling
Wraps [wire-avs](https://github.com/wireapp/wire-avs) for each platform, providing calling capabilities.

# Module cryptography
Interface to Proteus and MLS, handles all cryptographic operations. Uses [core-crypto](https://github.com/wireapp/core-crypto/).

# Module network
Maps the [wire-server](https://github.com/wireapp/wire-server) api and handles networking communications.

# Module persistence
Handles storing and retrieving data, interfacing with one more persistence mechanisms.

# Module protobuf
Provides multiplatform (de)serialization of [OTR messages and envelopes](https://github.com/wireapp/generic-message-proto).

# Module logger
Common Logging implementation used by other modules.

# Module app
Sample Android app showcasing Kalium usage

# Module cli
Sample JVM command-line-interface app showcasing Kalium usage
