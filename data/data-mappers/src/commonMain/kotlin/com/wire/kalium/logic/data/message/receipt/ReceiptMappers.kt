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

package com.wire.kalium.logic.data.message.receipt

import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public fun ReceiptType.toEntity(): ReceiptTypeEntity = when (this) {
    ReceiptType.READ -> ReceiptTypeEntity.READ
    ReceiptType.DELIVERED -> ReceiptTypeEntity.DELIVERY
}

@InternalKaliumApi
public fun ReceiptType.toMessageStatus(): MessageEntity.Status = when (this) {
    ReceiptType.READ -> MessageEntity.Status.READ
    ReceiptType.DELIVERED -> MessageEntity.Status.DELIVERED
}
