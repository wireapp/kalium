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

import KaliumNetwork

// MARK: - Errors

enum NetworkError: LocalizedError {
    case invalidURL
    case networkError(Error)
    case kaliumError(KaliumException)
    case decodingError(String)
    case httpError(statusCode: Int, message: String?, rawJson: String?)
    case missingData(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid URL"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .kaliumError(let exception):
            return "Kalium error: \(exception.description())"
        case .decodingError(let message):
            return "Decoding error: \(message)"
        case .httpError(let statusCode, let message, _):
            if let message = message {
                return "HTTP \(statusCode): \(message)"
            }
            return "HTTP error: \(statusCode)"
        case .missingData(let field):
            return "Missing data: \(field)"
        }
    }

    var rawJson: String? {
        switch self {
        case .httpError(_, _, let json):
            return json
        case .kaliumError(let exception):
            return exception.description()
        default:
            return nil
        }
    }
}

// MARK: - Login Result (combines session and user)

struct LoginResult {
    let session: Session
    let selfUser: SelfUser
    let rawSessionJson: String
    let rawSelfUserJson: String
}

// MARK: - Network Service using KaliumNetwork

class NetworkService {
    private var proxyCredentials: ProxyCredentials?

    init() {}

    /// Configure proxy credentials for subsequent requests
    func configureProxy(_ proxy: ServerConfig.ServerConfigApiProxy?, credentials: (username: String, password: String)?) {
        if let creds = credentials {
            self.proxyCredentials = ProxyCredentials(
                username: creds.username,
                password: creds.password
            )
        } else {
            self.proxyCredentials = nil
        }
    }

    // MARK: - Server Config API (using KaliumNetwork)

    /// Fetches server configuration from a JSON config URL using KaliumNetwork
    func fetchServerConfig(configUrl: String) async throws -> ServerConfig.ServerConfigLinks {
        let unboundContainer = UnboundNetworkContainerCommon(
            userAgent: "WireIOSExperiment/1.0",
            ignoreSSLCertificates: false,
            certificatePinning: [:],
            mockEngine: nil,
            developmentApiEnabled: true
        )

        let serverConfigApi = unboundContainer.serverConfigApi

        return try await withCheckedThrowingContinuation { continuation in
            serverConfigApi.fetchServerConfig(serverConfigUrl: configUrl) { response, error in
                if let error = error {
                    continuation.resume(throwing: NetworkError.networkError(error))
                    return
                }

                guard let response = response else {
                    continuation.resume(throwing: NetworkError.missingData("No response from server"))
                    return
                }

                if let successResponse = response as? NetworkResponseNetworkResponseSuccess<ServerConfig.ServerConfigLinks> {
                    continuation.resume(returning: successResponse.value)
                } else if let errorResponse = response as? NetworkResponseNetworkResponseError {
                    continuation.resume(throwing: NetworkError.kaliumError(errorResponse.kException))
                } else {
                    continuation.resume(throwing: NetworkError.decodingError("Unknown response type"))
                }
            }
        }
    }

    // MARK: - Login API (using KaliumNetwork)

    /// Performs login with email and password using KaliumNetwork
    func login(
        email: String,
        password: String,
        serverConfigLinks: ServerConfig.ServerConfigLinks
    ) async throws -> LoginResult {
        // First, fetch the API version to get metadata
        let metaData = try await fetchApiVersion(baseUrl: serverConfigLinks.api)

        // Create the full ServerConfig required for login
        let serverConfigDTO = ServerConfig(
            id: UUID().uuidString,
            links: serverConfigLinks,
            metadata: metaData
        )

        // Create the unauthenticated network container
        let container = UnauthenticatedNetworkContainerCompanion.shared.create(
            serverConfigDTO: serverConfigDTO,
            proxyCredentials: proxyCredentials,
            userAgent: "WireIOSExperiment/1.0",
            developmentApiEnabled: true,
            certificatePinning: [:],
            mockEngine: nil
        )

        // Create login parameters
        let loginParam = LoginParam.LoginWithEmail(
            email: email,
            password: password,
            label: "ios-experiment-app",
            verificationCode: nil
        )

        // Call login API
        return try await withCheckedThrowingContinuation { continuation in
            container.loginApi.login(param: loginParam, persist: true) { response, error in
                if let error = error {
                    continuation.resume(throwing: NetworkError.networkError(error))
                    return
                }

                guard let response = response else {
                    continuation.resume(throwing: NetworkError.missingData("No response from login"))
                    return
                }

                if let successResponse = response as? NetworkResponseNetworkResponseSuccess<KotlinPair<Session, SelfUser>> {
                    let pair = successResponse.value
                    guard let session = pair.first, let selfUser = pair.second else {
                        continuation.resume(throwing: NetworkError.missingData("Missing session or user data"))
                        return
                    }

                    let result = LoginResult(
                        session: session,
                        selfUser: selfUser,
                        rawSessionJson: self.formatSessionAsJson(session),
                        rawSelfUserJson: self.formatSelfUserAsJson(selfUser)
                    )
                    continuation.resume(returning: result)

                } else if let errorResponse = response as? NetworkResponseNetworkResponseError {
                    let exception = errorResponse.kException
                    let rawJson = exception.description()
                    continuation.resume(throwing: NetworkError.httpError(
                        statusCode: self.extractStatusCode(from: exception),
                        message: self.extractErrorLabel(from: exception),
                        rawJson: rawJson
                    ))
                } else {
                    continuation.resume(throwing: NetworkError.decodingError("Unknown response type"))
                }
            }
        }
    }

    // MARK: - Version API (using KaliumNetwork)

    private func fetchApiVersion(baseUrl: String) async throws -> ServerConfig.ServerConfigMetadata {
        let unboundContainer = UnboundNetworkContainerCommon(
            userAgent: "WireIOSExperiment/1.0",
            ignoreSSLCertificates: false,
            certificatePinning: [:],
            mockEngine: nil,
            developmentApiEnabled: true
        )

        let versionApiHelper = VersionApiHelper(unboundContainer: unboundContainer)

        return try await withCheckedThrowingContinuation { continuation in
            versionApiHelper.fetchApiVersion(baseUrl: baseUrl) { response, error in
                if let error = error {
                    continuation.resume(throwing: NetworkError.networkError(error))
                    return
                }

                guard let response = response else {
                    continuation.resume(throwing: NetworkError.missingData("No response from version API"))
                    return
                }

                if let successResponse = response as? NetworkResponseNetworkResponseSuccess<ServerConfig.ServerConfigMetadata> {
                    continuation.resume(returning: successResponse.value)
                } else if let errorResponse = response as? NetworkResponseNetworkResponseError {
                    continuation.resume(throwing: NetworkError.kaliumError(errorResponse.kException))
                } else {
                    continuation.resume(throwing: NetworkError.decodingError("Unknown response type"))
                }
            }
        }
    }

    // MARK: - Helper Methods

    private func formatSessionAsJson(_ session: Session) -> String {
        return """
        {
          "user_id": {
            "id": "\(session.userId.value)",
            "domain": "\(session.userId.domain)"
          },
          "token_type": "\(session.tokenType)",
          "access_token": "\(session.accessToken)",
          "refresh_token": "\(session.refreshToken)",
          "cookie_label": \(session.cookieLabel.map { "\"\($0)\"" } ?? "null")
        }
        """
    }

    private func formatSelfUserAsJson(_ user: SelfUser) -> String {
        return """
        {
          "id": "\(user.id.value)",
          "domain": "\(user.id.domain)",
          "name": "\(user.name)",
          "email": \(user.email.map { "\"\($0)\"" } ?? "null"),
          "handle": \(user.handle.map { "\"\($0)\"" } ?? "null"),
          "locale": "\(user.locale)",
          "accent_id": \(user.accentId)
        }
        """
    }

    private func extractStatusCode(from exception: KaliumException) -> Int {
        if let serverError = exception as? KaliumException.ServerError {
            return Int(serverError.errorResponse.code)
        }
        if let invalidRequest = exception as? KaliumException.InvalidRequestError {
            return Int(invalidRequest.errorResponse.code)
        }
        return 0
    }

    private func extractErrorLabel(from exception: KaliumException) -> String? {
        if let serverError = exception as? KaliumException.ServerError {
            return serverError.errorResponse.label
        }
        if let invalidRequest = exception as? KaliumException.InvalidRequestError {
            return invalidRequest.errorResponse.label
        }
        return nil
    }
}
