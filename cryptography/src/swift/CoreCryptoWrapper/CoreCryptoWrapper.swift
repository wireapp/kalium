/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

import Foundation
import WireCoreCrypto
import WireCoreCryptoUniffi

/// Main CoreCrypto wrapper exposing Obj-C compatible APIs for Kotlin/Native interop
@objc public class CoreCryptoWrapper: NSObject {

    private let coreCrypto: WireCoreCrypto.CoreCrypto

    private init(coreCrypto: WireCoreCrypto.CoreCrypto) {
        self.coreCrypto = coreCrypto
        super.init()
    }

    /// Creates a new CoreCrypto instance
    @objc public static func create(
        keystorePath: String,
        passphrase: Data,
        completion: @escaping (CoreCryptoWrapper?, NSError?) -> Void
    ) {
        Task {
            do {
                let databaseKey = try WireCoreCryptoUniffi.DatabaseKey(key: passphrase)
                let cc = try await WireCoreCrypto.CoreCrypto(keystorePath: keystorePath, key: databaseKey)
                completion(CoreCryptoWrapper(coreCrypto: cc), nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Returns the CoreCrypto version string
    @objc public static func coreCryptoVersion() -> String {
        return WireCoreCrypto.CoreCrypto.version()
    }

    /// Sets the logger for CoreCrypto
    @objc public static func setLogger(_ logger: CoreCryptoLoggerWrapper, level: Int32) {
        WireCoreCrypto.CoreCrypto.setLogger(logger)
        let logLevel: WireCoreCryptoUniffi.CoreCryptoLogLevel
        switch level {
        case 0: logLevel = .off
        case 1: logLevel = .trace
        case 2: logLevel = .debug
        case 3: logLevel = .info
        case 4: logLevel = .warn
        case 5: logLevel = .error
        default: logLevel = .warn
        }
        WireCoreCrypto.CoreCrypto.setMaxLogLevel(logLevel)
    }

    // MARK: - MLS Operations

    /// Initialize MLS client
    @objc public func mlsInit(
        clientId: Data,
        ciphersuites: [UInt16],
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await coreCrypto.transaction { context in
                    let clientIdObj = WireCoreCryptoUniffi.ClientId(bytes: clientId)
                    let ciphersuitesConverted = ciphersuites.compactMap { WireCoreCryptoUniffi.Ciphersuite(rawValue: $0) }
                    try await context.mlsInit(clientId: clientIdObj, ciphersuites: ciphersuitesConverted, nbKeyPackage: nil)
                }
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Get client public key
    @objc public func clientPublicKey(
        ciphersuite: UInt16,
        credentialType: Int32,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let cs = WireCoreCryptoUniffi.Ciphersuite(rawValue: ciphersuite)!
                    let ct = WireCoreCryptoUniffi.CredentialType(rawValue: UInt8(credentialType))!
                    return try await context.clientPublicKey(ciphersuite: cs, credentialType: ct)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Generate key packages
    @objc public func clientKeypackages(
        ciphersuite: UInt16,
        credentialType: Int32,
        amountRequested: UInt32,
        completion: @escaping ([Data]?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let cs = WireCoreCryptoUniffi.Ciphersuite(rawValue: ciphersuite)!
                    let ct = WireCoreCryptoUniffi.CredentialType(rawValue: UInt8(credentialType))!
                    let keyPackages = try await context.clientKeypackages(ciphersuite: cs, credentialType: ct, amountRequested: amountRequested)
                    return keyPackages.map { $0.copyBytes() }
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Get valid key packages count
    @objc public func clientValidKeypackagesCount(
        ciphersuite: UInt16,
        credentialType: Int32,
        completion: @escaping (UInt64, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let cs = WireCoreCryptoUniffi.Ciphersuite(rawValue: ciphersuite)!
                    let ct = WireCoreCryptoUniffi.CredentialType(rawValue: UInt8(credentialType))!
                    return try await context.clientValidKeypackagesCount(ciphersuite: cs, credentialType: ct)
                }
                completion(result, nil)
            } catch {
                completion(0, error as NSError)
            }
        }
    }

    // MARK: - Conversation Operations

    /// Create a new conversation
    @objc public func createConversation(
        conversationId: Data,
        credentialType: Int32,
        ciphersuite: UInt16,
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    let ct = WireCoreCryptoUniffi.CredentialType(rawValue: UInt8(credentialType))!
                    let config = WireCoreCryptoUniffi.ConversationConfiguration(
                        ciphersuite: WireCoreCryptoUniffi.Ciphersuite(rawValue: ciphersuite)!,
                        externalSenders: [],
                        custom: WireCoreCryptoUniffi.CustomConfiguration(keyRotationSpan: nil, wirePolicy: nil)
                    )
                    try await context.createConversation(conversationId: convId, creatorCredentialType: ct, config: config)
                }
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Check if conversation exists
    @objc public func conversationExists(
        conversationId: Data,
        completion: @escaping (Bool, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    return try await context.conversationExists(conversationId: convId)
                }
                completion(result, nil)
            } catch {
                completion(false, error as NSError)
            }
        }
    }

    /// Get conversation epoch
    @objc public func conversationEpoch(
        conversationId: Data,
        completion: @escaping (UInt64, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    return try await context.conversationEpoch(conversationId: convId)
                }
                completion(result, nil)
            } catch {
                completion(0, error as NSError)
            }
        }
    }

    /// Encrypt a message
    @objc public func encryptMessage(
        conversationId: Data,
        message: Data,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    return try await context.encryptMessage(conversationId: convId, message: message)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Decrypt a message
    @objc public func decryptMessage(
        conversationId: Data,
        payload: Data,
        completion: @escaping (DecryptedMessageWrapper?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    return try await context.decryptMessage(conversationId: convId, payload: payload)
                }
                completion(DecryptedMessageWrapper(result), nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Process welcome message
    @objc public func processWelcomeMessage(
        welcomeMessage: Data,
        completion: @escaping (WelcomeBundleWrapper?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let welcome = WireCoreCryptoUniffi.Welcome(bytes: welcomeMessage)
                    let config = WireCoreCryptoUniffi.CustomConfiguration(keyRotationSpan: nil, wirePolicy: nil)
                    return try await context.processWelcomeMessage(welcomeMessage: welcome, customConfiguration: config)
                }
                completion(WelcomeBundleWrapper(result), nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Add clients to conversation
    @objc public func addClientsToConversation(
        conversationId: Data,
        keyPackages: [Data],
        completion: @escaping ([String]?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    let kps = keyPackages.map { WireCoreCryptoUniffi.KeyPackage(bytes: $0) }
                    return try await context.addClientsToConversation(conversationId: convId, keyPackages: kps)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Remove clients from conversation
    @objc public func removeClientsFromConversation(
        conversationId: Data,
        clientIds: [Data],
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    let clients = clientIds.map { WireCoreCryptoUniffi.ClientId(bytes: $0) }
                    try await context.removeClientsFromConversation(conversationId: convId, clients: clients)
                }
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Wipe conversation
    @objc public func wipeConversation(
        conversationId: Data,
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    try await context.wipeConversation(conversationId: convId)
                }
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Update keying material
    @objc public func updateKeyingMaterial(
        conversationId: Data,
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    try await context.updateKeyingMaterial(conversationId: convId)
                }
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Commit pending proposals
    @objc public func commitPendingProposals(
        conversationId: Data,
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    try await context.commitPendingProposals(conversationId: convId)
                }
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Join by external commit
    @objc public func joinByExternalCommit(
        groupInfo: Data,
        credentialType: Int32,
        completion: @escaping (WelcomeBundleWrapper?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let gi = WireCoreCryptoUniffi.GroupInfo(bytes: groupInfo)
                    let ct = WireCoreCryptoUniffi.CredentialType(rawValue: UInt8(credentialType))!
                    let config = WireCoreCryptoUniffi.CustomConfiguration(keyRotationSpan: nil, wirePolicy: nil)
                    return try await context.joinByExternalCommit(groupInfo: gi, customConfiguration: config, credentialType: ct)
                }
                completion(WelcomeBundleWrapper(result), nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Get external sender key
    @objc public func getExternalSender(
        conversationId: Data,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    return try await context.getExternalSender(conversationId: convId).copyBytes()
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Export secret key
    @objc public func exportSecretKey(
        conversationId: Data,
        keyLength: UInt32,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    return try await context.exportSecretKey(conversationId: convId, keyLength: keyLength).copyBytes()
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Get client IDs in conversation
    @objc public func getClientIds(
        conversationId: Data,
        completion: @escaping ([Data]?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let convId = WireCoreCryptoUniffi.ConversationId(bytes: conversationId)
                    let clientIds = try await context.getClientIds(conversationId: convId)
                    return clientIds.map { $0.copyBytes() }
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    // MARK: - E2EI Operations

    /// Create new E2EI enrollment
    @objc public func e2eiNewEnrollment(
        clientId: String,
        displayName: String,
        handle: String,
        team: String?,
        expirySec: UInt32,
        ciphersuite: UInt16,
        completion: @escaping (E2eiEnrollmentWrapper?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let cs = WireCoreCryptoUniffi.Ciphersuite(rawValue: ciphersuite)!
                    return try await context.e2eiNewEnrollment(
                        clientId: clientId,
                        displayName: displayName,
                        handle: handle,
                        team: team,
                        expirySec: expirySec,
                        ciphersuite: cs
                    )
                }
                completion(E2eiEnrollmentWrapper(result), nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Initialize MLS with E2EI only
    @objc public func e2eiMlsInitOnly(
        enrollment: E2eiEnrollmentWrapper,
        certificateChain: String,
        nbKeyPackage: Int32,
        completion: @escaping ([String]?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let nbKp: UInt32? = nbKeyPackage > 0 ? UInt32(nbKeyPackage) : nil
                    return try await context.e2eiMlsInitOnly(
                        enrollment: enrollment.enrollment,
                        certificateChain: certificateChain,
                        nbKeyPackage: nbKp
                    )
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Check if E2EI is enabled
    @objc public func e2eiIsEnabled(
        ciphersuite: UInt16,
        completion: @escaping (Bool, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    let cs = WireCoreCryptoUniffi.Ciphersuite(rawValue: ciphersuite)!
                    return try await context.e2eiIsEnabled(ciphersuite: cs)
                }
                completion(result, nil)
            } catch {
                completion(false, error as NSError)
            }
        }
    }

    /// Register ACME CA
    @objc public func e2eiRegisterAcmeCa(
        trustAnchorPem: String,
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await coreCrypto.transaction { context in
                    try await context.e2eiRegisterAcmeCa(trustAnchorPem: trustAnchorPem)
                }
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Register CRL
    @objc public func e2eiRegisterCrl(
        crlDp: String,
        crlDer: Data,
        completion: @escaping (CrlRegistrationWrapper?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.e2eiRegisterCrl(crlDp: crlDp, crlDer: crlDer)
                }
                completion(CrlRegistrationWrapper(result), nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Register intermediate CA
    @objc public func e2eiRegisterIntermediateCa(
        certPem: String,
        completion: @escaping ([String]?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.e2eiRegisterIntermediateCa(certPem: certPem)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    // MARK: - Proteus Operations

    /// Initialize Proteus
    @objc public func proteusInit(completion: @escaping (NSError?) -> Void) {
        Task {
            do {
                try await coreCrypto.transaction { context in
                    try await context.proteusInit()
                }
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Create new Proteus prekey
    @objc public func proteusNewPrekey(
        prekeyId: UInt16,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusNewPrekey(prekeyId: prekeyId)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Create new Proteus prekey with auto ID
    @objc public func proteusNewPrekeyAuto(
        completion: @escaping (ProteusAutoPrekeyBundleWrapper?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusNewPrekeyAuto()
                }
                completion(ProteusAutoPrekeyBundleWrapper(result), nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Get last resort prekey
    @objc public func proteusLastResortPrekey(
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusLastResortPrekey()
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Get last resort prekey ID
    @objc public func proteusLastResortPrekeyId(
        completion: @escaping (UInt16, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try context.proteusLastResortPrekeyId()
                }
                completion(result, nil)
            } catch {
                completion(65535, error as NSError)
            }
        }
    }

    /// Get Proteus fingerprint
    @objc public func proteusFingerprint(
        completion: @escaping (String?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusFingerprint()
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Get local fingerprint for session
    @objc public func proteusFingerprintLocal(
        sessionId: String,
        completion: @escaping (String?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusFingerprintLocal(sessionId: sessionId)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Get remote fingerprint for session
    @objc public func proteusFingerprintRemote(
        sessionId: String,
        completion: @escaping (String?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusFingerprintRemote(sessionId: sessionId)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Check if Proteus session exists
    @objc public func proteusSessionExists(
        sessionId: String,
        completion: @escaping (Bool, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusSessionExists(sessionId: sessionId)
                }
                completion(result, nil)
            } catch {
                completion(false, error as NSError)
            }
        }
    }

    /// Create Proteus session from prekey
    @objc public func proteusSessionFromPrekey(
        sessionId: String,
        prekey: Data,
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await coreCrypto.transaction { context in
                    try await context.proteusSessionFromPrekey(sessionId: sessionId, prekey: prekey)
                }
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Create Proteus session from message
    @objc public func proteusSessionFromMessage(
        sessionId: String,
        envelope: Data,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusSessionFromMessage(sessionId: sessionId, envelope: envelope)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Encrypt with Proteus
    @objc public func proteusEncrypt(
        sessionId: String,
        plaintext: Data,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusEncrypt(sessionId: sessionId, plaintext: plaintext)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Encrypt batched with Proteus
    @objc public func proteusEncryptBatched(
        sessions: [String],
        plaintext: Data,
        completion: @escaping ([String: Data]?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusEncryptBatched(sessions: sessions, plaintext: plaintext)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Decrypt with Proteus
    @objc public func proteusDecrypt(
        sessionId: String,
        ciphertext: Data,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.proteusDecrypt(sessionId: sessionId, ciphertext: ciphertext)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Delete Proteus session
    @objc public func proteusSessionDelete(
        sessionId: String,
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await coreCrypto.transaction { context in
                    try await context.proteusSessionDelete(sessionId: sessionId)
                }
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    // MARK: - Utility

    /// Generate random bytes
    @objc public func randomBytes(
        length: UInt32,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await coreCrypto.transaction { context in
                    return try await context.randomBytes(len: length)
                }
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }
}
