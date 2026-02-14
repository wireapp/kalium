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

package com.wire.kalium.logic.data.properties

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.FolderWithConversations
import com.wire.kalium.logic.data.conversation.folders.toFolder
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.properties.PropertyKey
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNotFound
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

internal interface ReadReceiptsPropertyRepository {
    suspend fun getReadReceiptsStatus(): Boolean
    suspend fun observeReadReceiptsStatus(): Flow<Either<CoreFailure, Boolean>>
    suspend fun syncReadReceiptsStatus(): Either<CoreFailure, Unit>
    suspend fun setReadReceiptsEnabled(): Either<CoreFailure, Unit>
    suspend fun deleteReadReceiptsProperty(): Either<CoreFailure, Unit>
}

internal interface TypingIndicatorPropertyRepository {
    suspend fun getTypingIndicatorStatus(): Boolean
    suspend fun observeTypingIndicatorStatus(): Flow<Either<CoreFailure, Boolean>>
    suspend fun syncTypingIndicatorStatus(): Either<CoreFailure, Unit>
    suspend fun setTypingIndicatorEnabled(): Either<CoreFailure, Unit>
    suspend fun removeTypingIndicatorProperty(): Either<CoreFailure, Unit>
}

internal interface ScreenshotCensoringPropertyRepository {
    suspend fun syncScreenshotCensoringStatus(): Either<CoreFailure, Unit>
    suspend fun setScreenshotCensoringEnabled(): Either<CoreFailure, Unit>
    suspend fun deleteScreenshotCensoringProperty(): Either<CoreFailure, Unit>
}

internal interface UserPropertiesSyncRepository {
    suspend fun syncPropertiesStatuses(): Either<CoreFailure, Unit>
}

internal interface ConversationFoldersPropertyRepository {
    suspend fun getConversationFolders(): Either<CoreFailure, List<FolderWithConversations>>
}

@Mockable
internal interface UserPropertyRepository :
    ReadReceiptsPropertyRepository,
    TypingIndicatorPropertyRepository,
    ScreenshotCensoringPropertyRepository,
    UserPropertiesSyncRepository,
    ConversationFoldersPropertyRepository

internal class UserPropertyDataSource(
    propertiesApi: PropertiesApi,
    userConfigRepository: UserConfigRepository,
    selfUserId: UserId,
) : UserPropertyRepository,
    ReadReceiptsPropertyRepository by ReadReceiptsPropertyDataSource(propertiesApi, userConfigRepository),
    TypingIndicatorPropertyRepository by TypingIndicatorPropertyDataSource(propertiesApi, userConfigRepository),
    ScreenshotCensoringPropertyRepository by ScreenshotCensoringPropertyDataSource(propertiesApi, userConfigRepository),
    UserPropertiesSyncRepository by UserPropertiesSyncDataSource(propertiesApi, userConfigRepository),
    ConversationFoldersPropertyRepository by ConversationFoldersPropertyDataSource(propertiesApi, selfUserId)

private class ReadReceiptsPropertyDataSource(
    private val propertiesApi: PropertiesApi,
    private val userConfigRepository: UserConfigRepository,
) : ReadReceiptsPropertyRepository {

    override suspend fun getReadReceiptsStatus(): Boolean =
        userConfigRepository.isReadReceiptsEnabled()
            .firstOrNull()
            ?.fold({ false }, { it }) ?: false

    override suspend fun observeReadReceiptsStatus(): Flow<Either<CoreFailure, Boolean>> = userConfigRepository.isReadReceiptsEnabled()

    override suspend fun syncReadReceiptsStatus(): Either<CoreFailure, Unit> =
        wrapApiRequest {
            propertiesApi.getProperty(PropertyKey.WIRE_RECEIPT_MODE)
        }.flatMapLeft { failure ->
            if (failure.isPropertyNotFound()) {
                Either.Right(0)
            } else {
                Either.Left(failure)
            }
        }.flatMap { value ->
            userConfigRepository.setReadReceiptsStatus(value == 1)
        }

    override suspend fun setReadReceiptsEnabled(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.setProperty(PropertyKey.WIRE_RECEIPT_MODE, 1)
    }.flatMap {
        userConfigRepository.setReadReceiptsStatus(true)
    }

    override suspend fun deleteReadReceiptsProperty(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.deleteProperty(PropertyKey.WIRE_RECEIPT_MODE)
    }.flatMap {
        userConfigRepository.setReadReceiptsStatus(false)
    }
}

private class TypingIndicatorPropertyDataSource(
    private val propertiesApi: PropertiesApi,
    private val userConfigRepository: UserConfigRepository,
) : TypingIndicatorPropertyRepository {

    override suspend fun getTypingIndicatorStatus(): Boolean =
        userConfigRepository.isTypingIndicatorEnabled()
            .firstOrNull()
            ?.fold({ false }, { it }) ?: true

    override suspend fun observeTypingIndicatorStatus(): Flow<Either<CoreFailure, Boolean>> =
        userConfigRepository.isTypingIndicatorEnabled()

    override suspend fun syncTypingIndicatorStatus(): Either<CoreFailure, Unit> =
        wrapApiRequest {
            propertiesApi.getProperty(PropertyKey.WIRE_TYPING_INDICATOR_MODE)
        }.flatMapLeft { failure ->
            if (failure.isPropertyNotFound()) {
                Either.Right(1)
            } else {
                Either.Left(failure)
            }
        }.flatMap { value ->
            userConfigRepository.setTypingIndicatorStatus(value != 0)
        }

    override suspend fun setTypingIndicatorEnabled(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.deleteProperty(PropertyKey.WIRE_TYPING_INDICATOR_MODE)
    }.flatMap {
        userConfigRepository.setTypingIndicatorStatus(true)
    }

    override suspend fun removeTypingIndicatorProperty(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.setProperty(PropertyKey.WIRE_TYPING_INDICATOR_MODE, 0)
    }.flatMap {
        userConfigRepository.setTypingIndicatorStatus(false)
    }
}

private class ScreenshotCensoringPropertyDataSource(
    private val propertiesApi: PropertiesApi,
    private val userConfigRepository: UserConfigRepository,
) : ScreenshotCensoringPropertyRepository {

    override suspend fun syncScreenshotCensoringStatus(): Either<CoreFailure, Unit> =
        wrapApiRequest {
            propertiesApi.getProperty(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE)
        }.flatMapLeft { failure ->
            if (failure.isPropertyNotFound()) {
                Either.Right(0)
            } else {
                Either.Left(failure)
            }
        }.flatMap { value ->
            userConfigRepository.setScreenshotCensoringConfig(value == 1)
        }

    override suspend fun setScreenshotCensoringEnabled(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.setProperty(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE, 1)
    }.flatMap {
        userConfigRepository.setScreenshotCensoringConfig(true)
    }

    override suspend fun deleteScreenshotCensoringProperty(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.deleteProperty(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE)
    }.flatMap {
        userConfigRepository.setScreenshotCensoringConfig(false)
    }
}

private class UserPropertiesSyncDataSource(
    private val propertiesApi: PropertiesApi,
    private val userConfigRepository: UserConfigRepository,
) : UserPropertiesSyncRepository {

    override suspend fun syncPropertiesStatuses(): Either<CoreFailure, Unit> =
        wrapApiRequest {
            propertiesApi.getPropertiesValues()
        }.fold({ failure ->
            if (failure.isPropertyNotFound()) {
                syncUsingFallbackCalls()
            } else {
                Either.Left(failure)
            }
        }, { properties ->
            val readReceiptsEnabled = properties.findIntValue(PropertyKey.WIRE_RECEIPT_MODE) == 1
            val typingIndicatorEnabled = properties.findIntValue(PropertyKey.WIRE_TYPING_INDICATOR_MODE)?.let { it != 0 } ?: true
            val screenshotCensoringEnabled = properties.findIntValue(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE) == 1

            userConfigRepository.setReadReceiptsStatus(readReceiptsEnabled)
                .flatMap { userConfigRepository.setTypingIndicatorStatus(typingIndicatorEnabled) }
                .flatMap { userConfigRepository.setScreenshotCensoringConfig(screenshotCensoringEnabled) }
        })

    private suspend fun syncUsingFallbackCalls(): Either<CoreFailure, Unit> {
        suspend fun syncReadReceipts() = wrapApiRequest { propertiesApi.getProperty(PropertyKey.WIRE_RECEIPT_MODE) }
            .flatMapLeft { if (it.isPropertyNotFound()) Either.Right(0) else Either.Left(it) }
            .flatMap { userConfigRepository.setReadReceiptsStatus(it == 1) }

        suspend fun syncTypingIndicator() = wrapApiRequest { propertiesApi.getProperty(PropertyKey.WIRE_TYPING_INDICATOR_MODE) }
            .flatMapLeft { if (it.isPropertyNotFound()) Either.Right(1) else Either.Left(it) }
            .flatMap { userConfigRepository.setTypingIndicatorStatus(it != 0) }

        suspend fun syncScreenshotCensoring() = wrapApiRequest {
            propertiesApi.getProperty(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE)
        }
            .flatMapLeft { if (it.isPropertyNotFound()) Either.Right(0) else Either.Left(it) }
            .flatMap { userConfigRepository.setScreenshotCensoringConfig(it == 1) }

        return syncReadReceipts()
            .flatMap { syncTypingIndicator() }
            .flatMap { syncScreenshotCensoring() }
    }
}

private class ConversationFoldersPropertyDataSource(
    private val propertiesApi: PropertiesApi,
    private val selfUserId: UserId,
) : ConversationFoldersPropertyRepository {

    override suspend fun getConversationFolders(): Either<CoreFailure, List<FolderWithConversations>> = wrapApiRequest {
        propertiesApi.getLabels()
    }.map { labelListResponse ->
        labelListResponse.labels.map { label -> label.toFolder(selfUserId.domain) }
    }
}

private fun CoreFailure.isPropertyNotFound(): Boolean =
    this is NetworkFailure.ServerMiscommunication &&
            kaliumException is KaliumException.InvalidRequestError &&
            (kaliumException as KaliumException.InvalidRequestError).isNotFound()

private fun JsonObject.findIntValue(propertyKey: PropertyKey): Int? = this[propertyKey.key].toIntOrNull()

private fun JsonElement?.toIntOrNull(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) {
        primitive.content.toIntOrNull()
    } else {
        primitive.intOrNull
    }
}
