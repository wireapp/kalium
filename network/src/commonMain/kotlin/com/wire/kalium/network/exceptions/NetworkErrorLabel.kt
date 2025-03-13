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

package com.wire.kalium.network.exceptions

internal object NetworkErrorLabel {
    const val TOO_MANY_CLIENTS = "too-many-clients"
    const val INVALID_CREDENTIALS = "invalid-credentials"
    const val INVALID_EMAIL = "invalid-email"
    const val BAD_REQUEST = "bad-request"
    const val MISSING_AUTH = "missing-auth"
    const val DOMAIN_BLOCKED_FOR_REGISTRATION = "domain-blocked-for-registration"
    const val KEY_EXISTS = "key-exists"
    const val BLACKLISTED_EMAIL = "blacklisted-email"
    const val INVALID_CODE = "invalid-code"
    const val USER_CREATION_RESTRICTED = "user-creation-restricted"
    const val TOO_MANY_MEMBERS = "too-many-team-members"
    const val INVALID_HANDLE = "invalid-handle"
    const val HANDLE_EXISTS = "handle-exists"
    const val NO_TEAM = "no-team"
    const val OPERATION_DENIED = "operation-denied"
    const val MLS_STALE_MESSAGE = "mls-stale-message"
    const val MLS_SELF_REMOVAL_NOT_ALLOWED = "mls-self-removal-not-allowed"
    const val MLS_COMMIT_MISSING_REFERENCES = "mls-commit-missing-references"
    const val MLS_CLIENT_MISMATCH = "mls-client-mismatch"
    const val MLS_UNSUPPORTED_PROPOSAL = "mls-unsupported-proposal"
    const val MLS_KEY_PACKAGE_REF_NOT_FOUND = "mls-key-package-ref-not-found"
    const val MLS_MISSING_GROUP_INFO = "mls-missing-group-info"
    const val MLS_PROTOCOL_ERROR = "mls-protocol-error"
    const val UNKNOWN_CLIENT = "unknown-client"
    const val NOT_TEAM_MEMBER = "no-team-member"
    const val NO_CONVERSATION = "no-conversation"
    const val NO_CONVERSATION_CODE = "no-conversation-code"
    const val GUEST_LINKS_DISABLED = "guest-links-disabled"
    const val ACCESS_DENIED = "access-denied"
    const val WRONG_CONVERSATION_PASSWORD = "invalid-conversation-password"
    const val NOT_FOUND = "not-found"
    const val MISSING_LEGALHOLD_CONSENT = "missing-legalhold-consent"
    const val ACCOUNT_SUSPENDED = "suspended"
    const val ACCOUNT_PENDING_ACTIVATION = "pending-activation"

    // Federation
    const val FEDERATION_FAILURE = "federation-remote-error"
    const val FEDERATION_DENIED = "federation-denied"
    const val FEDERATION_NOT_ENABLED = "federation-not-enabled"
    const val FEDERATION_UNREACHABLE_DOMAINS = "federation-unreachable-domains-error"

    // connection
    const val BAD_CONNECTION_UPDATE = "bad-conn-update"
    object KaliumCustom {
        const val MISSING_REFRESH_TOKEN = "missing-refresh_token"
        const val MISSING_NONCE = "missing-nonce"
        const val MISSING_CHALLENGE_TYPE = "missing-challenge-type"
    }

}

enum class AuthenticationCodeFailure(val responseLabel: String) {
    MISSING_AUTHENTICATION_CODE("code-authentication-required"),
    INVALID_OR_EXPIRED_AUTHENTICATION_CODE("code-authentication-failed");
}
