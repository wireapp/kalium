/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

@file:Suppress("TooManyFunctions", "MagicNumber")

package com.wire.kalium.common.error

fun ServerError.InvalidRequest.isInvalidCredentials(): Boolean {
    return errorResponse.label == "invalid-credentials"
}

fun ServerError.InvalidRequest.isUnknownClient(): Boolean {
    return errorResponse.label == "unknown-client"
}

fun ServerError.InvalidRequest.isTooManyClients(): Boolean {
    return errorResponse.label == "too-many-clients"
}

fun ServerError.InvalidRequest.isMissingAuth(): Boolean {
    return errorResponse.label == "missing-auth"
}

fun ServerError.InvalidRequest.isBadRequest(): Boolean {
    return errorResponse.label == "bad-request"
}

fun ServerError.InvalidRequest.isNotFound(): Boolean {
    return errorResponse.code == 404
}

fun ServerError.InvalidRequest.isNotFoundLabel(): Boolean {
    return errorResponse.label == "not-found"
}

fun ServerError.InvalidRequest.isDomainBlockedForRegistration(): Boolean {
    return errorResponse.label == "domain-blocked-for-registration"
}

fun ServerError.InvalidRequest.isKeyExists(): Boolean {
    return errorResponse.label == "key-exists"
}

fun ServerError.InvalidRequest.isBlackListedEmail(): Boolean {
    return errorResponse.label == "blacklisted-email"
}

fun ServerError.InvalidRequest.isInvalidEmail(): Boolean {
    return errorResponse.label == "invalid-email"
}

fun ServerError.InvalidRequest.isInvalidHandle(): Boolean {
    return errorResponse.label == "invalid-handle"
}

fun ServerError.InvalidRequest.isTooManyRequests(): Boolean {
    return errorResponse.code == 429 || errorResponse.code == HTTP_STATUS_NGINZ_TOO_MANY_REQUESTS
}

fun ServerError.InvalidRequest.isHandleExists(): Boolean {
    return errorResponse.label == "handle-exists"
}

fun ServerError.InvalidRequest.isInvalidCode(): Boolean {
    return errorResponse.label == "invalid-code"
}

fun ServerError.InvalidRequest.isUserCreationRestricted(): Boolean {
    return errorResponse.label == "user-creation-restricted"
}

fun ServerError.InvalidRequest.isTooManyMembers(): Boolean {
    return errorResponse.label == "too-many-team-members"
}

fun ServerError.InvalidRequest.isNoTeam(): Boolean {
    return errorResponse.label == "no-team"
}

fun ServerError.InvalidRequest.isOperationDenied(): Boolean {
    return errorResponse.label == "operation-denied"
}

fun ServerError.InvalidRequest.isNotTeamMember(): Boolean {
    return errorResponse.label == "no-team-member"
}

fun ServerError.InvalidRequest.isConversationNotFound(): Boolean {
    return errorResponse.label == "no-conversation"
}

fun ServerError.InvalidRequest.isConversationHasNoCode(): Boolean {
    return errorResponse.label == "no-conversation-code"
}

fun ServerError.InvalidRequest.isGuestLinkDisabled(): Boolean {
    return errorResponse.label == "guest-links-disabled"
}

fun ServerError.InvalidRequest.isAccessDenied(): Boolean {
    return errorResponse.label == "access-denied"
}

fun ServerError.InvalidRequest.isWrongConversationPassword(): Boolean {
    return (errorResponse.label == "invalid-conversation-password") ||
            (errorResponse.label == "bad-request" && errorResponse.message.contains("password"))
}

fun ServerError.InvalidRequest.isBadConnectionStatusUpdate(): Boolean {
    return errorResponse.label == "bad-conn-update"
}

val ServerError.InvalidRequest.authenticationCodeFailure: AuthenticationCodeFailure?
    get() = AuthenticationCodeFailure.entries.firstOrNull {
        errorResponse.label == it.responseLabel
    }

fun ServerError.InvalidRequest.isUserNotFound(): Boolean = errorResponse.label == "user_not_found"
fun ServerError.InvalidRequest.isNoCryptoState(): Boolean = errorResponse.label == "no_crypto_state"
fun ServerError.InvalidRequest.isMlsStaleMessage(): Boolean = errorResponse.label == "mls-stale-message"
fun ServerError.InvalidRequest.isMissingLegalHoldConsent(): Boolean = errorResponse.label == "missing-legalhold-consent"
fun ServerError.InvalidRequest.isAccountSuspended(): Boolean = errorResponse.label == "suspended"
fun ServerError.InvalidRequest.isAccountPendingActivation(): Boolean = errorResponse.label == "pending-activation"

fun ServerError.Server.isEnterpriseServiceNotEnabled(): Boolean =
    errorResponse.code == 503 && errorResponse.label == "enterprise-service-not-enabled"

fun ServerError.InvalidRequest.isMeetingNotFound(): Boolean =
    errorResponse.code == 404 && errorResponse.label == "meeting-not-found"

private const val HTTP_STATUS_NGINZ_TOO_MANY_REQUESTS = 420
