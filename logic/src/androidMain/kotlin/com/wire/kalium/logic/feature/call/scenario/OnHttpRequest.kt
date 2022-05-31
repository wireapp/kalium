package com.wire.kalium.logic.feature.call.scenario

import com.benasher44.uuid.uuid4
import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

//TODO(testing): create unit test
class OnHttpRequest(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val messageSender: MessageSender,
    private val callingScope: CoroutineScope
) {
    fun sendHandlerSuccess(
        context: Pointer?,
        messageString: String?,
        conversationId: ConversationId,
        avsSelfUserId: UserId,
        avsSelfClientId: ClientId
    ) {
        callingScope.launch {
            messageString?.let { message ->
                when (sendCallingMessage(conversationId, avsSelfUserId, avsSelfClientId, message)) {
                    is Either.Right -> {
                        calling.wcall_resp(
                            inst = handle.await(),
                            status = 200,
                            reason = "",
                            arg = context
                        )
                    }
                    is Either.Left -> {
                        calling.wcall_resp(
                            inst = handle.await(),
                            status = 400, // TODO(calling): Handle the errorCode from CoreFailure
                            reason = "Couldn't send Calling Message",
                            arg = context
                        )
                    }
                }
            }
        }
    }

    fun sendClientDiscoveryMessage(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
    ) {
        /*

          val message = QualifiedOtrMessage(selfClientId, QEncryptedContent.Empty, nativePush = false)
            msgClient.postMessage(convId, message).future.map {
              case Left(error) => Left(error)
              case Right(resp) => Right(resp.missing)
            }

         */
        callingScope.launch {
            val message = Message(
                uuid4().toString(),
                MessageContent.Empty,
                conversationId,
                Clock.System.now().toString(),
                userId,
                clientId,
                Message.Status.SENT
            )
            when (val sentMessage = messageSender.sendClientDiscoveryMessage(message = message)) {
                is Either.Right -> {
                    callingLogger.i("OnHttpRequest() -> sendClientDiscoveryMessage() - Success: ${sentMessage.value}")
                    callingLogger.i(sentMessage.value)

                    /*

                    val clients = userClients.entries.flatMap { case (userId, clientIds) =>
                      clientIds.map { clientId =>
                        AvsClient(userId.str, clientId.str)
                      }
                    }

                    val json = encode(AvsClientList(clients.toSeq))
                    withAvs(wcall_set_clients_for_conv(wCall, convId.str, json))


                     */
                }
                is Either.Left -> {
                    callingLogger.e("OnHttpRequest() -> sendClientDiscoveryMessage() - Error: ${sentMessage.value}")
                }
            }
        }
    }

    private suspend fun sendCallingMessage(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
        data: String
    ): Either<CoreFailure, Unit> {
        val messageContent = MessageContent.Calling(data)
        val date = Clock.System.now().toString()
        val message = Message(uuid4().toString(), messageContent, conversationId, date, userId, clientId, Message.Status.SENT)
        return messageSender.sendMessage(message)
    }
}
