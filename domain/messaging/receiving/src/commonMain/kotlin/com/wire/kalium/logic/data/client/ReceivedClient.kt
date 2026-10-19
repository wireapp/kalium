/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.authenticated.client.ClientDTO
import com.wire.kalium.network.api.authenticated.client.ClientTypeDTO
import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.persistence.dao.client.ClientTypeEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.client.InsertClientParam
import kotlin.time.Clock
import kotlin.time.Instant

fun clientFromDTO(client: ClientDTO): Client = Client(
    id = ClientId(client.clientId),
    type = fromClientTypeDTO(client.type),
    registrationTime = Instant.parse(client.registrationTime),
    lastActive = client.lastActive?.let { Instant.parse(it).coerceAtMost(Clock.System.now()) },
    deviceType = fromDeviceTypeDTO(client.deviceType),
    label = client.label,
    model = client.model,
    isVerified = false,
    isValid = true,
    mlsPublicKeys = client.mlsPublicKeys,
    isMLSCapable = client.mlsPublicKeys?.isNotEmpty() ?: false,
    isAsyncNotificationsCapable = client.capabilities.contains(ClientCapabilityDTO.ConsumableNotifications)
)

/** Full self-client refresh retains server MLS public keys; new-client alerts intentionally do not. */
fun ClientDTO.selfClientParameters(userId: UserId): InsertClientParam =
    clientFromDTO(this).newClientParameters(userId).copy(mlsPublicKeys = mlsPublicKeys)

fun Client.newClientParameters(userId: UserId): InsertClientParam =
    InsertClientParam(
        userId = userId.toDao(),
        id = id.value,
        deviceType = deviceType?.let { it.toDeviceEntity() },
        clientType = type?.let { it.toClientEntity() },
        label = label,
        model = model,
        registrationDate = registrationTime,
        lastActive = lastActive,
        mlsPublicKeys = null,
        isMLSCapable = isMLSCapable,
        isAsyncNotificationsCapable = isAsyncNotificationsCapable
    )

fun DeviceType.toDeviceEntity(): DeviceTypeEntity = when (this) {
    DeviceType.Phone -> DeviceTypeEntity.Phone
    DeviceType.Tablet -> DeviceTypeEntity.Tablet
    DeviceType.Desktop -> DeviceTypeEntity.Desktop
    DeviceType.LegalHold -> DeviceTypeEntity.LegalHold
    DeviceType.Unknown -> DeviceTypeEntity.Unknown
}

fun ClientType.toClientEntity(): ClientTypeEntity = when (this) {
    ClientType.Temporary -> ClientTypeEntity.Temporary
    ClientType.Permanent -> ClientTypeEntity.Permanent
    ClientType.LegalHold -> ClientTypeEntity.LegalHold
}

private fun fromClientTypeDTO(clientTypeDTO: ClientTypeDTO): ClientType = when (clientTypeDTO) {
    ClientTypeDTO.Temporary -> ClientType.Temporary
    ClientTypeDTO.Permanent -> ClientType.Permanent
    ClientTypeDTO.LegalHold -> ClientType.LegalHold
}

private fun fromDeviceTypeDTO(deviceTypeDTO: DeviceTypeDTO): DeviceType = when (deviceTypeDTO) {
    DeviceTypeDTO.Phone -> DeviceType.Phone
    DeviceTypeDTO.Tablet -> DeviceType.Tablet
    DeviceTypeDTO.Desktop -> DeviceType.Desktop
    DeviceTypeDTO.LegalHold -> DeviceType.LegalHold
    DeviceTypeDTO.Unknown -> DeviceType.Unknown
}
