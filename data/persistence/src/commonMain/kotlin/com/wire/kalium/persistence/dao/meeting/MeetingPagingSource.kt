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

package com.wire.kalium.persistence.dao.meeting

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.wire.kalium.persistence.MeetingsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates

internal class MeetingPagingSource(
    private val meetingsQueries: MeetingsQueries,
    private val readContext: CoroutineContext,
    private val writeContext: CoroutineContext,
    private val parameters: MeetingPagingParameters,
) : PagingSource<Int, MeetingOccurrenceDetailsEntity>(), Query.Listener {

    private var currentMeetingQuery: Query<MeetingOccurrenceDetailsEntity>? by Delegates.observable(null) { _, old, new ->
        old?.removeListener(this)
        new?.addListener(this)
    }
    private var currentAvatarsQuery: Query<MeetingParticipantPreviewAssetEntity>? by Delegates.observable(null) { _, old, new ->
        old?.removeListener(this)
        new?.addListener(this)
    }
    override val jumpingSupported: Boolean get() = true

    init {
        registerInvalidatedCallback {
            currentMeetingQuery?.removeListener(this)
            currentMeetingQuery = null
            currentAvatarsQuery?.removeListener(this)
            currentAvatarsQuery = null
        }
    }

    override fun queryResultsChanged() = invalidate()

    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MeetingOccurrenceDetailsEntity> =
        withContext(readContext) {
            try {
                val key = params.key?.toLong() ?: parameters.initialOffset
                val limit = when (params) {
                    is LoadParams.Prepend<*> -> minOf(key, params.loadSize.toLong())
                    else -> params.loadSize.toLong()
                }
                val initialCount = countMeetingOccurrences()
                val offset = when (params) {
                    is LoadParams.Prepend<*> -> maxOf(0, key - params.loadSize).toInt()
                    is LoadParams.Append<*> -> key.toInt()
                    is LoadParams.Refresh<*> ->
                        if (key >= initialCount - params.loadSize) maxOf(0, initialCount - params.loadSize) else key.toInt()
                }
                val count = ensureOccurrencesForLoad(params, offset, limit, initialCount)
                val meetingQuery = meetingsQueries.selectPagedMeetingOccurrenceDetails(
                    fromDate = parameters.from,
                    untilDate = parameters.until,
                    limit = limit,
                    offset = offset.toLong(),
                    mapper = MeetingMapper::fromViewToDetails
                ).also { currentMeetingQuery = it }
                val meetings = meetingQuery.awaitAsList()
                val participantPreviewAssetIds = loadAndObserveAvatars(meetings)
                val data = meetings.map {
                    it.copy(participantPreviewAssetIds = participantPreviewAssetIds[it.meeting.conversationId].orEmpty())
                }
                val nextPosition = offset + data.size

                if (invalid) {
                    LoadResult.Invalid()
                } else {
                    LoadResult.Page(
                        data = data,
                        prevKey = offset.takeIf { it > 0 && data.isNotEmpty() },
                        nextKey = nextPosition.takeIf { data.isNotEmpty() && data.size >= limit && it < count },
                        itemsBefore = offset,
                        itemsAfter = maxOf(0, count - nextPosition),
                    )
                }
            } catch (exception: Exception) {
                LoadResult.Error(exception)
            }
        }

    override fun getRefreshKey(state: PagingState<Int, MeetingOccurrenceDetailsEntity>): Int? =
        state.anchorPosition?.let { maxOf(0, it - (state.config.initialLoadSize / 2)) }

    private suspend fun ensureOccurrencesForLoad(params: LoadParams<Int>, offset: Int, limit: Long, count: Int): Int {
        val targetCount = when {
            params is LoadParams.Prepend<*> -> count
            else -> (offset.toLong() + limit + parameters.prefetchDistance)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }

        if (targetCount <= count) return count

        currentMeetingQuery = null
        val generatedCount = withContext(writeContext) {
            meetingsQueries.transactionWithResult {
                val existingCount = countMeetingOccurrences()
                val missingCount = (targetCount - existingCount).coerceAtLeast(0)
                if (missingCount <= 0) return@transactionWithResult 0
                val bounds = parameters.until
                    ?.let { GenerationBounds.countUntil(totalCount = missingCount, until = it) }
                    ?: GenerationBounds.count(missingCount)
                val recurringMeetings = meetingsQueries.selectRecurringMeetings(MeetingMapper::fromViewToModel).awaitAsList()
                meetingsQueries.insertGeneratedOccurrences(
                    meetings = recurringMeetings,
                    bounds = bounds,
                    shouldRegenerateOccurrences = recurringMeetings.associate { it.meetingId to false },
                )
            }
        }
        return count + generatedCount
    }

    private suspend fun countMeetingOccurrences(): Int =
        meetingsQueries.countUpcomingMeetingOccurrences(fromDate = parameters.from, untilDate = parameters.until).awaitAsOne().toInt()

    private suspend fun loadAndObserveAvatars(meetings: List<MeetingOccurrenceDetailsEntity>): Map<QualifiedIDEntity, List<QualifiedIDEntity>> {
        val conversationIds = meetings.filter { meeting ->
            // Only load avatars for group meeting conversations, as only those have participant avatars to show
            meeting.conversationType == ConversationEntity.Type.GROUP && meeting.groupType is ConversationEntity.GroupType.Meeting
        }.mapTo(mutableSetOf()) { it.meeting.conversationId }

        if (conversationIds.isEmpty()) {
            currentAvatarsQuery = null
            return emptyMap()
        }

        val query = meetingsQueries.selectMeetingParticipantPreviewAssetIds(
            conversationIds = conversationIds.toList(),
            mapper = ::MeetingParticipantPreviewAssetEntity
        )
        currentAvatarsQuery = query
        return query.awaitAsList()
            .groupBy(keySelector = { it.conversationId }, valueTransform = { it.previewAssetId })
            .mapValues { (_, previewAssetIds) -> previewAssetIds.filterNotNull() }
    }
}

internal data class MeetingPagingParameters(val from: Instant, val until: Instant?, val initialOffset: Long, val prefetchDistance: Int)
