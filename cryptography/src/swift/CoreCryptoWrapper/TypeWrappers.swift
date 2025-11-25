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
import WireCoreCryptoUniffi

// MARK: - ACME Types

/// Wrapper for AcmeDirectory
@objc public class AcmeDirectoryWrapper: NSObject {
    @objc public let newNonce: String
    @objc public let newAccount: String
    @objc public let newOrder: String

    internal init(_ directory: WireCoreCryptoUniffi.AcmeDirectory) {
        self.newNonce = directory.newNonce
        self.newAccount = directory.newAccount
        self.newOrder = directory.newOrder
        super.init()
    }
}

/// Wrapper for NewAcmeOrder
@objc public class NewAcmeOrderWrapper: NSObject {
    @objc public let delegate: Data
    @objc public let authorizations: [String]

    internal init(_ order: WireCoreCryptoUniffi.NewAcmeOrder) {
        self.delegate = order.delegate
        self.authorizations = order.authorizations
        super.init()
    }
}

/// Wrapper for NewAcmeAuthz
@objc public class NewAcmeAuthzWrapper: NSObject {
    @objc public let identifier: String
    @objc public let keyauth: String?
    @objc public let challenge: AcmeChallengeWrapper

    internal init(_ authz: WireCoreCryptoUniffi.NewAcmeAuthz) {
        self.identifier = authz.identifier
        self.keyauth = authz.keyauth
        self.challenge = AcmeChallengeWrapper(authz.challenge)
        super.init()
    }
}

/// Wrapper for AcmeChallenge
@objc public class AcmeChallengeWrapper: NSObject {
    @objc public let delegate: Data
    @objc public let url: String
    @objc public let target: String

    internal init(_ challenge: WireCoreCryptoUniffi.AcmeChallenge) {
        self.delegate = challenge.delegate
        self.url = challenge.url
        self.target = challenge.target
        super.init()
    }
}

// MARK: - MLS Types

/// Wrapper for DecryptedMessage
@objc public class DecryptedMessageWrapper: NSObject {
    @objc public let message: Data?
    @objc public let isActive: Bool
    @objc public let commitDelay: UInt64
    @objc public let senderClientId: Data?
    @objc public let hasEpochChanged: Bool
    @objc public let identity: WireIdentityWrapper?
    @objc public let crlNewDistributionPoints: [String]?

    internal init(_ msg: WireCoreCryptoUniffi.DecryptedMessage) {
        self.message = msg.message
        self.isActive = msg.isActive
        self.commitDelay = msg.commitDelay ?? 0
        self.senderClientId = msg.senderClientId?.copyBytes()
        self.hasEpochChanged = msg.hasEpochChanged
        self.identity = WireIdentityWrapper(msg.identity)
        self.crlNewDistributionPoints = msg.crlNewDistributionPoints
        super.init()
    }
}

/// Wrapper for WelcomeBundle
@objc public class WelcomeBundleWrapper: NSObject {
    @objc public let id: Data
    @objc public let crlNewDistributionPoints: [String]?

    internal init(_ bundle: WireCoreCryptoUniffi.WelcomeBundle) {
        self.id = bundle.id.copyBytes()
        self.crlNewDistributionPoints = bundle.crlNewDistributionPoints
        super.init()
    }
}

/// Wrapper for WireIdentity
@objc public class WireIdentityWrapper: NSObject {
    @objc public let clientId: String
    @objc public let status: Int32
    @objc public let thumbprint: String
    @objc public let credentialType: Int32
    // X509 identity fields (optional)
    @objc public let handle: String?
    @objc public let displayName: String?
    @objc public let domain: String?
    @objc public let certificate: String?
    @objc public let serialNumber: String?
    @objc public let notBefore: Double
    @objc public let notAfter: Double

    internal init(_ identity: WireCoreCryptoUniffi.WireIdentity) {
        self.clientId = identity.clientId
        self.status = Int32(identity.status.rawValue)
        self.thumbprint = identity.thumbprint
        self.credentialType = Int32(identity.credentialType.rawValue)
        if let x509 = identity.x509Identity {
            self.handle = x509.handle
            self.displayName = x509.displayName
            self.domain = x509.domain
            self.certificate = x509.certificate
            self.serialNumber = x509.serialNumber
            self.notBefore = x509.notBefore.timeIntervalSince1970
            self.notAfter = x509.notAfter.timeIntervalSince1970
        } else {
            self.handle = nil
            self.displayName = nil
            self.domain = nil
            self.certificate = nil
            self.serialNumber = nil
            self.notBefore = 0
            self.notAfter = 0
        }
        super.init()
    }
}

// MARK: - Proteus Types

/// Wrapper for ProteusAutoPrekeyBundle
@objc public class ProteusAutoPrekeyBundleWrapper: NSObject {
    @objc public let id: UInt16
    @objc public let pkb: Data

    internal init(_ bundle: WireCoreCryptoUniffi.ProteusAutoPrekeyBundle) {
        self.id = bundle.id
        self.pkb = bundle.pkb
        super.init()
    }
}

// MARK: - CRL Types

/// Wrapper for CrlRegistration
@objc public class CrlRegistrationWrapper: NSObject {
    @objc public let dirty: Bool
    @objc public let expiration: UInt64

    internal init(_ registration: WireCoreCryptoUniffi.CrlRegistration) {
        self.dirty = registration.dirty
        self.expiration = registration.expiration ?? 0
        super.init()
    }
}

// MARK: - Logger

/// Logger wrapper for CoreCrypto
@objc public final class CoreCryptoLoggerWrapper: NSObject, WireCoreCryptoUniffi.CoreCryptoLogger, @unchecked Sendable {

    public typealias LogCallback = @Sendable (Int32, String, String?) -> Void

    private let callback: LogCallback

    @objc public init(callback: @escaping LogCallback) {
        self.callback = callback
        super.init()
    }

    public func log(level: WireCoreCryptoUniffi.CoreCryptoLogLevel, message: String, context: String?) throws {
        let levelInt: Int32
        switch level {
        case .off: levelInt = 0
        case .trace: levelInt = 1
        case .debug: levelInt = 2
        case .info: levelInt = 3
        case .warn: levelInt = 4
        case .error: levelInt = 5
        }
        callback(levelInt, message, context)
    }
}
