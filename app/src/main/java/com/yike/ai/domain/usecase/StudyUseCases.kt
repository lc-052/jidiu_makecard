package com.yike.ai.domain.usecase

import com.yike.ai.core.database.*
import com.yike.ai.data.repository.StudyRepository
import com.yike.ai.domain.model.codec
import java.time.*

class GetReviewQueue(private val repository: StudyRepository) {
    suspend operator fun invoke(
        deck: String?,
        newLimit: Int,
        reviewLimit: Int,
        now: Long = System.currentTimeMillis(),
    ): List<ReviewItem> {
        val zone = ZoneId.systemDefault()
        val start =
            Instant.ofEpochMilli(now)
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val today =
            repository.dao.allLogs().filter { it.reviewedAt >= start && it.reviewedAt <= now }
        val newToday =
            today.count { codec.decodeFromString<CardEntity>(it.previousState).state == "NEW" }
        val reviewed =
            today.count { codec.decodeFromString<CardEntity>(it.previousState).state == "REVIEW" }
        val due = repository.dao.due(now, deck)
        return due.filter { it.card.state in listOf("LEARNING", "RELEARNING") } +
            due.filter { it.card.state == "REVIEW" }
                .take((reviewLimit - reviewed).coerceAtLeast(0)) +
            due.filter { it.card.state == "NEW" }.take((newLimit - newToday).coerceAtLeast(0))
    }
}

data class StudyStats(
    val today: Int,
    val newToday: Int,
    val duration: Long,
    val streak: Int,
    val activity: Map<LocalDate, Int>,
)

fun studyStats(
    logs: List<ReviewLogEntity>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): StudyStats {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val valid = logs.filter { it.reviewedAt <= now }
    val activity =
        valid
            .groupingBy { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() }
            .eachCount()
    val current =
        valid.filter { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() == today }
    var day = if (activity.containsKey(today)) today else today.minusDays(1)
    var streak = 0
    while (activity.containsKey(day)) {
        streak++
        day = day.minusDays(1)
    }
    return StudyStats(
        current.size,
        current.count { codec.decodeFromString<CardEntity>(it.previousState).state == "NEW" },
        current.sumOf { it.elapsedTimeMs },
        streak,
        activity,
    )
}
