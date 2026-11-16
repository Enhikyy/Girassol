package dev.labforge.app.core.world

/** Serializable xorshift64* generator. No hidden JVM/Android entropy is used during simulation. */
internal class WorldRandom(var state: Long) {
    init {
        if (state == 0L) state = 0x6A09E667F3BCC909L
    }

    fun nextLong(): Long {
        var x = state
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        state = x
        return x * 0x2545F4914F6CDD1DL
    }

    fun nextFloat(): Float = ((nextLong() ushr 40) and 0xFFFFFFL).toFloat() / 16_777_216f

    fun nextDouble(): Double = ((nextLong() ushr 11) and 0x1FFFFFFFFFFFFFL).toDouble() / 9_007_199_254_740_992.0

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return (nextDouble() * bound).toInt().coerceIn(0, bound - 1)
    }

    fun range(min: Float, max: Float): Float = min + (max - min) * nextFloat()

    fun <T> pick(values: List<T>): T = values[nextInt(values.size)]
}
