package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.location.LocationMapper
import com.wire.kalium.logic.data.prekey.PreyKeyMapper
import com.wire.kalium.network.api.user.client.ClientCapabilityDTO
import com.wire.kalium.network.api.user.client.ClientTypeDTO
import com.wire.kalium.network.api.user.client.DeviceTypeDTO
import com.wire.kalium.network.api.user.client.RegisterClientRequest
import com.wire.kalium.network.api.user.client.RegisterClientResponse

class ClientMapper(
    private val preyKeyMapper: PreyKeyMapper,
    private val locationMapper: LocationMapper,
    private val clientConfig: ClientConfig
) {

    fun toRegisterClientRequest(param: RegisterClientParam): RegisterClientRequest = RegisterClientRequest(
        password = param.password,
        lastKey = preyKeyMapper.toPreKeyDTO(param.lastKey),
        label = clientConfig.deviceName(),
        deviceType = toDeviceTypeDTO(clientConfig.deviceType()),
        type = toClientTypeDTO(clientConfig.clientType()),
        capabilities = param.capabilities?.let { capabilities -> capabilities.map { toClientCapabilityDTO(it) } } ?: run { null },
        model = clientConfig.deviceModelName(),
        preKeyDTOS = param.preKeys.map { preyKeyMapper.toPreKeyDTO(it) },
    )

    fun fromRegisterClientResponse(response: RegisterClientResponse): Client = Client(
        clientId = response.clientId,
        type = fromClientTypeDTO(response.type),
        registrationTime = response.registrationTime,
        location = locationMapper.fromLocationDTO(response.location),
        deviceType = response.deviceType?.let { fromDeviceTypeDTO(it) } ?: run { null },
        label = response.label,
        cookie = response.cookie,
        capabilities = response.capabilities?.let { capabilities -> Capabilities(capabilities.capabilities.map { fromClientCapabilityDTO(it) }) }
            ?: run { null },
        model = response.model
    )

    private fun toClientTypeDTO(clientType: ClientType): ClientTypeDTO {
        return when (clientType) {
            ClientType.Temporary -> ClientTypeDTO.Temporary
            ClientType.Permanent -> ClientTypeDTO.Permanent
            ClientType.LegalHold -> ClientTypeDTO.LegalHold
        }
    }

    private fun fromClientTypeDTO(clientTypeDTO: ClientTypeDTO): ClientType {
        return when (clientTypeDTO) {
            ClientTypeDTO.Temporary -> ClientType.Temporary
            ClientTypeDTO.Permanent -> ClientType.Permanent
            ClientTypeDTO.LegalHold -> ClientType.LegalHold
        }
    }

    private fun toClientCapabilityDTO(clientCapability: ClientCapability): ClientCapabilityDTO {
        return when (clientCapability) {
            ClientCapability.LegalHoldImplicitConsent -> ClientCapabilityDTO.LegalHoldImplicitConsent
        }
    }

    private fun fromClientCapabilityDTO(clientCapabilityDTO: ClientCapabilityDTO): ClientCapability {
        return when (clientCapabilityDTO) {
            ClientCapabilityDTO.LegalHoldImplicitConsent -> ClientCapability.LegalHoldImplicitConsent
        }
    }

    private fun toDeviceTypeDTO(deviceType: DeviceType): DeviceTypeDTO = when (deviceType) {
        DeviceType.Phone -> DeviceTypeDTO.Phone
        DeviceType.Tablet -> DeviceTypeDTO.Tablet
        DeviceType.Desktop -> DeviceTypeDTO.Desktop
        DeviceType.LegalHold -> DeviceTypeDTO.LegalHold
    }

    private fun fromDeviceTypeDTO(deviceTypeDTO: DeviceTypeDTO): DeviceType = when (deviceTypeDTO) {
        DeviceTypeDTO.Phone -> DeviceType.Phone
        DeviceTypeDTO.Tablet -> DeviceType.Tablet
        DeviceTypeDTO.Desktop -> DeviceType.Desktop
        DeviceTypeDTO.LegalHold -> DeviceType.LegalHold
    }
}
