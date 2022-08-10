package com.wire.kalium.testservice.managed

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.testservice.models.Instance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import javax.ws.rs.WebApplicationException

class ConversationRepository {

    companion object {
        private val log = LoggerFactory.getLogger(ConversationRepository::class.java.name)

        fun deleteConversation(instance: Instance, conversationId: ConversationId, messageId: String, deleteForEveryone: Boolean) {
            instance.coreLogic?.globalScope {
                val result = session.currentSession()
                if (result is CurrentSessionResult.Success) {
                    instance.coreLogic.sessionScope(result.authSession.session.userId) {
                        log.info("Instance ${instance.instanceId}: Delete message everywhere")
                        runBlocking {
                            messages.deleteMessage(conversationId, messageId, deleteForEveryone)
                        }
                    }
                }
            }
        }

        fun sendTextMessage(instance: Instance, conversationId: ConversationId, text: String?) {
            instance.coreLogic?.globalScope {
                val result = session.currentSession()
                if (result is CurrentSessionResult.Success) {
                    instance.coreLogic.sessionScope(result.authSession.session.userId) {
                        text?.let {
                            log.info("Instance ${instance.instanceId}: Send text message '${text}'")
                            runBlocking {
                                messages.sendTextMessage(conversationId, text)
                            }
                        }
                    }
                }
            }
        }

        fun getMessages(instance: Instance, conversationId: ConversationId): List<Message> {
            instance.coreLogic?.globalScope {
                val result = session.currentSession()
                if (result is CurrentSessionResult.Success) {
                    instance.coreLogic.sessionScope(result.authSession.session.userId) {
                        val recentMessages = runBlocking {
                            log.info("Instance ${instance.instanceId}: Get recent messages...")
                            messages.getRecentMessages(conversationId).first()
                        }
                        return recentMessages
                    }
                }
            }
            throw WebApplicationException("Instance ${instance.instanceId}: Could not get recent messages")
        }
    }
}
