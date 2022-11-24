package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.location.LocationMapper
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.network.api.base.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientResponse
import com.wire.kalium.network.api.base.authenticated.client.ClientTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.RegisterClientRequest
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity

class ClientMapper(
    private val preyKeyMapper: PreKeyMapper,
    private val locationMapper: LocationMapper
) {

    fun toRegisterClientRequest(
        clientConfig: ClientConfig,
        param: RegisterClientParam
    ): RegisterClientRequest = RegisterClientRequest(
        password = param.password,
        lastKey = preyKeyMapper.toPreKeyDTO(param.lastKey),
        label = clientConfig.deviceName(),
        deviceType = toDeviceTypeDTO(clientConfig.deviceType()),
        type = param.clientType?.let { toClientTypeDTO(param.clientType) } ?: toClientTypeDTO(clientConfig.clientType()),
        capabilities = param.capabilities?.let { capabilities -> capabilities.map { toClientCapabilityDTO(it) } } ?: run { null },
        model = clientConfig.deviceModelName(),
        preKeys = param.preKeys.map { preyKeyMapper.toPreKeyDTO(it) },
    )

    fun fromClientResponse(response: ClientResponse): Client = Client(
        id = ClientId(response.clientId),
        type = fromClientTypeDTO(response.type),
        registrationTime = response.registrationTime,
        location = response.location?.let { locationMapper.fromLocationResponse(it) } ?: run { null },
        deviceType = response.deviceType?.let { fromDeviceTypeDTO(it) } ?: run { null },
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

    fun fromOtherUsersClientsDTO(otherUsersClients: List<SimpleClientResponse>): List<OtherUserClient> =
        otherUsersClients.map {
            OtherUserClient(DeviceType.valueOf(it.deviceClass.name), it.id)
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
}
