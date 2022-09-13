package com.wire.kalium.logic.data.message

import com.wire.kalium.protobuf.messages.Confirmation

interface ConfirmationTypeMapper {
    fun fromProtoConfirmationTypeToModel(type: Confirmation.Type): Message.ConfirmationType
    fun fromModelConfirmationTypeToProto(type: Message.ConfirmationType): Confirmation.Type
}

internal class ConfirmationTypeMapperImpl : ConfirmationTypeMapper {

    override fun fromProtoConfirmationTypeToModel(type: Confirmation.Type): Message.ConfirmationType =
        when (type) {
            Confirmation.Type.DELIVERED -> Message.ConfirmationType.DELIVERED
            Confirmation.Type.READ -> Message.ConfirmationType.READ
            // In case the type is not recognized it was at least delivered
            is Confirmation.Type.UNRECOGNIZED -> Message.ConfirmationType.DELIVERED
        }

    override fun fromModelConfirmationTypeToProto(type: Message.ConfirmationType): Confirmation.Type =
        when(type) {
            Message.ConfirmationType.DELIVERED -> Confirmation.Type.DELIVERED
            Message.ConfirmationType.READ -> Confirmation.Type.READ
        }

}
