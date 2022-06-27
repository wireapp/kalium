package com.wire.kalium.api.tools.json.api.message

import com.wire.kalium.api.tools.IgnoreIOS
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.network.api.message.MessagePriority
import com.wire.kalium.network.api.message.provideEnvelopeProtoMapper
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.otr.QualifiedNewOtrMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals

@IgnoreIOS
class EnvelopeProtoMapperTest {

    private val envelopeProtoMapper = provideEnvelopeProtoMapper()

    @Test
    fun givenEnvelopeWithData_whenMappingToProtobuf_thenBlobShouldMatch() {
        val data = byteArrayOf(0x42, 0x13, 0x69)

        val encoded = envelopeProtoMapper.encodeToProtobuf(
            MessageApi.Parameters.QualifiedDefaultParameters(
                sender = TEST_SENDER,
                recipients = mapOf(),
                nativePush = true,
                priority = MessagePriority.HIGH,
                transient = false,
                data = data,
                messageOption = MessageApi.QualifiedMessageOption.ReportAll
            )
        )
        val newOtrMessage = QualifiedNewOtrMessage.decodeFromByteArray(encoded)

        assertContentEquals(data, newOtrMessage.blob!!.array)
    }

    private companion object{
        val TEST_SENDER = "9AFBD180"
    }
}
