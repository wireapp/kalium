# Module logic
Higher level business logic operations, tying up single operations from other modules.

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

# Sample Apps:
Basic apps showcasing how to use Kalium

## Module app
📱 Android

## Module cli
🖥️ JVM terminal app
