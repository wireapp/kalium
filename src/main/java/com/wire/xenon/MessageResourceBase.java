package com.wire.xenon;

import com.google.protobuf.InvalidProtocolBufferException;
import com.waz.model.Messages;
import com.wire.bots.cryptobox.CryptoException;
import com.wire.xenon.backend.GenericMessageProcessor;
import com.wire.xenon.backend.models.Conversation;
import com.wire.xenon.backend.models.Member;
import com.wire.xenon.backend.models.Payload;
import com.wire.xenon.backend.models.SystemMessage;
import com.wire.xenon.models.MessageBase;
import com.wire.xenon.tools.Logger;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

public abstract class MessageResourceBase {
    protected final MessageHandlerBase handler;

    public MessageResourceBase(MessageHandlerBase handler) {
        this.handler = handler;
    }

    protected void handleMessage(UUID eventId, Payload payload, WireClient client) throws Exception {
        Payload.Data data = payload.data;
        UUID botId = client.getId();

        switch (payload.type) {
            case "conversation.otr-message-add":
                UUID from = payload.from;

                Logger.debug("conversation.otr-message-add: bot: %s from: %s:%s", botId, from, data.sender);

                GenericMessageProcessor processor = new GenericMessageProcessor(client, handler);

                Messages.GenericMessage genericMessage = decrypt(client, payload);

                final UUID messageId = UUID.fromString(genericMessage.getMessageId());
                MessageBase msgBase = new MessageBase(eventId, messageId, payload.convId, data.sender, from, payload.time);

                processor.process(msgBase, genericMessage);

                handler.onEvent(client, from, genericMessage);
                break;
            case "conversation.member-join":
                Logger.debug("conversation.member-join: bot: %s", botId);

                // Check if this bot got added to the conversation
                List<UUID> participants = data.userIds;
                if (participants.remove(botId)) {
                    SystemMessage systemMessage = getSystemMessage(eventId, payload);
                    systemMessage.conversation = client.getConversation();
                    systemMessage.type = "conversation.create"; //hack the type

                    handler.onNewConversation(client, systemMessage);
                    return;
                }

                // Check if we still have some prekeys available. Upload new prekeys if needed
                handler.validatePreKeys(client, participants.size());

                SystemMessage systemMessage = getSystemMessage(eventId, payload);
                systemMessage.users = data.userIds;

                handler.onMemberJoin(client, systemMessage);
                break;
            case "conversation.member-leave":
                Logger.debug("conversation.member-leave: bot: %s", botId);

                systemMessage = getSystemMessage(eventId, payload);
                systemMessage.users = data.userIds;

                // Check if this bot got removed from the conversation
                participants = data.userIds;
                if (participants.remove(botId)) {
                    handler.onBotRemoved(botId, systemMessage);
                    return;
                }

                if (!participants.isEmpty()) {
                    handler.onMemberLeave(client, systemMessage);
                }
                break;
            case "conversation.delete":
                Logger.debug("conversation.delete: bot: %s", botId);
                systemMessage = getSystemMessage(eventId, payload);

                // Cleanup
                handler.onBotRemoved(botId, systemMessage);
                break;
            case "conversation.create":
                Logger.debug("conversation.create: bot: %s", botId);

                systemMessage = getSystemMessage(eventId, payload);
                if (systemMessage.conversation.members != null) {
                    Member self = new Member();
                    self.id = botId;
                    systemMessage.conversation.members.add(self);
                }

                handler.onNewConversation(client, systemMessage);
                break;
            case "conversation.rename":
                Logger.debug("conversation.rename: bot: %s", botId);

                systemMessage = getSystemMessage(eventId, payload);

                handler.onConversationRename(client, systemMessage);
                break;
            case "user.connection":
                Payload.Connection connection = payload.connection;
                Logger.debug("user.connection: bot: %s, from: %s to: %s status: %s",
                        botId,
                        connection.from,
                        connection.to,
                        connection.status);

                boolean accepted = handler.onConnectRequest(client, connection.from, connection.to, connection.status);
                if (accepted) {
                    Conversation conversation = new Conversation();
                    conversation.id = connection.convId;
                    systemMessage = new SystemMessage();
                    systemMessage.id = eventId;
                    systemMessage.from = connection.from;
                    systemMessage.type = payload.type;
                    systemMessage.conversation = conversation;

                    handler.onNewConversation(client, systemMessage);
                }
                break;
            default:
                Logger.debug("Unknown event: %s", payload.type);
        }
    }

    private SystemMessage getSystemMessage(UUID eventId, Payload payload) {
        SystemMessage systemMessage = new SystemMessage();
        systemMessage.id = eventId;
        systemMessage.from = payload.from;
        systemMessage.time = payload.time;
        systemMessage.type = payload.type;
        systemMessage.convId = payload.convId;

        systemMessage.conversation = new Conversation();
        systemMessage.conversation.id = payload.convId;
        systemMessage.conversation.creator = payload.data.creator;
        systemMessage.conversation.name = payload.data.name;
        if (payload.data.members != null)
            systemMessage.conversation.members = payload.data.members.others;

        return systemMessage;
    }

    private Messages.GenericMessage decrypt(WireClient client, Payload payload)
            throws CryptoException, InvalidProtocolBufferException {
        UUID from = payload.from;
        String sender = payload.data.sender;
        String cipher = payload.data.text;

        String encoded = client.decrypt(from, sender, cipher);
        byte[] decoded = Base64.getDecoder().decode(encoded);
        return Messages.GenericMessage.parseFrom(decoded);
    }
}
