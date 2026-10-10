package com.yike.ai.core.fsrs

import com.yike.ai.core.database.CardEntity
import kotlin.math.*

/**
 * FSRS-6, translated from OSR py-fsrs scheduler.py. See licenses and reference-vector tests. Fuzz
 * disabled. UTC millis; elapsed time clamps at zero on backward clock movement.
 */
class FsrsScheduler(private val retention: Double = 0.9) {
    init {
        require(retention in 0.7..0.99)
    }

    private val w =
        doubleArrayOf(
            0.212,
            1.2931,
            2.3065,
            8.2956,
            6.4133,
            0.8334,
            3.0194,
            0.001,
            1.8722,
            0.1666,
            0.796,
            1.4835,
            0.0614,
            0.2629,
            1.6483,
            0.6014,
            1.8729,
            0.5425,
            0.0912,
            0.0658,
            0.1542,
        )
    private val decay = -w[20]
    private val factor = 0.9.pow(1 / decay) - 1

    fun retrievability(elapsed: Double, s: Double) =
        (1 + factor * elapsed.coerceAtLeast(0.0) / s).pow(decay)

    private fun initialD(r: Int) = w[4] - exp(w[5] * (r - 1)) + 1

    private fun nextD(d: Double, r: Int) =
        (w[7] * initialD(4) + (1 - w[7]) * (d + (10 - d) * (-w[6] * (r - 3)) / 9)).coerceIn(
            1.0,
            10.0,
        )

    private fun interval(s: Double) =
        Math.rint(s / factor * (retention.pow(1 / decay) - 1)).toInt().coerceIn(1, 36500)

    fun schedule(c: CardEntity, rating: Int, now: Long): CardEntity {
        require(rating in 1..4)
        val elapsed = ((now - (c.lastReviewAt ?: now)).coerceAtLeast(0) / DAY).toDouble()
        val s =
            when {
                c.lastReviewAt == null -> w[rating - 1]
                elapsed < 1 -> {
                    var growth = exp(w[17] * (rating - 3 + w[18])) * c.stability.pow(-w[19])
                    if (rating >= 3) growth = max(growth, 1.0)
                    c.stability * growth
                }
                rating == 1 ->
                    min(
                        w[11] *
                            c.difficulty.pow(-w[12]) *
                            ((c.stability + 1).pow(w[13]) - 1) *
                            exp((1 - retrievability(elapsed, c.stability)) * w[14]),
                        c.stability / exp(w[17] * w[18]),
                    )
                else ->
                    c.stability *
                        (1 +
                            exp(w[8]) *
                                (11 - c.difficulty) *
                                c.stability.pow(-w[9]) *
                                (exp((1 - retrievability(elapsed, c.stability)) * w[10]) - 1) *
                                (if (rating == 2) w[15] else 1.0) *
                                (if (rating == 4) w[16] else 1.0))
            }.coerceAtLeast(0.001)
        val d =
            if (c.lastReviewAt == null) initialD(rating).coerceIn(1.0, 10.0)
            else nextD(c.difficulty, rating)
        var state = if (c.state == "NEW") "LEARNING" else c.state
        var step = c.step
        val delay: Long
        if (state == "REVIEW") {
            if (rating == 1) {
                state = "RELEARNING"
                step = 0
                delay = 600_000
            } else {
                delay = interval(s) * DAY
            }
        } else {
            val steps =
                if (state == "RELEARNING") longArrayOf(600_000) else longArrayOf(60_000, 600_000)
            when (rating) {
                1 -> {
                    step = 0
                    delay = steps[0]
                }
                2 -> {
                    delay =
                        if (step == 0)
                            if (steps.size == 1) steps[0] * 3 / 2 else (steps[0] + steps[1]) / 2
                        else steps[step.coerceAtMost(steps.lastIndex)]
                }
                3 -> {
                    if (step + 1 >= steps.size) {
                        state = "REVIEW"
                        step = 0
                        delay = interval(s) * DAY
                    } else {
                        step++
                        delay = steps[step]
                    }
                }
                else -> {
                    state = "REVIEW"
                    step = 0
                    delay = interval(s) * DAY
                }
            }
        }
        return c.copy(
            state = state,
            step = step,
            stability = s,
            difficulty = d,
            elapsedDays = elapsed,
            scheduledDays = delay / DAY.toDouble(),
            dueAt = now + delay,
            lastReviewAt = now,
            reps = c.reps + 1,
            lapses = c.lapses + if (rating == 1 && c.state == "REVIEW") 1 else 0,
        )
    }

    companion object {
        const val DAY = 86_400_000L
    }
}
