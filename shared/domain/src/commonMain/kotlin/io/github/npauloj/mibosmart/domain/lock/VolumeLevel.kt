package io.github.npauloj.mibosmart.domain.lock

/**
 * How loudly the lock announces itself: the four levels the hardware accepts (SPEC L7).
 *
 * The partner sends and takes the [level] as an integer 0..3 (`docs/api-contract.md` §5); the app
 * reasons about the named levels, so a fifth value on the wire is a contract change the data layer
 * has to report rather than a number the UI renders as "volume 4".
 */
enum class VolumeLevel(val level: Int) {
    Mute(0),
    Low(1),
    Medium(2),
    High(3),
    ;

    companion object {

        /** The level [level] names, or `null` when the partner answered outside 0..3 (SPEC E3). */
        fun ofLevel(level: Int): VolumeLevel? = entries.firstOrNull { it.level == level }
    }
}
