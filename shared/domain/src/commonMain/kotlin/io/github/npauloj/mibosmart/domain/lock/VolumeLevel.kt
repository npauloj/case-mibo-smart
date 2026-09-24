package io.github.npauloj.mibosmart.domain.lock

/** How loudly the lock announces itself: the four levels the hardware accepts (SPEC L7). */
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
