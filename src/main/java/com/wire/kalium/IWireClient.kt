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

import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.models.backend.Conversation
import com.wire.kalium.models.backend.User
import com.wire.kalium.models.inbound.AssetKey
import com.wire.kalium.models.outbound.Asset
import com.wire.kalium.models.outbound.GenericMessageIdentifiable
import com.wire.kalium.models.outbound.otr.PreKey
import java.io.Closeable
import java.io.IOException
import java.util.UUID

/**
 * Thread safe class for postings into this conversation
 */
interface IWireClient : Closeable {
    /**
     * Post a generic message into conversation
     *
     * @param message generic message (Text, Image, File, Reply, Mention, ...)
     * @throws Exception
     */
    @Throws(Exception::class)
    fun send(message: GenericMessageIdentifiable)

    /**
     * @param message generic message (Text, Image, File, Reply, Mention, ...)
     * @param userId  ignore all other participants except this user
     * @throws Exception
     */
    @Throws(Exception::class)
    fun send(message: GenericMessageIdentifiable, userId: UUID)

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
    @Throws(Exception::class)
    fun downloadAsset(assetKey: String, assetToken: String, sha256Challenge: ByteArray, otrKey: ByteArray): ByteArray

    /**
     * @return Bot ID as UUID
     */
    fun getUserId(): UUID

    /**
     * Fetch the bot's own user profile information. A bot's profile has the following attributes:
     *
     *
     * id (String): The bot's user ID.
     * name (String): The bot's name.
     * accent_id (Number): The bot's accent colour.
     * assets (Array): The bot's public profile assets (e.g. images).
     *
     * @return
     */
    @Throws(HttpException::class)
    fun getSelf(): User

    /**
     * Fetch users' profiles from the Backend
     *
     * @param userIds User IDs (UUID) that are being requested
     * @return Collection of user profiles (name, accent colour,...)
     * @throws HttpException
     */
    @Throws(HttpException::class)
    fun getUsers(userIds: MutableCollection<UUID>): MutableCollection<User>

    /**
     * Fetch users' profiles from the Backend
     *
     * @param userId User ID (UUID) that are being requested
     * @return User profile (name, accent colour,...)
     * @throws HttpException
     */
    @Throws(HttpException::class)
    fun getUser(userId: UUID): User

    /**
     * Fetch conversation details from the Backend
     *
     * @return Conversation details including Conversation ID, Conversation name, List of participants
     * @throws IOException
     */
    @Throws(IOException::class)
    fun getConversation(): Conversation

    /**
     * Bots cannot send/receive/accept connect requests. This method can be used when
     * running the sdk as a regular user and you need to
     * accept/reject a connect request.
     *
     * @param user User ID as UUID
     * @throws Exception
     */
    @Throws(Exception::class)
    fun acceptConnection(user: UUID)

    /**
     * Decrypt cipher either using existing session or it creates new session from this cipher and decrypts
     *
     * @param userId   Sender's User id
     * @param clientId Sender's Client id
     * @param cypher   Encrypted, Base64 encoded string
     * @return Base64 encoded decrypted text
     * @throws CryptoException
     */
    @Throws(CryptoException::class)
    fun decrypt(userId: UUID, clientId: String, cypher: String): String

    /**
     * Invoked by the sdk. Called once when the conversation is created
     *
     * @return Last prekey
     * @throws CryptoException
     */
    @Throws(CryptoException::class)
    fun newLastPreKey(): PreKey

    /**
     * Invoked by the sdk. Called once when the conversation is created and then occasionally when number of available
     * keys drops too low
     *
     * @param from  Starting offset
     * @param count Number of keys to generate
     * @return List of prekeys
     * @throws CryptoException
     */
    @Throws(CryptoException::class)
    fun newPreKeys(from: Int, count: Int): ArrayList<PreKey>

    /**
     * Uploads previously generated prekeys to BE
     *
     * @param preKeys Pre keys to be uploaded
     * @throws IOException
     */
    @Throws(IOException::class)
    fun uploadPreKeys(preKeys: ArrayList<PreKey>)

    /**
     * Returns the list of available prekeys.
     * If the number is too low (less than 8) you should generate new prekeys and upload them to BE
     *
     * @return List of available prekeys' ids
     */
    fun getAvailablePrekeys(): ArrayList<Int>

    /**
     * Checks if CryptoBox is closed
     *
     * @return True if crypto box is closed
     */
    fun isClosed(): Boolean

    /**
     * Download publicly available profile picture for the given asset key. This asset is not encrypted
     *
     * @param assetKey Asset key
     * @return Profile picture binary data
     * @throws Exception
     */
    @Throws(Exception::class)
    fun downloadProfilePicture(assetKey: String): ByteArray

    /**
     * Uploads assert to backend. This method is used in conjunction with sendPicture(IGeneric)
     *
     * @param asset Asset to be uploaded
     * @return Assert Key and Asset token in case of private assets
     * @throws Exception
     */
    @Throws(Exception::class)
    fun uploadAsset(asset: Asset): AssetKey

    @Throws(HttpException::class)
    fun getTeam(): UUID?

    @Throws(HttpException::class)
    fun createConversation(name: String, teamId: UUID, users: MutableList<UUID>): Conversation

    @Throws(HttpException::class)
    fun createOne2One(teamId: UUID, userId: UUID): Conversation

    @Throws(HttpException::class)
    fun leaveConversation(userId: UUID)

    @Throws(HttpException::class)
    fun addParticipants(vararg userIds: UUID): User

    @Throws(HttpException::class)
    fun addService(serviceId: UUID, providerId: UUID): User

    @Throws(HttpException::class)
    fun deleteConversation(teamId: UUID): Boolean

    @Throws(HttpException::class)
    fun getUserId(username: String): UUID
}
