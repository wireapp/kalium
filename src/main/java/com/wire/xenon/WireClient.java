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

package com.wire.xenon;

import com.wire.bots.cryptobox.CryptoException;
import com.wire.xenon.assets.IAsset;
import com.wire.xenon.assets.IGeneric;
import com.wire.xenon.backend.models.Conversation;
import com.wire.xenon.backend.models.User;
import com.wire.xenon.exceptions.HttpException;
import com.wire.xenon.models.AssetKey;
import com.wire.xenon.models.otr.PreKey;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Thread safe class for postings into this conversation
 */
public interface WireClient extends Closeable {

    /**
     * Post a generic message into conversation
     *
     * @param message generic message (Text, Image, File, Reply, Mention, ...)
     * @throws Exception
     */
    void send(IGeneric message) throws Exception;

    /**
     * @param message generic message (Text, Image, File, Reply, Mention, ...)
     * @param userId  ignore all other participants except this user
     * @throws Exception
     */
    void send(IGeneric message, UUID userId) throws Exception;

    /**
     * This method downloads asset from the Backend.
     *
     * @param assetKey        Unique asset identifier (UUID)
     * @param assetToken      Asset token (null in case of public assets)
     * @param sha256Challenge SHA256 hash code for this asset
     * @param otrKey          Encryption key to be used to decrypt the data
     * @return Decrypted asset data
     * @throws Exception
     */
    byte[] downloadAsset(String assetKey, String assetToken, byte[] sha256Challenge, byte[] otrKey) throws Exception;

    /**
     * @return Bot ID as UUID
     */
    UUID getId();

    /**
     * Fetch the bot's own user profile information. A bot's profile has the following attributes:
     * <p>
     * id (String): The bot's user ID.
     * name (String): The bot's name.
     * accent_id (Number): The bot's accent colour.
     * assets (Array): The bot's public profile assets (e.g. images).
     *
     * @return
     */
    User getSelf() throws HttpException;

    /**
     * @return Conversation ID as UUID
     */
    UUID getConversationId();

    /**
     * @return Device ID as returned by the Wire Backend
     */
    String getDeviceId();

    /**
     * Fetch users' profiles from the Backend
     *
     * @param userIds User IDs (UUID) that are being requested
     * @return Collection of user profiles (name, accent colour,...)
     * @throws HttpException
     */
    Collection<User> getUsers(Collection<UUID> userIds) throws HttpException;

    /**
     * Fetch users' profiles from the Backend
     *
     * @param userId User ID (UUID) that are being requested
     * @return User profile (name, accent colour,...)
     * @throws HttpException
     */
    User getUser(UUID userId) throws HttpException;

    /**
     * Fetch conversation details from the Backend
     *
     * @return Conversation details including Conversation ID, Conversation name, List of participants
     * @throws IOException
     */
    Conversation getConversation() throws IOException;

    /**
     * Bots cannot send/receive/accept connect requests. This method can be used when
     * running the sdk as a regular user and you need to
     * accept/reject a connect request.
     *
     * @param user User ID as UUID
     * @throws Exception
     */
    void acceptConnection(UUID user) throws Exception;

    /**
     * Decrypt cipher either using existing session or it creates new session from this cipher and decrypts
     *
     * @param userId   Sender's User id
     * @param clientId Sender's Client id
     * @param cypher   Encrypted, Base64 encoded string
     * @return Base64 encoded decrypted text
     * @throws CryptoException
     */
    String decrypt(UUID userId, String clientId, String cypher) throws CryptoException;

    /**
     * Invoked by the sdk. Called once when the conversation is created
     *
     * @return Last prekey
     * @throws CryptoException
     */
    PreKey newLastPreKey() throws CryptoException;

    /**
     * Invoked by the sdk. Called once when the conversation is created and then occasionally when number of available
     * keys drops too low
     *
     * @param from  Starting offset
     * @param count Number of keys to generate
     * @return List of prekeys
     * @throws CryptoException
     */
    ArrayList<PreKey> newPreKeys(int from, int count) throws CryptoException;

    /**
     * Uploads previously generated prekeys to BE
     *
     * @param preKeys Pre keys to be uploaded
     * @throws IOException
     */
    void uploadPreKeys(ArrayList<PreKey> preKeys) throws IOException;

    /**
     * Returns the list of available prekeys.
     * If the number is too low (less than 8) you should generate new prekeys and upload them to BE
     *
     * @return List of available prekeys' ids
     */
    ArrayList<Integer> getAvailablePrekeys();

    /**
     * Checks if CryptoBox is closed
     *
     * @return True if crypto box is closed
     */
    boolean isClosed();

    /**
     * Download publicly available profile picture for the given asset key. This asset is not encrypted
     *
     * @param assetKey Asset key
     * @return Profile picture binary data
     * @throws Exception
     */
    byte[] downloadProfilePicture(String assetKey) throws Exception;

    /**
     * Uploads assert to backend. This method is used in conjunction with sendPicture(IGeneric)
     *
     * @param asset Asset to be uploaded
     * @return Assert Key and Asset token in case of private assets
     * @throws Exception
     */
    AssetKey uploadAsset(IAsset asset) throws Exception;

    UUID getTeam() throws HttpException;

    Conversation createConversation(String name, UUID teamId, List<UUID> users) throws HttpException;

    Conversation createOne2One(UUID teamId, UUID userId) throws HttpException;

    void leaveConversation(UUID userId) throws HttpException;

    User addParticipants(UUID... userIds) throws HttpException;

    User addService(UUID serviceId, UUID providerId) throws HttpException;

    boolean deleteConversation(UUID teamId) throws HttpException;
}
