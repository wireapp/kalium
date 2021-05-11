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

package com.wire.xenon.backend;

import com.waz.model.Messages;
import com.wire.xenon.MessageHandlerBase;
import com.wire.xenon.WireClient;
import com.wire.xenon.models.*;
import com.wire.xenon.tools.Logger;

import java.util.UUID;

public class GenericMessageProcessor {
    private final WireClient client;
    private final MessageHandlerBase handler;

    public GenericMessageProcessor(WireClient client, MessageHandlerBase handler) {
        this.client = client;
        this.handler = handler;
    }

    public boolean process(MessageBase msgBase, Messages.GenericMessage generic) {
        Logger.debug("eventId: %s, msgId: %s, proto: %s",
                msgBase.getEventId(),
                msgBase.getMessageId(),
                generic);

        // Text
        if (generic.hasText()) {
            Messages.Text text = generic.getText();

            if (!text.getLinkPreviewList().isEmpty()) {
                return handleLinkPreview(text, new LinkPreviewMessage(msgBase));
            }

            if (text.hasContent()) {
                TextMessage msg = fromText(new TextMessage(msgBase), text);

                handler.onText(client, msg);
                return true;
            }
        }

        // Ephemeral messages
        if (generic.hasEphemeral()) {
            Messages.Ephemeral ephemeral = generic.getEphemeral();

            if (ephemeral.hasText()) {
                final Messages.Text text = ephemeral.getText();

                if (text.hasContent()) {
                    final EphemeralTextMessage msg = new EphemeralTextMessage(msgBase);
                    fromText(msg, text);

                    msg.setExpireAfterMillis(ephemeral.getExpireAfterMillis());

                    handler.onText(client, msg);
                    return true;
                }
            }

            if (ephemeral.hasAsset()) {
                return handleAsset(msgBase, ephemeral.getAsset());
            }
        }

        // Edit message
        if (generic.hasEdited()) {
            Messages.MessageEdit edited = generic.getEdited();

            if (edited.hasText()) {
                final Messages.Text text = edited.getText();

                if (text.hasContent()) {
                    final EditedTextMessage msg = new EditedTextMessage(msgBase);
                    fromText(msg, text);

                    final UUID replacingMessageId = UUID.fromString(edited.getReplacingMessageId());
                    msg.setReplacingMessageId(replacingMessageId);

                    handler.onEditText(client, msg);
                    return true;
                }
            }
        }

        if (generic.hasConfirmation()) {
            Messages.Confirmation confirmation = generic.getConfirmation();
            ConfirmationMessage msg = new ConfirmationMessage(msgBase);

            return handleConfirmation(confirmation, msg);
        }

        if (generic.hasCalling()) {
            Messages.Calling calling = generic.getCalling();
            if (calling.hasContent()) {
                CallingMessage message = new CallingMessage(msgBase);
                message.setContent(calling.getContent());

                handler.onCalling(client, message);
            }
            return true;
        }

        if (generic.hasDeleted()) {
            DeletedTextMessage msg = new DeletedTextMessage(msgBase);
            UUID delMsgId = UUID.fromString(generic.getDeleted().getMessageId());
            msg.setDeletedMessageId(delMsgId);

            handler.onDelete(client, msg);
            return true;
        }

        if (generic.hasReaction()) {
            Messages.Reaction reaction = generic.getReaction();
            ReactionMessage msg = new ReactionMessage(msgBase);

            return handleReaction(reaction, msg);
        }

        if (generic.hasKnock()) {
            PingMessage msg = new PingMessage(msgBase);

            handler.onPing(client, msg);
            return true;
        }

        if (generic.hasAsset()) {
            return handleAsset(msgBase, generic.getAsset());
        }

        return false;
    }

    private TextMessage fromText(TextMessage textMessage, Messages.Text text) {
        textMessage.setText(text.getContent());

        if (text.hasQuote()) {
            final String quotedMessageId = text.getQuote().getQuotedMessageId();
            textMessage.setQuotedMessageId(UUID.fromString(quotedMessageId));
        }
        for (Messages.Mention mention : text.getMentionsList())
            textMessage.addMention(mention.getUserId(), mention.getStart(), mention.getLength());
        return textMessage;
    }

    private boolean handleAsset(MessageBase msgBase, Messages.Asset asset) {
        if (asset.hasOriginal()) {
            final Messages.Asset.Original original = asset.getOriginal();
            if (original.hasImage()) {
                handler.onPhotoPreview(client, new PhotoPreviewMessage(msgBase, original));
            } else if (original.hasAudio()) {
                handler.onAudioPreview(client, new AudioPreviewMessage(msgBase, original));
            } else if (original.hasVideo()) {
                handler.onVideoPreview(client, new VideoPreviewMessage(msgBase, original));
            } else {
                handler.onFilePreview(client, new FilePreviewMessage(msgBase, original));
            }
        }

        if (asset.hasUploaded()) {
            handler.onAssetData(client, new RemoteMessage(msgBase, asset.getUploaded()));
        }

        return true;
    }

    private boolean handleConfirmation(Messages.Confirmation confirmation, ConfirmationMessage msg) {
        String firstMessageId = confirmation.getFirstMessageId();
        Messages.Confirmation.Type type = confirmation.getType();

        msg.setConfirmationMessageId(UUID.fromString(firstMessageId));
        msg.setType(type.getNumber() == Messages.Confirmation.Type.DELIVERED_VALUE
                ? ConfirmationMessage.Type.DELIVERED
                : ConfirmationMessage.Type.READ);

        handler.onConfirmation(client, msg);
        return true;
    }

    private boolean handleLinkPreview(Messages.Text text, LinkPreviewMessage msg) {
        for (Messages.LinkPreview link : text.getLinkPreviewList()) {

            if (text.hasContent()) {
                Messages.Asset image = link.getImage();

                msg.fromOrigin(image.getOriginal());
                msg.fromRemote(image.getUploaded());

                final Messages.Asset.ImageMetaData imageMetaData = image.getOriginal().getImage();
                msg.setHeight(imageMetaData.getHeight());
                msg.setWidth(imageMetaData.getWidth());

                msg.setSummary(link.getSummary());
                msg.setTitle(link.getTitle());
                msg.setUrl(link.getUrl());
                msg.setUrlOffset(link.getUrlOffset());

                msg.setText(text.getContent());
                handler.onLinkPreview(client, msg);
            }
        }
        return true;
    }

    private boolean handleReaction(Messages.Reaction reaction, ReactionMessage msg) {
        if (reaction.hasEmoji()) {
            msg.setEmoji(reaction.getEmoji());
            msg.setReactionMessageId(UUID.fromString(reaction.getMessageId()));

            handler.onReaction(client, msg);
        }
        return true;
    }
}
