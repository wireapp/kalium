/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.network.api.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

/** The app and receiving use the same batching and federation fallback. */
suspend fun fetchUserProfiles(
    qualifiedUserIdList: Set<UserId>,
    selfUserId: UserId,
    api: UserDetailsApi,
): Either<CoreFailure, ListUsersDTO> = if (qualifiedUserIdList.isEmpty()) {
    Either.Right(ListUsersDTO(emptyList(), emptyList()))
} else {
    qualifiedUserIdList
        .chunked(USER_PROFILE_BATCH_SIZE)
        .foldToEitherWhileRight(ListUsersDTO(emptyList(), emptyList())) { chunk, usersDTO ->
            wrapApiRequest {
                kaliumLogger.d("Fetching ${chunk.size} users")
                api.getMultipleUsers(
                    ListUserRequest.qualifiedIds(chunk.map { userId -> userId.toApi() })
                )
            }.map {
                kaliumLogger.d("Found ${it.usersFound.size} users and ${it.usersFailed.size} failed users")
                usersDTO.copy(
                    usersFound = (usersDTO.usersFound + it.usersFound).distinct(),
                    usersFailed = (usersDTO.usersFailed + it.usersFailed).distinct(),
                )
            }
        }
        .flatMapLeft { error ->
            if (error is NetworkFailure.FederatedBackendFailure.FederationNotEnabled) {
                val domains = qualifiedUserIdList
                    .filterNot { it.domain == selfUserId.domain }
                    .map { it.domain.obfuscateDomain() }
                    .toSet()
                val domainNames = domains.joinToString(separator = ", ")
                kaliumLogger.e("User ids contains different domains when federation is not enabled by backend: $domainNames")
                wrapApiRequest {
                    api.getMultipleUsers(
                        ListUserRequest.qualifiedIds(
                            qualifiedUserIdList.filter { it.domain == selfUserId.domain }
                                .map { userId -> userId.toApi() }
                        )
                    )
                }
            } else {
                Either.Left(error)
            }
        }
}

/**
 * Temporary workaround until we find and fix the root cause of malformed qualified IDs in local storage.
 *
 * On a specific list-users bad-request validation error, we remove malformed local entries and retry once.
 */
suspend fun recoverMalformedQualifiedIdsIfNeeded(
    fetchResult: Either<CoreFailure, ListUsersDTO>,
    qualifiedUserIds: Set<UserId>,
    deleteMalformedUser: suspend (UserId) -> Unit,
    retry: suspend (Set<UserId>) -> Either<CoreFailure, ListUsersDTO>,
): Either<CoreFailure, ListUsersDTO> = fetchResult.fold(
    fnL = { error ->
        val malformedDomainError = error.asMalformedQualifiedIdsDomainError()
        val malformedUsers = qualifiedUserIds
            .filter { it.value.isBlank() || it.domain.isBlank() }
            .toSet()

        if (malformedDomainError == null || malformedUsers.isEmpty()) {
            Either.Left(error)
        } else {
            kaliumLogger.logStructuredJson(
                level = KaliumLogLevel.ERROR,
                leadingMessage = "list-users malformed qualified_ids domain",
                jsonStringKeyValues = mapOf(
                    "event" to "list_users_malformed_qualified_ids_domain",
                    "action" to "delete_malformed_users_and_retry",
                    "malformedUsersCount" to malformedUsers.size,
                    "sample" to malformedUsers.toObfuscatedSample(),
                    "cause" to malformedDomainError.errorResponse.message,
                    "stackTrace" to malformedDomainError.stackTraceToString(),
                )
            )

            wrapStorageRequest {
                malformedUsers.forEach { malformedUser ->
                    deleteMalformedUser(malformedUser)
                }
            }.fold(
                fnL = { Either.Left(error) },
                fnR = {
                    val validUsers = qualifiedUserIds
                        .filterNot(malformedUsers::contains)
                        .toSet()
                    retry(validUsers)
                }
            )
        }
    },
    fnR = { listUsersDTO -> Either.Right(listUsersDTO) }
)

private fun CoreFailure.asMalformedQualifiedIdsDomainError(): KaliumException.InvalidRequestError? {
    val invalidRequest = (this as? NetworkFailure.ServerMiscommunication)?.kaliumException as? KaliumException.InvalidRequestError
    return if (
        invalidRequest?.errorResponse?.code == HttpStatusCode.BadRequest.value &&
        QUALIFIED_IDS_DOMAIN_VALIDATION_ERROR_REGEX.matches(invalidRequest.errorResponse.message)
    ) {
        invalidRequest
    } else {
        null
    }
}

private val QUALIFIED_IDS_DOMAIN_VALIDATION_ERROR_REGEX =
    Regex("^Error in \\$\\['qualified_ids']\\[\\d+]\\.domain:.*$", setOf(RegexOption.IGNORE_CASE))

private fun Set<UserId>.toObfuscatedSample(limit: Int = 3): String =
    take(limit).joinToString { "${it.value.obfuscateId()}@${it.domain.obfuscateDomain()}" }

private const val USER_PROFILE_BATCH_SIZE = 500
