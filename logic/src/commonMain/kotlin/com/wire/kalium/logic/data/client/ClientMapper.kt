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

package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.authenticated.client.ClientDTO
import com.wire.kalium.network.api.authenticated.client.ClientTypeDTO
import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.authenticated.client.RegisterClientRequest
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.authenticated.client.UpdateClientCapabilitiesRequest
import com.wire.kalium.persistence.dao.client.ClientTypeEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.client.InsertClientParam
import com.wire.kalium.persistence.dao.newclient.NewClientEntity
import com.wire.kalium.network.api.model.UserId as UserIdDTO
import com.wire.kalium.persistence.dao.client.Client as ClientEntity

@Suppress("TooManyFunctions")
internal class ClientMapper(
    private val preyKeyMapper: PreKeyMapper
) {

    internal fun toRegisterClientRequest(
        clientConfig: ClientConfig,
        param: RegisterClientParameters,
    ): RegisterClientRequest = RegisterClientRequest(
        password = param.password,
        lastKey = preyKeyMapper.toPreKeyDTO(param.lastKey),
        label = clientConfig.deviceName(),
        deviceType = toDeviceTypeDTO(clientConfig.deviceType()),
        type = param.clientType?.let { toClientTypeDTO(param.clientType) } ?: toClientTypeDTO(clientConfig.clientType()),
        capabilities = param.capabilities?.let { capabilities -> capabilities.map { toClientCapabilityDTO(it) } },
        model = (param.model?.let { param.model } ?: clientConfig.deviceModelName()) + param.modelPostfix.orEmpty(),
        preKeys = param.preKeys.map { preyKeyMapper.toPreKeyDTO(it) },
        cookieLabel = param.cookieLabel,
        secondFactorVerificationCode = param.secondFactorVerificationCode,
    )

    internal fun toUpdateClientCapabilitiesRequest(
        updateClientCapabilitiesParam: UpdateClientCapabilitiesParam,
    ): UpdateClientCapabilitiesRequest = UpdateClientCapabilitiesRequest(
        capabilities = updateClientCapabilitiesParam.capabilities.map { toClientCapabilityDTO(it) },
    )

    // TODO: mapping directly form DTO to domain object is not ideal since we lose verification information
    internal fun fromClientDto(client: ClientDTO): Client = clientFromDTO(client)

    internal fun fromClientEntity(clientEntity: ClientEntity): Client = with(clientEntity) {
        Client(
            id = ClientId(id),
            type = clientType?.let { fromClientTypeEntity(it) },
            registrationTime = registrationDate,
            lastActive = lastActive,
            deviceType = deviceType?.let { fromDeviceTypeEntity(deviceType) },
            label = label,
            model = model,
            isVerified = isProteusVerified,
            isValid = isValid,
            mlsPublicKeys = mlsPublicKeys,
            isMLSCapable = isMLSCapable,
            isAsyncNotificationsCapable = isAsyncNotificationsCapable
        )
    }

    internal fun fromNewClientEntity(clientEntity: NewClientEntity): Client = with(clientEntity) {
        Client(
            id = ClientId(id),
            type = null,
            registrationTime = registrationDate,
            lastActive = null,
            deviceType = deviceType?.let { fromDeviceTypeEntity(deviceType) },
            label = null,
            model = model,
            isVerified = false,
            isValid = true,
            mlsPublicKeys = null,
            isMLSCapable = false,
            isAsyncNotificationsCapable = false
        )
    }

    internal fun toInsertClientParam(simpleClientResponse: List<SimpleClientResponse>, userIdDTO: UserIdDTO): List<InsertClientParam> =
        simpleClientResponse.map {
            with(it) {
                InsertClientParam(
                    userId = userIdDTO.toDao(),
                    id = id,
                    deviceType = toDeviceTypeEntity(deviceClass),
                    clientType = null,
                    label = null,
                    model = null,
                    registrationDate = null,
                    lastActive = null,
                    mlsPublicKeys = null,
                    isMLSCapable = false,
                    isAsyncNotificationsCapable = false
                )
            }
        }

    internal fun toInsertClientParam(client: ClientDTO, userIdDTO: UserIdDTO): InsertClientParam =
        client.selfClientParameters(UserId(userIdDTO.value, userIdDTO.domain))

    internal fun toInsertClientParam(userId: UserId, clientId: List<ClientId>): List<InsertClientParam> =
        clientId.map {
            InsertClientParam(
                userId = userId.toDao(),
                id = it.value,
                deviceType = null,
                clientType = null,
                label = null,
                model = null,
                registrationDate = null,
                lastActive = null,
                mlsPublicKeys = null,
                isMLSCapable = false,
                isAsyncNotificationsCapable = false
            )
        }

    internal fun toInsertClientParam(userId: UserId, event: Event.User.NewClient): InsertClientParam =
        event.client.newClientParameters(userId)

    private fun toClientTypeDTO(clientType: ClientType): ClientTypeDTO = when (clientType) {
        ClientType.Temporary -> ClientTypeDTO.Temporary
        ClientType.Permanent -> ClientTypeDTO.Permanent
        ClientType.LegalHold -> ClientTypeDTO.LegalHold
    }

    private fun fromClientTypeEntity(clientTypeEntity: ClientTypeEntity): ClientType = when (clientTypeEntity) {
        ClientTypeEntity.Temporary -> ClientType.Temporary
        ClientTypeEntity.Permanent -> ClientType.Permanent
        ClientTypeEntity.LegalHold -> ClientType.LegalHold
    }

    private fun toClientCapabilityDTO(clientCapability: ClientCapability): ClientCapabilityDTO = when (clientCapability) {
        ClientCapability.LegalHoldImplicitConsent -> ClientCapabilityDTO.LegalHoldImplicitConsent
        ClientCapability.ConsumableNotifications -> ClientCapabilityDTO.ConsumableNotifications
        is ClientCapability.Unknown -> ClientCapabilityDTO.Unknown(clientCapability.name)
    }

    private fun fromClientCapabilityDTO(clientCapabilityDTO: ClientCapabilityDTO): ClientCapability = when (clientCapabilityDTO) {
        ClientCapabilityDTO.LegalHoldImplicitConsent -> ClientCapability.LegalHoldImplicitConsent
        ClientCapabilityDTO.ConsumableNotifications -> ClientCapability.ConsumableNotifications
        is ClientCapabilityDTO.Unknown -> ClientCapability.Unknown(clientCapabilityDTO.name)
    }

    internal fun fromOtherUsersClientsDTO(otherUsersClients: List<ClientEntity>): List<OtherUserClient> =
        otherUsersClients.map {
            OtherUserClient(fromDeviceTypeEntity(it.deviceType), it.id, it.isValid, it.isProteusVerified)
        }

    private fun toDeviceTypeDTO(deviceType: DeviceType): DeviceTypeDTO = when (deviceType) {
        DeviceType.Phone -> DeviceTypeDTO.Phone
        DeviceType.Tablet -> DeviceTypeDTO.Tablet
        DeviceType.Desktop -> DeviceTypeDTO.Desktop
        DeviceType.LegalHold -> DeviceTypeDTO.LegalHold
        DeviceType.Unknown -> DeviceTypeDTO.Unknown
    }

    internal fun fromDeviceTypeEntity(deviceTypeEntity: DeviceTypeEntity?): DeviceType = when (deviceTypeEntity) {
        DeviceTypeEntity.Phone -> DeviceType.Phone
        DeviceTypeEntity.Tablet -> DeviceType.Tablet
        DeviceTypeEntity.Desktop -> DeviceType.Desktop
        DeviceTypeEntity.LegalHold -> DeviceType.LegalHold
        DeviceTypeEntity.Unknown, null -> DeviceType.Unknown
    }

    internal fun toDeviceTypeEntity(deviceTypeEntity: DeviceType): DeviceTypeEntity = deviceTypeEntity.toDeviceEntity()

    internal fun toDeviceTypeEntity(deviceTypeDTO: DeviceTypeDTO): DeviceTypeEntity = when (deviceTypeDTO) {
        DeviceTypeDTO.Phone -> DeviceTypeEntity.Phone
        DeviceTypeDTO.Tablet -> DeviceTypeEntity.Tablet
        DeviceTypeDTO.Desktop -> DeviceTypeEntity.Desktop
        DeviceTypeDTO.LegalHold -> DeviceTypeEntity.LegalHold
        DeviceTypeDTO.Unknown -> DeviceTypeEntity.Unknown
    }

    internal fun toClientTypeEntity(clientTypeDTO: ClientTypeDTO): ClientTypeEntity = when (clientTypeDTO) {
        ClientTypeDTO.Temporary -> ClientTypeEntity.Temporary
        ClientTypeDTO.Permanent -> ClientTypeEntity.Permanent
        ClientTypeDTO.LegalHold -> ClientTypeEntity.LegalHold
    }

    internal fun toClientTypeEntity(clientType: ClientType): ClientTypeEntity = clientType.toClientEntity()

}
