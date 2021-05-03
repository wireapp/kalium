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

package com.wire.xenon.assets;

import com.google.protobuf.ByteString;
import com.waz.model.Messages;
import com.wire.xenon.tools.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class Picture extends AssetBase {
    private byte[] imageData;
    private int width;
    private int height;
    private int size;
    private long expires;

    public Picture(byte[] imageData, String mime) throws Exception {
        super(UUID.randomUUID(), mime, imageData);
        this.imageData = imageData;
        size = imageData.length;
        BufferedImage bufferedImage = loadBufferImage(imageData);
        width = bufferedImage.getWidth();
        height = bufferedImage.getHeight();
    }

    public Picture(byte[] imageData) throws Exception {
        super(UUID.randomUUID(), Util.extractMimeType(imageData), imageData);

        this.imageData = imageData;
        size = imageData.length;
        BufferedImage bufferedImage = loadBufferImage(imageData);
        width = bufferedImage.getWidth();
        height = bufferedImage.getHeight();
    }

    public Picture(UUID messageId, String mimeType) {
        super(messageId, mimeType);
    }

    @Override
    public Messages.GenericMessage createGenericMsg() {
        Messages.GenericMessage.Builder ret = Messages.GenericMessage.newBuilder()
                .setMessageId(getMessageId().toString());

        Messages.Asset.ImageMetaData.Builder metaData = Messages.Asset.ImageMetaData.newBuilder()
                .setHeight(height)
                .setWidth(width)
                .setTag("medium");

        Messages.Asset.Original.Builder original = Messages.Asset.Original.newBuilder()
                .setSize(size)
                .setMimeType(mimeType)
                .setImage(metaData);

        Messages.Asset.RemoteData.Builder remoteData = Messages.Asset.RemoteData.newBuilder()
                .setOtrKey(ByteString.copyFrom(getOtrKey()))
                .setSha256(ByteString.copyFrom(getSha256()));

        if (getAssetToken() != null) {
            remoteData.setAssetToken(getAssetToken());
        }

        if (getAssetKey() != null) {
            remoteData.setAssetId(getAssetKey());
        }

        Messages.Asset.Builder asset = Messages.Asset.newBuilder()
                .setExpectsReadConfirmation(isReadReceiptsEnabled())
                .setUploaded(remoteData)
                .setOriginal(original);

        if (expires > 0) {
            Messages.Ephemeral.Builder ephemeral = Messages.Ephemeral.newBuilder()
                    .setAsset(asset)
                    .setExpireAfterMillis(expires);

            return ret
                    .setEphemeral(ephemeral)
                    .build();
        }
        return ret
                .setAsset(asset)
                .build();
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getExpires() {
        return expires;
    }

    public void setExpires(long expires) {
        this.expires = expires;
    }

    private BufferedImage loadBufferImage(byte[] imageData) throws IOException {
        try (InputStream input = new ByteArrayInputStream(imageData)) {
            return ImageIO.read(input);
        }
    }
}
