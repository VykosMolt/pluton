package io.github.vykosmolt.pluton.core

import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

// FSRS-6 defaults from ts-fsrs 5.4.2, ported to pure Kotlin so Android and iOS schedule identically.
// The web app enables fuzz; native scheduling is deterministic for now. Tests in FsrsParityTest compare against
// fixtures generated from ts-fsrs with enable_fuzz=false. Add fuzz behind a seeded PRNG if due-date variety matters.

@Serializable
enum class State { New, Learning, Review, Relearning }
enum class Rating(val value: Int) { Again(1), Hard(2), Good(3), Easy(4) }

@Serializable
data class Card(
    val due: Long = 0,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val learningSteps: Int = 0,
    val state: State = State.New,
    val lastReview: Long? = null,
)

fun createEmptyCard(now: Long): Card = Card(due = now)

private const val S_MIN = 1e-3
private const val S_MAX = 36500.0
private const val DAY_MS = 86_400_000L
private const val MINUTE_MS = 60_000L

private val DEFAULT_W = doubleArrayOf(
    0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 1e-3, 1.8722, 0.1666, 0.796,
    1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
)

class Scheduler(
    requestRetention: Double = 0.9,
    private val maximumInterval: Int = 36500,
    private val learningSteps: List<Int> = listOf(1, 10),
    private val relearningSteps: List<Int> = listOf(10),
) {
    private val w = DEFAULT_W
    private val decay = -w[20]
    private val factor = roundTo(exp((1 / decay) * ln(0.9)) - 1, 8)
    private val intervalModifier = roundTo((requestRetention.pow(1 / decay) - 1) / factor, 8)

    /** Schedule `card` after `rating` at `now` (epoch millis). */
    fun next(card: Card, now: Long, rating: Rating): Card {
        val elapsed = if (card.state != State.New && card.lastReview != null) dayDiff(card.lastReview, now) else 0
        val current = card.copy(lastReview = now, elapsedDays = elapsed, reps = card.reps + 1)
        return when (card.state) {
            State.New -> newState(current, rating, now)
            State.Learning, State.Relearning -> learningState(current, rating, now)
            State.Review -> reviewState(current, rating, now)
        }
    }

    private fun newState(current: Card, rating: Rating, now: Long): Card {
        val next = nextDs(current, 0, rating)
        return applyLearningSteps(next, rating, State.Learning, current, now)
    }

    private fun learningState(current: Card, rating: Rating, now: Long): Card {
        val next = nextDs(current, current.elapsedDays, rating)
        return applyLearningSteps(next, rating, current.state, current, now)
    }

    private fun reviewState(current: Card, rating: Rating, now: Long): Card {
        val interval = current.elapsedDays
        val r = forgettingCurve(interval, current.stability)
        val again = nextDs(current, interval, Rating.Again, r)
        val hard = nextDs(current, interval, Rating.Hard, r)
        val good = nextDs(current, interval, Rating.Good, r)
        val easy = nextDs(current, interval, Rating.Easy, r)
        val (hardCard, goodCard, easyCard) = scheduleReviewIntervals(hard, good, easy, interval, now)
        val againScheduled = applyLearningSteps(again, Rating.Again, State.Relearning, current, now)
        return when (rating) {
            Rating.Again -> againScheduled.copy(lapses = againScheduled.lapses + 1)
            Rating.Hard -> hardCard
            Rating.Good -> goodCard
            Rating.Easy -> easyCard
        }
    }

    private data class Memory(val stability: Double, val difficulty: Double)

    private fun nextState(card: Card, t: Int, rating: Rating, r: Double? = null): Memory {
        val d = card.difficulty
        val s = card.stability
        if (d == 0.0 && s == 0.0) {
            return Memory(initStability(rating), clamp(initDifficulty(rating), 1.0, 10.0))
        }
        check(d >= 1 && s >= S_MIN) { "Invalid memory state { difficulty: $d, stability: $s }" }
        val retrievability = r ?: forgettingCurve(t, s)
        val newS = when {
            t == 0 -> nextShortTermStability(s, rating)
            rating == Rating.Again -> {
                val sAfterFail = nextForgetStability(d, s, retrievability)
                val nextSMin = s / exp(w[17] * w[18])
                clamp(roundTo(nextSMin, 8), S_MIN, sAfterFail)
            }
            else -> nextRecallStability(d, s, retrievability, rating)
        }
        return Memory(newS, nextDifficulty(d, rating))
    }

    private fun nextDs(card: Card, t: Int, rating: Rating, r: Double? = null): Card {
        val m = nextState(card, t, rating, r)
        return card.copy(stability = m.stability, difficulty = m.difficulty)
    }

    /** Learning/relearning steps. Returns (minutes, next step index). */
    private fun learningInfo(current: Card, rating: Rating): Pair<Int, Int> {
        val steps = if (current.state == State.Review || current.state == State.Relearning) relearningSteps else learningSteps
        val cur = current.learningSteps
        if (steps.isEmpty() || cur >= steps.size) return 0 to 0
        val first = steps[0]
        return when (rating) {
            Rating.Again -> first to 0
            Rating.Hard -> {
                val minutes = if (steps.size == 1) round(first * 1.5).toInt() else round((first + steps[1]) / 2.0).toInt()
                minutes to cur
            }
            Rating.Good -> if (cur + 1 < steps.size) steps[cur + 1] to cur + 1 else 0 to 0
            Rating.Easy -> 0 to 0
        }
    }

    private fun applyLearningSteps(card: Card, rating: Rating, toState: State, current: Card, now: Long): Card {
        val (minutes, nextStep) = learningInfo(current, rating)
        if (minutes in 1..1439) {
            return card.copy(learningSteps = nextStep, scheduledDays = 0, state = toState, due = now + minutes * MINUTE_MS)
        }
        val review = card.copy(state = State.Review)
        return if (minutes >= 1440) {
            review.copy(learningSteps = nextStep, scheduledDays = minutes / 1440, due = now + minutes * MINUTE_MS)
        } else {
            val interval = intervalDays(card.stability)
            review.copy(learningSteps = 0, scheduledDays = interval, due = now + interval * DAY_MS)
        }
    }

    private fun scheduleReviewIntervals(hard: Card, good: Card, easy: Card, interval: Int, now: Long): Triple<Card, Card, Card> {
        var h = intervalDays(hard.stability)
        var g = intervalDays(good.stability)
        h = min(h, g)
        g = max(g, h + 1)
        val e = max(intervalDays(easy.stability), g + 1)
        return Triple(
            hard.copy(scheduledDays = h, due = now + h * DAY_MS, state = State.Review, learningSteps = 0),
            good.copy(scheduledDays = g, due = now + g * DAY_MS, state = State.Review, learningSteps = 0),
            easy.copy(scheduledDays = e, due = now + e * DAY_MS, state = State.Review, learningSteps = 0),
        )
    }

    private fun intervalDays(stability: Double): Int =
        min(max(1L, round(stability * intervalModifier).toLong()), maximumInterval.toLong()).toInt()

    private fun initStability(rating: Rating): Double = max(w[rating.value - 1], 0.1)

    private fun initDifficulty(rating: Rating): Double =
        roundTo(w[4] - exp((rating.value - 1) * w[5]) + 1, 8)

    private fun nextDifficulty(d: Double, rating: Rating): Double {
        val delta = -w[6] * (rating.value - 3)
        val next = d + linearDamping(delta, d)
        return clamp(meanReversion(initDifficulty(Rating.Easy), next), 1.0, 10.0)
    }

    private fun linearDamping(delta: Double, old: Double): Double = roundTo(delta * (10 - old) / 9, 8)

    private fun meanReversion(init: Double, current: Double): Double =
        roundTo(w[7] * init + (1 - w[7]) * current, 8)

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.Hard) w[15] else 1.0
        val easyBound = if (rating == Rating.Easy) w[16] else 1.0
        return roundTo(
            clamp(
                s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBound),
                S_MIN,
                S_MAX,
            ),
            8,
        )
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double = roundTo(
        clamp(w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14]), S_MIN, S_MAX),
        8,
    )

    private fun nextShortTermStability(s: Double, rating: Rating): Double {
        val sinc = s.pow(-w[19]) * exp(w[17] * (rating.value - 3 + w[18]))
        val masked = if (rating.value >= Rating.Hard.value) max(sinc, 1.0) else sinc
        return roundTo(clamp(s * masked, S_MIN, S_MAX), 8)
    }

    private fun forgettingCurve(t: Int, s: Double): Double =
        roundTo((1 + factor * t / s).pow(decay), 8)

    private fun dayDiff(last: Long, now: Long): Int = (floorDiv(now, DAY_MS) - floorDiv(last, DAY_MS)).toInt()

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if (a % b != 0L && (a xor b) < 0) q - 1 else q
    }
}

private fun clamp(v: Double, lo: Double, hi: Double): Double = min(max(v, lo), hi)
private fun roundTo(v: Double, decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return round(v * factor) / factor
}
