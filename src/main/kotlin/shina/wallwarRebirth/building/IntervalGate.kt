package shina.wallwarRebirth.building

/**
 * Shared timer for towers, tesla, cannons, and barracks.
 * The first shot waits out the full interval. Upgrades do not reset it.
 * With no target the age parks at the interval so the next target fires immediately.
 * A shorter interval that has already elapsed fires on the next check.
 * Base mineral output does not use this.
 */
object IntervalGate {
    fun advance(age: Int, interval: Int, counting: Boolean): Int =
        if (counting && age < interval) age + 1 else age

    fun due(age: Int, interval: Int): Boolean = age >= interval

    /** Tower, tesla, and cannon: count even while idle, then hold at the interval until a target exists. */
    fun step(age: Int, interval: Int, hasTarget: Boolean): Pair<Int, Boolean> {
        val next = if (age < interval) age + 1 else age
        if (next < interval || !hasTarget) return next to false
        return 0 to true
    }
}
