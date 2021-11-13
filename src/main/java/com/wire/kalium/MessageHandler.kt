//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//
package com.wire.kalium

//import com.wire.kalium.models.LinkPreviewMessage TODO: Implement LinkPreviews
import com.waz.model.Messages.GenericMessage
import com.wire.kalium.backend.models.NewBot
import com.wire.kalium.backend.models.SystemMessage
import com.wire.kalium.models.inbound.*
import com.wire.kalium.tools.Logger
import java.util.*

interface MessageHandler {
    /**
     * @param newBot       Initialization object for new Bot instance
     * -  id          : The unique user ID for the bot.
     * -  client      : The client ID for the bot.
     * -  origin      : The profile of the user who requested the bot, as it is returned from GET /bot/users.
     * -  conversation: The convId as seen by the bot and as returned from GET /bot/convId.
     * -  token       : The bearer token that the bot must use on inbound requests.
     * -  locale      : The preferred locale for the bot to use, in form of an IETF language tag.
     * @param serviceToken Service token obtained from the Wire BE when the service was created
     * @return If TRUE is returned new bot instance is created for this conversation
     * If FALSE is returned this service declines to create new bot instance for this conversation
     */
    fun onNewBot(newBot: NewBot, serviceToken: String): Boolean {
        return true
    }

    /**
     * This callback is invoked by the framework when the bot is added into a conversation
     *
     * @param client  Thread safe wire client that can be used to post back to this conversation
     * @param message SystemMessage object. message.conversation
     */
    fun onNewConversation(client: IWireClient, message: SystemMessage) {}

    /**
     * This callback is invoked by the framework every time connection request is received
     *
     * @param client Thread safe wire client that can be used to post back to this conversation
     * @param from   UserId of the connection request source user
     * @param to     UserId of the connection request destination user
     * @param status Relation status of the connection request
     * @return TRUE if connection was accepted
     */
    fun onConnectRequest(client: IWireClient, from: UUID, to: UUID, status: String): Boolean {
        // Bot received connect request and we want to accept it immediately
        if (status == "pending") {
            return try {
                client.acceptConnection(to)
                true
            } catch (e: Exception) {
                Logger.error("MessageHandlerBase:onConnectRequest: %s", e)
                false
            }
        }
        // Connect request sent by the bot got accepted
        return status == "accepted"
    }

    /**
     * This callback is invoked by the framework every time new participant joins this conversation
     *
     * @param client  Thread safe wire client that can be used to post back to this conversation
     * @param message System message object with message.users as List of UserIds that just joined this conversation
     */
    fun onMemberJoin(client: IWireClient, message: SystemMessage) {}

    /**
     * @param client  Thread safe wire client that can be used to post back to this conversation
     * @param message System message object with message.users as List of UserIds that just joined this conversation
     */
    fun onMemberLeave(client: IWireClient, message: SystemMessage) {}

    /**
     * This callback is called when this bot gets removed from the conversation
     *
     * @param botId Id of the Bot that got removed
     * @param msg   System message
     */
    fun onBotRemoved(botId: UUID, msg: SystemMessage) {}

    /**
     * @param newBot
     * @return Bot name that will be used for this conversation. If NULL is returned the Default Bot Name will be used
     */
    fun getName(newBot: NewBot): String? {
        return null
    }

    /**
     * @return Bot's Accent Colour index (from [1 - 7]) that will be used for this conversation. If 0 is returned the
     * default one will be used
     */
    fun getAccentColour(): Int {
        return 0
    }

    /**
     * @return Asset key for the small profile picture. If NULL is returned the default key will be used
     */
    fun getSmallProfilePicture(): String? {
        return null
    }

    /**
     * @return Asset key for the big profile picture. If NULL is returned the default key will be used
     */
    fun getBigProfilePicture(): String? {
        return null
    }

    /**
     * This method is called when a text is posted into the conversation
     *
     * @param client Thread safe wire client that can be used to post back to this conversation
     * @param msg    Message containing text
     */
    fun onText(client: IWireClient, msg: TextMessage) {}

    /**
     * This is generic method that is called every time something is posted to this conversation.
     *
     * @param client         Thread safe wire client that can be used to post back to this conversation
     * @param userId         User Id for the sender
     * @param genericMessage Generic message as it comes from the BE
     */
    fun onEvent(client: IWireClient, userId: UUID, genericMessage: GenericMessage) {}

    /**
     * Called when user edits previously sent message
     *
     * @param client Thread safe wire client that can be used to post back to this conversation
     * @param msg    New Message containing replacing messageId
     */
    fun onEditText(client: IWireClient, msg: EditedTextMessage) {}
    fun onCalling(client: IWireClient, msg: CallingMessage) {}
    fun onConversationRename(client: IWireClient, systemMessage: SystemMessage) {}
    fun onDelete(client: IWireClient, msg: DeletedTextMessage) {}
    fun onReaction(client: IWireClient, msg: ReactionMessage) {}
    fun onNewTeamMember(userClient: IWireClient, userId: UUID) {}
    fun onUserUpdate(id: UUID, userId: UUID) {}

    //    open fun onLinkPreview(client: WireClient, msg: LinkPreviewMessage) {} TODO: Implement LinkPreviews
    fun onPing(client: IWireClient, msg: PingMessage) {}

    /**
     * This method is called when ephemeral text is posted into the conversation
     *
     * @param client Thread safe wire client that can be used to post back to this conversation
     * @param msg    Message containing text and expiration time
     */
    fun onText(client: IWireClient, msg: EphemeralTextMessage) {}
    fun onConfirmation(client: IWireClient, msg: ConfirmationMessage) {}
    fun validatePreKeys(client: IWireClient, size: Int) {
        try {
            val minAvailable = 8 * size
            if (minAvailable > 0) {
                val availablePrekeys = client.getAvailablePrekeys()
                availablePrekeys.remove(65535) //remove the last prekey
                if (availablePrekeys.isNotEmpty() && availablePrekeys.size < minAvailable) {
                    val lastKeyOffset = Collections.max(availablePrekeys)
                    val keys = client.newPreKeys(lastKeyOffset!! + 1, minAvailable)
                    client.uploadPreKeys(keys)
                    Logger.info("Uploaded " + keys.size + " prekeys")
                }
            }
        } catch (e: Exception) {
            Logger.error("validatePreKeys: bot: %s %s", client.getUserId(), e)
        }
    }

    fun onPhotoPreview(client: IWireClient, msg: PhotoPreviewMessage) {}
    fun onAssetData(client: IWireClient, msg: RemoteMessage) {}
    fun onFilePreview(client: IWireClient, msg: FilePreviewMessage) {}
    open fun onAudioPreview(client: IWireClient, msg: AudioPreviewMessage) {}
    fun onVideoPreview(client: IWireClient, msg: VideoPreviewMessage) {}
}
