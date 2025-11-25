import XCTest
import WireCoreCrypto
import WireCoreCryptoUniffi

typealias CC = WireCoreCrypto.CoreCrypto

final class CoreCryptoTests: XCTestCase {

    var tempDir: URL!

    override func setUp() async throws {
        tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("corecrypto-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    }

    override func tearDown() async throws {
        try? FileManager.default.removeItem(at: tempDir)
    }

    func createCoreCrypto(clientId: String = "test-client") async throws -> CC {
        let keystorePath = tempDir.appendingPathComponent("keystore-\(clientId)").path
        let passphrase = "test-passphrase-12345678901234567890123456789012".data(using: .utf8)!
        let databaseKey = try DatabaseKey(key: passphrase)
        return try await CC(keystorePath: keystorePath, key: databaseKey)
    }

    // MARK: - Basic Tests

    func testCoreCryptoVersion() async throws {
        let version = CC.version()
        XCTAssertFalse(version.isEmpty, "Version should not be empty")
        print("CoreCrypto version: \(version)")
    }

    func testCoreCryptoCreation() async throws {
        let coreCrypto = try await createCoreCrypto()
        XCTAssertNotNil(coreCrypto)
    }

    func testTransaction() async throws {
        let coreCrypto = try await createCoreCrypto()
        let result = try await coreCrypto.transaction { context in
            return "success"
        }
        XCTAssertEqual(result, "success")
    }

    // MARK: - MLS Tests

    func testMlsInit() async throws {
        let coreCrypto = try await createCoreCrypto()
        let clientId = "test-client-id".data(using: .utf8)!

        try await coreCrypto.transaction { context in
            let clientIdObj = ClientId(bytes: clientId)
            let ciphersuites: [Ciphersuite] = [.mls128Dhkemx25519Aes128gcmSha256Ed25519]
            try await context.mlsInit(clientId: clientIdObj, ciphersuites: ciphersuites, nbKeyPackage: nil)
        }
    }

    func testClientPublicKey() async throws {
        let coreCrypto = try await createCoreCrypto()
        let clientId = "test-client-id".data(using: .utf8)!

        try await coreCrypto.transaction { context in
            let clientIdObj = ClientId(bytes: clientId)
            let ciphersuites: [Ciphersuite] = [.mls128Dhkemx25519Aes128gcmSha256Ed25519]
            try await context.mlsInit(clientId: clientIdObj, ciphersuites: ciphersuites, nbKeyPackage: nil)
        }

        let publicKey = try await coreCrypto.transaction { context in
            try await context.clientPublicKey(
                ciphersuite: .mls128Dhkemx25519Aes128gcmSha256Ed25519,
                credentialType: .basic
            )
        }

        XCTAssertFalse(publicKey.isEmpty, "Public key should not be empty")
    }

    func testGenerateKeyPackages() async throws {
        let coreCrypto = try await createCoreCrypto()
        let clientId = "test-client-id".data(using: .utf8)!

        try await coreCrypto.transaction { context in
            let clientIdObj = ClientId(bytes: clientId)
            let ciphersuites: [Ciphersuite] = [.mls128Dhkemx25519Aes128gcmSha256Ed25519]
            try await context.mlsInit(clientId: clientIdObj, ciphersuites: ciphersuites, nbKeyPackage: nil)
        }

        let keyPackages = try await coreCrypto.transaction { context in
            try await context.clientKeypackages(
                ciphersuite: .mls128Dhkemx25519Aes128gcmSha256Ed25519,
                credentialType: .basic,
                amountRequested: 5
            )
        }

        XCTAssertEqual(keyPackages.count, 5, "Should generate 5 key packages")
    }

    func testCreateConversation() async throws {
        let coreCrypto = try await createCoreCrypto()
        let clientId = "test-client-id".data(using: .utf8)!
        let conversationId = "test-conversation".data(using: .utf8)!

        try await coreCrypto.transaction { context in
            let clientIdObj = ClientId(bytes: clientId)
            let ciphersuites: [Ciphersuite] = [.mls128Dhkemx25519Aes128gcmSha256Ed25519]
            try await context.mlsInit(clientId: clientIdObj, ciphersuites: ciphersuites, nbKeyPackage: nil)
        }

        try await coreCrypto.transaction { context in
            let convId = ConversationId(bytes: conversationId)
            let config = ConversationConfiguration(
                ciphersuite: .mls128Dhkemx25519Aes128gcmSha256Ed25519,
                externalSenders: [],
                custom: CustomConfiguration(keyRotationSpan: nil, wirePolicy: nil)
            )
            try await context.createConversation(
                conversationId: convId,
                creatorCredentialType: .basic,
                config: config
            )
        }

        let exists = try await coreCrypto.transaction { context in
            let convId = ConversationId(bytes: conversationId)
            return try await context.conversationExists(conversationId: convId)
        }

        XCTAssertTrue(exists, "Conversation should exist")
    }

    func testConversationEpoch() async throws {
        let coreCrypto = try await createCoreCrypto()
        let clientId = "test-client-id".data(using: .utf8)!
        let conversationId = "test-conversation".data(using: .utf8)!

        try await coreCrypto.transaction { context in
            let clientIdObj = ClientId(bytes: clientId)
            let ciphersuites: [Ciphersuite] = [.mls128Dhkemx25519Aes128gcmSha256Ed25519]
            try await context.mlsInit(clientId: clientIdObj, ciphersuites: ciphersuites, nbKeyPackage: nil)
        }

        try await coreCrypto.transaction { context in
            let convId = ConversationId(bytes: conversationId)
            let config = ConversationConfiguration(
                ciphersuite: .mls128Dhkemx25519Aes128gcmSha256Ed25519,
                externalSenders: [],
                custom: CustomConfiguration(keyRotationSpan: nil, wirePolicy: nil)
            )
            try await context.createConversation(
                conversationId: convId,
                creatorCredentialType: .basic,
                config: config
            )
        }

        let epoch = try await coreCrypto.transaction { context in
            let convId = ConversationId(bytes: conversationId)
            return try await context.conversationEpoch(conversationId: convId)
        }

        XCTAssertEqual(epoch, 0, "New conversation should have epoch 0")
    }

    // MARK: - Proteus Tests

    func testProteusInit() async throws {
        let coreCrypto = try await createCoreCrypto()

        try await coreCrypto.transaction { context in
            try await context.proteusInit()
        }
    }

    func testProteusFingerprint() async throws {
        let coreCrypto = try await createCoreCrypto()

        try await coreCrypto.transaction { context in
            try await context.proteusInit()
        }

        let fingerprint = try await coreCrypto.transaction { context in
            try await context.proteusFingerprint()
        }

        XCTAssertFalse(fingerprint.isEmpty, "Fingerprint should not be empty")
    }

    func testProteusNewPrekey() async throws {
        let coreCrypto = try await createCoreCrypto()

        try await coreCrypto.transaction { context in
            try await context.proteusInit()
        }

        let prekey = try await coreCrypto.transaction { context in
            try await context.proteusNewPrekey(prekeyId: 1)
        }

        XCTAssertFalse(prekey.isEmpty, "Prekey should not be empty")
    }

    func testProteusLastResortPrekey() async throws {
        let coreCrypto = try await createCoreCrypto()

        try await coreCrypto.transaction { context in
            try await context.proteusInit()
        }

        let lastResortPrekey = try await coreCrypto.transaction { context in
            try await context.proteusLastResortPrekey()
        }

        XCTAssertFalse(lastResortPrekey.isEmpty, "Last resort prekey should not be empty")
    }

    // MARK: - Two Client Tests

    func testTwoClientsEncryptDecrypt() async throws {
        // Create two CoreCrypto instances
        let alice = try await createCoreCrypto(clientId: "alice")
        let bob = try await createCoreCrypto(clientId: "bob")

        // Initialize Proteus for both
        try await alice.transaction { context in
            try await context.proteusInit()
        }
        try await bob.transaction { context in
            try await context.proteusInit()
        }

        // Get Bob's prekey
        let bobPrekey = try await bob.transaction { context in
            try await context.proteusNewPrekey(prekeyId: 1)
        }

        // Alice creates session with Bob using his prekey
        let sessionId = "alice-to-bob"
        try await alice.transaction { context in
            try await context.proteusSessionFromPrekey(sessionId: sessionId, prekey: bobPrekey)
        }

        // Alice encrypts a message
        let plaintext = "Hello Bob!".data(using: .utf8)!
        let ciphertext = try await alice.transaction { context in
            try await context.proteusEncrypt(sessionId: sessionId, plaintext: plaintext)
        }

        // Bob decrypts the message (this also creates the session on Bob's side)
        let decrypted = try await bob.transaction { context in
            try await context.proteusSessionFromMessage(sessionId: "bob-to-alice", envelope: ciphertext)
        }

        XCTAssertEqual(decrypted, plaintext, "Decrypted message should match original")
    }
}
