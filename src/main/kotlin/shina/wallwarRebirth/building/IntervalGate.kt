package shina.wallwarRebirth.building

/**
 * Shared timer for automatic defenses and barracks.
 * Defenses start ready when placed. Each attack resets the age.
 * With no target, the age parks at the interval until a target appears.
 * Upgrades preserve elapsed time.
 */
object IntervalGate {
    fun advance(age: Int, interval: Int, counting: Boolean): Int =
        if (counting && age < interval) age + 1 else age

    fun due(age: Int, interval: Int): Boolean = age >= interval

    /** Count even while idle, then hold at the interval until a target exists. */
    fun step(age: Int, interval: Int, hasTarget: Boolean): Pair<Int, Boolean> {
        val next = if (age < interval) age + 1 else age
        if (next < interval || !hasTarget) return next to false
        return 0 to true
    }
}
