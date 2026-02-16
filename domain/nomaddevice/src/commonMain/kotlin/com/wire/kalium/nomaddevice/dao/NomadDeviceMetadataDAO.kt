/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.nomaddevice.dao

import com.wire.kalium.persistence.dao.MetadataDAO

public const val DEFAULT_NOMAD_DEVICE_KEY: String = "nomad_device_identifier"

public interface NomadDeviceMetadataDAO {
    public suspend fun putDeviceIdentifier(deviceIdentifier: String)
    public suspend fun getDeviceIdentifier(): String?
    public suspend fun clearDeviceIdentifier()
}

public class NomadDeviceMetadataDAOImpl(
    private val metadataDAO: MetadataDAO,
    private val key: String = DEFAULT_NOMAD_DEVICE_KEY,
) : NomadDeviceMetadataDAO {

    override suspend fun putDeviceIdentifier(deviceIdentifier: String) {
        metadataDAO.insertValue(value = deviceIdentifier, key = key)
    }

    override suspend fun getDeviceIdentifier(): String? = metadataDAO.valueByKey(key)

    override suspend fun clearDeviceIdentifier() {
        metadataDAO.deleteValue(key)
    }
}

public object NomadDeviceMetadataDAOFactory {
    public fun fromMetadata(metadataDAO: MetadataDAO): NomadDeviceMetadataDAO =
        NomadDeviceMetadataDAOImpl(metadataDAO = metadataDAO)
}
