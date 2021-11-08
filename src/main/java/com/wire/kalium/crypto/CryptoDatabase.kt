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
package com.wire.kalium.crypto

import kotlin.Throws
import java.io.IOException
import java.util.UUID
import com.wire.bots.cryptobox.IStorage
import com.wire.bots.cryptobox.CryptoException
import com.wire.bots.cryptobox.ICryptobox
import com.wire.bots.cryptobox.CryptoDb

/**
 * Wrapper for the Crypto Box. This class is thread safe.
 */
class CryptoDatabase : CryptoBase {
    private val box: CryptoDb? = null

    /**
     *
     *
     * Opens the CryptoBox using given directory path
     * The given directory must exist and be writable.
     *
     * Note: Do not create multiple OtrManagers that operate on the same or
     * overlapping directories. Doing so results in undefined behaviour.
     *
     * @param botId   Bot id
     * @param storage Instance of a IStorage class
     */
    constructor(botId: UUID?, storage: IStorage?) {
        box = try {
            CryptoDb(botId.toString(), storage)
        } catch (e: IOException) {
            throw CryptoException(e)
        }
    }

    constructor(botId: UUID?, storage: IStorage?, dir: String?) {
        box = try {
            CryptoDb(botId.toString(), storage, dir)
        } catch (e: IOException) {
            throw CryptoException(e)
        }
    }

    override fun box(): ICryptobox? {
        return box
    }

    @Throws(IOException::class)
    override fun purge() {
        box.purge()
    }
}
