package com.wire.kalium.logic.feature.message

import com.wire.android.core.exception.Failure
import com.wire.android.core.functional.Either
import com.wire.android.core.functional.suspending
import com.wire.android.feature.contact.DetailedContact
import com.wire.android.feature.conversation.content.MessageRepository
import com.wire.android.feature.conversation.data.ConversationRepository
import com.wire.android.shared.prekey.PreKeyRepository
import com.wire.android.shared.prekey.data.UserPreKeyInfo
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.contact.DetailedContact
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

class OutgoingMessageRecipientsRetriever(
    private val preKeyRepository: PreKeyRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository
) {

    /**
     * Gets detailed contacts for sending a new message in a conversation.
     */
    suspend fun prepareRecipientsForNewOutgoingMessage(
        senderUserId: String,
        conversationId: String
    ): Either<AuthenticationResult.Failure, List<DetailedContact>> = suspending {
        conversationRepository.detailedConversationMembers(conversationId).flatMap { detailedContacts ->
            createSessionForMissingClientsIfNeeded(senderUserId, detailedContacts).map {
                detailedContacts
            }
        }
    }

    private suspend fun createSessionForMissingClientsIfNeeded(
        senderUserId: String,
        detailedContacts: List<DetailedContact>
    ): Either<AuthenticationResult.Failure, Unit> = suspending {
        missingContactClients(detailedContacts, senderUserId).flatMap { missingContactClients ->
            establishMissingSessions(senderUserId, missingContactClients)
        }
    }

    private suspend fun establishMissingSessions(
        senderUserId: String, missingContactClients: MutableMap<String, List<String>>
    ): Either<AuthenticationResult.Failure, Unit> = suspending {
        if (missingContactClients.isEmpty()) {
            return@suspending Either.Right(Unit)
        }
        preKeyRepository.preKeysOfClientsByUsers(missingContactClients).map { preKeyInfoList: List<UserPreKeyInfo> ->
            preKeyInfoList.forEach { userPreKeyInfo ->
                userPreKeyInfo.clientsInfo.foldToEitherWhileRight(Unit) { clientPreKeyInfo, _ ->
                    messageRepository.establishCryptoSession(
                        senderUserId,
                        userPreKeyInfo.userId,
                        clientPreKeyInfo.clientId,
                        clientPreKeyInfo.preKey
                    )
                }
            }
        }
    }

    private suspend fun missingClients(detailedContacts: List<DetailedContact>, senderUserId: String) =
        suspending {
            detailedContacts.foldToEitherWhileRight(mutableMapOf<String, List<String>>()) { detailedContact, userAccumulator ->
                missingClientsForContact(detailedContact, senderUserId).map { missingClients ->
                    if (missingClients.isNotEmpty()) {
                        userAccumulator[detailedContact.contact.id] = missingClients
                    }
                    userAccumulator
                }
            }
        }

    private suspend fun missingClientsForContact(detailedContact: DetailedContact, senderUserId: String) =
        suspending {
            detailedContact.clients.foldToEitherWhileRight(mutableListOf<String>()) { contact, clientIdAccumulator ->
                messageRepository.doesCryptoSessionExists(senderUserId, detailedContact.contact.id, contact.id)
                    .map { exists ->
                        if (!exists) {
                            clientIdAccumulator += contact.id
                        }
                        clientIdAccumulator
                    }
            }
        }
}
