package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MLSMessageCreatorTest {

    @Mock
    private val mlsClientProvider = mock(MLSClientProvider::class)

    @Mock
    private val protoContentMapper = mock(ProtoContentMapper::class)

    private lateinit var mlsMessageCreator: MLSMessageCreator

    @BeforeTest
    fun setup() {
        mlsMessageCreator = MLSMessageCreatorImpl(mlsClientProvider, protoContentMapper)
    }

    @Test
    fun givenMessage_whenCreatingMLSMessage_thenMLSClientShouldBeUsedToEncryptProtobufContent() = runTest {
        val encryptedData = byteArrayOf()
        given(mlsClientProvider)
            .suspendFunction(mlsClientProvider::getMLSClient)
            .whenInvokedWith(anything())
            .then { Either.Right(MLS_CLIENT)}

        given(MLS_CLIENT)
            .function(MLS_CLIENT::encryptMessage)
            .whenInvokedWith(anything(), anything())
            .thenReturn(encryptedData)

        val plainData = byteArrayOf(0x42, 0x73)
        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(plainData))

        mlsMessageCreator.createOutgoingMLSMessage(GROUP_ID, TestMessage.TEXT_MESSAGE).shouldSucceed {}

        verify(MLS_CLIENT)
            .function(MLS_CLIENT::encryptMessage)
            .with(eq(GROUP_ID), eq(plainData))
            .wasInvoked(once)
    }

    private companion object {
        const val GROUP_ID = "groupId"
        val MLS_CLIENT = mock(classOf<MLSClient>())
    }

}
