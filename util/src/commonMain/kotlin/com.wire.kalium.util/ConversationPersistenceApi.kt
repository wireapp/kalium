/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.util

/**
 * This API is internal to the conversation persistence layer.
 *
 * Use only from the following designated use-cases:
 * - [FetchConversationsUseCase]
 * - [FetchConversationUseCase]
 * - [PersistConversationsUseCase]
 * - [PersistConversationUseCase]
 * - [UpdateConversationProtocolUseCase]
 *
 * Any direct usage outside of these use-cases may result in inconsistencies
 * such as missing MLS metadata, incorrect member state, or bypassed validation logic.
 *
 * If you believe you need to use this API, consider using or extending one of the above use-cases,
 * or explicitly opt-in using `@OptIn(ConversationPersistenceApi::class)` and document your reason.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.TYPEALIAS
)
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal to the conversation persistence mechanism and must not be used outside of its designated scope."
)
annotation class ConversationPersistenceApi(
    val message: String = "",
    val replaceWith: ReplaceWith = ReplaceWith("")
)

