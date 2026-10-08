package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.logic.data.meeting.MeetingReminder
import com.wire.kalium.logic.data.meeting.MeetingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/** Reads locally stored meeting occurrences for scheduling reminder notifications. */
public interface MeetingRemindersUseCase {
    /** Emits when stored meetings or occurrences change. Requery [next] or [within] after each emission. */
    public fun changes(): Flow<Unit>

    /** Returns the earliest occurrence starting strictly after [from], or null when none exists. */
    public suspend fun next(from: Instant): MeetingReminder?

    /** Returns occurrences with start times at or after [startInclusive] and before [endExclusive], ordered by occurrence ID. */
    public suspend fun within(startInclusive: Instant, endExclusive: Instant): List<MeetingReminder>
}

internal class MeetingRemindersUseCaseImpl(
    private val repository: MeetingRepository,
) : MeetingRemindersUseCase {

    override fun changes(): Flow<Unit> = repository.observeMeetingReminderChanges()

    override suspend fun next(from: Instant): MeetingReminder? = repository.getNextMeetingReminder(from)

    override suspend fun within(startInclusive: Instant, endExclusive: Instant): List<MeetingReminder> =
        repository.getMeetingRemindersWithin(startInclusive, endExclusive)
}
