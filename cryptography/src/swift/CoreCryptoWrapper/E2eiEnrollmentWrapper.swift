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

/// Wrapper for E2EI Enrollment exposing Obj-C compatible APIs for Kotlin/Native interop
@objc public class E2eiEnrollmentWrapper: NSObject {

    internal let enrollment: WireCoreCryptoUniffi.E2eiEnrollment

    internal init(_ enrollment: WireCoreCryptoUniffi.E2eiEnrollment) {
        self.enrollment = enrollment
        super.init()
    }

    /// Process ACME directory response
    @objc public func directoryResponse(
        _ directory: Data,
        completion: @escaping (AcmeDirectoryWrapper?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.directoryResponse(directory: directory)
                completion(AcmeDirectoryWrapper(result), nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Create new account request
    @objc public func newAccountRequest(
        _ previousNonce: String,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.newAccountRequest(previousNonce: previousNonce)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Process account response
    @objc public func newAccountResponse(
        _ account: Data,
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await enrollment.newAccountResponse(account: account)
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Create new order request
    @objc public func newOrderRequest(
        _ previousNonce: String,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.newOrderRequest(previousNonce: previousNonce)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Process order response
    @objc public func newOrderResponse(
        _ order: Data,
        completion: @escaping (NewAcmeOrderWrapper?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.newOrderResponse(order: order)
                completion(NewAcmeOrderWrapper(result), nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Create new authorization request
    @objc public func newAuthzRequest(
        url: String,
        previousNonce: String,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.newAuthzRequest(url: url, previousNonce: previousNonce)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Process authorization response
    @objc public func newAuthzResponse(
        _ authz: Data,
        completion: @escaping (NewAcmeAuthzWrapper?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.newAuthzResponse(authz: authz)
                completion(NewAcmeAuthzWrapper(result), nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Create DPoP token
    @objc public func createDpopToken(
        expirySecs: UInt32,
        backendNonce: String,
        completion: @escaping (String?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.createDpopToken(expirySecs: expirySecs, backendNonce: backendNonce)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Create new DPoP challenge request
    @objc public func newDpopChallengeRequest(
        accessToken: String,
        previousNonce: String,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.newDpopChallengeRequest(accessToken: accessToken, previousNonce: previousNonce)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Process DPoP challenge response
    @objc public func newDpopChallengeResponse(
        _ challenge: Data,
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await enrollment.newDpopChallengeResponse(challenge: challenge)
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Create new OIDC challenge request
    @objc public func newOidcChallengeRequest(
        idToken: String,
        previousNonce: String,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.newOidcChallengeRequest(idToken: idToken, previousNonce: previousNonce)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Process OIDC challenge response
    @objc public func newOidcChallengeResponse(
        _ challenge: Data,
        completion: @escaping (NSError?) -> Void
    ) {
        Task {
            do {
                try await enrollment.newOidcChallengeResponse(challenge: challenge)
                completion(nil)
            } catch {
                completion(error as NSError)
            }
        }
    }

    /// Create check order request
    @objc public func checkOrderRequest(
        orderUrl: String,
        previousNonce: String,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.checkOrderRequest(orderUrl: orderUrl, previousNonce: previousNonce)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Process check order response
    @objc public func checkOrderResponse(
        _ order: Data,
        completion: @escaping (String?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.checkOrderResponse(order: order)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Create finalize request
    @objc public func finalizeRequest(
        _ previousNonce: String,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.finalizeRequest(previousNonce: previousNonce)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Process finalize response
    @objc public func finalizeResponse(
        _ finalize: Data,
        completion: @escaping (String?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.finalizeResponse(finalize: finalize)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }

    /// Create certificate request
    @objc public func certificateRequest(
        _ previousNonce: String,
        completion: @escaping (Data?, NSError?) -> Void
    ) {
        Task {
            do {
                let result = try await enrollment.certificateRequest(previousNonce: previousNonce)
                completion(result, nil)
            } catch {
                completion(nil, error as NSError)
            }
        }
    }
}
