/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.location.LocationMapper
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.network.api.base.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientResponse
import com.wire.kalium.network.api.base.authenticated.client.ClientTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.RegisterClientRequest
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.client.InsertClientParam
import com.wire.kalium.network.api.base.model.UserId as UserIdDTO

class ClientMapper(
    private val idMapper: IdMapper,
    private val preyKeyMapper: PreKeyMapper,
    private val locationMapper: LocationMapper
) {

    fun toRegisterClientRequest(
        clientConfig: ClientConfig,
        param: RegisterClientParam,
    ): RegisterClientRequest = RegisterClientRequest(
        password = param.password,
        lastKey = preyKeyMapper.toPreKeyDTO(param.lastKey),
        label = clientConfig.deviceName(),
        deviceType = toDeviceTypeDTO(clientConfig.deviceType()),
        type = param.clientType?.let { toClientTypeDTO(param.clientType) } ?: toClientTypeDTO(clientConfig.clientType()),
        capabilities = param.capabilities?.let { capabilities -> capabilities.map { toClientCapabilityDTO(it) } } ?: run { null },
        model = clientConfig.deviceModelName(),
        preKeys = param.preKeys.map { preyKeyMapper.toPreKeyDTO(it) },
        cookieLabel = param.cookieLabel
    )

    fun fromClientResponse(response: ClientResponse): Client = Client(
        id = ClientId(response.clientId),
        type = fromClientTypeDTO(response.type),
        registrationTime = response.registrationTime,
        location = response.location?.let { locationMapper.fromLocationResponse(it) } ?: run { null },
        deviceType = fromDeviceTypeDTO(response.deviceType),
        label = response.label,
        cookie = response.cookie,
        capabilities = response.capabilities?.let { capabilities ->
            Capabilities(capabilities.capabilities.map
            { fromClientCapabilityDTO(it) })
        }
            ?: run { null },
        model = response.model,
        mlsPublicKeys = response.mlsPublicKeys ?: emptyMap()
    )

    fun fromNewClientEvent(event: Event.User.NewClient): Client = Client(
        id = event.clientId,
        type = fromClientTypeDTO(event.clientType),
        registrationTime = event.registrationTime,
        location = null,
        deviceType = fromDeviceTypeDTO(event.deviceType),
        label = event.label,
        cookie = null,
        capabilities = null,
        model = event.model,
        mlsPublicKeys = emptyMap()
    )

    fun toInsertClientParam(simpleClientResponse: List<SimpleClientResponse>, userIdDTO: UserIdDTO): List<InsertClientParam> =
        simpleClientResponse.map {
            with(it) {
                InsertClientParam(
                    userId = userIdDTO.toDao(),
                    id = id,
                    deviceType = toDeviceTypeEntity(deviceClass)
                )
            }
        }

    private fun toClientTypeDTO(clientType: ClientType): ClientTypeDTO = when (clientType) {
        ClientType.Temporary -> ClientTypeDTO.Temporary
        ClientType.Permanent -> ClientTypeDTO.Permanent
        ClientType.LegalHold -> ClientTypeDTO.LegalHold
    }

    private fun fromClientTypeDTO(clientTypeDTO: ClientTypeDTO): ClientType = when (clientTypeDTO) {
        ClientTypeDTO.Temporary -> ClientType.Temporary
        ClientTypeDTO.Permanent -> ClientType.Permanent
        ClientTypeDTO.LegalHold -> ClientType.LegalHold
    }

    private fun toClientCapabilityDTO(clientCapability: ClientCapability): ClientCapabilityDTO = when (clientCapability) {
        ClientCapability.LegalHoldImplicitConsent -> ClientCapabilityDTO.LegalHoldImplicitConsent
    }

    private fun fromClientCapabilityDTO(clientCapabilityDTO: ClientCapabilityDTO): ClientCapability = when (clientCapabilityDTO) {
        ClientCapabilityDTO.LegalHoldImplicitConsent -> ClientCapability.LegalHoldImplicitConsent
    }

    private fun toDeviceTypeDTO(deviceType: DeviceType): DeviceTypeDTO = when (deviceType) {
        DeviceType.Phone -> DeviceTypeDTO.Phone
        DeviceType.Tablet -> DeviceTypeDTO.Tablet
        DeviceType.Desktop -> DeviceTypeDTO.Desktop
        DeviceType.LegalHold -> DeviceTypeDTO.LegalHold
        DeviceType.Unknown -> DeviceTypeDTO.Unknown
    }

    private fun fromDeviceTypeDTO(deviceTypeDTO: DeviceTypeDTO): DeviceType = when (deviceTypeDTO) {
        DeviceTypeDTO.Phone -> DeviceType.Phone
        DeviceTypeDTO.Tablet -> DeviceType.Tablet
        DeviceTypeDTO.Desktop -> DeviceType.Desktop
        DeviceTypeDTO.LegalHold -> DeviceType.LegalHold
        DeviceTypeDTO.Unknown -> DeviceType.Unknown
    }

    fun fromDeviceTypeEntity(deviceTypeEntity: DeviceTypeEntity?): DeviceType = when (deviceTypeEntity) {
        DeviceTypeEntity.Phone -> DeviceType.Phone
        DeviceTypeEntity.Tablet -> DeviceType.Tablet
        DeviceTypeEntity.Desktop -> DeviceType.Desktop
        DeviceTypeEntity.LegalHold -> DeviceType.LegalHold
        DeviceTypeEntity.Unknown, null -> DeviceType.Unknown
    }

    fun toDeviceTypeEntity(deviceTypeEntity: DeviceType): DeviceTypeEntity = when (deviceTypeEntity) {
        DeviceType.Phone -> DeviceTypeEntity.Phone
        DeviceType.Tablet -> DeviceTypeEntity.Tablet
        DeviceType.Desktop -> DeviceTypeEntity.Desktop
        DeviceType.LegalHold -> DeviceTypeEntity.LegalHold
        DeviceType.Unknown -> DeviceTypeEntity.Unknown
    }

    fun toDeviceTypeEntity(deviceTypeDTO: DeviceTypeDTO): DeviceTypeEntity = when (deviceTypeDTO) {
        DeviceTypeDTO.Phone -> DeviceTypeEntity.Phone
        DeviceTypeDTO.Tablet -> DeviceTypeEntity.Tablet
        DeviceTypeDTO.Desktop -> DeviceTypeEntity.Desktop
        DeviceTypeDTO.LegalHold -> DeviceTypeEntity.LegalHold
        DeviceTypeDTO.Unknown -> DeviceTypeEntity.Unknown
    }
}
