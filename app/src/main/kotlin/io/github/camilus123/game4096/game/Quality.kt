package io.github.camilus123.game4096.game

/**
 * How richly the board is drawn. Gameplay and the timing of every animation are identical
 * at every level; only the decoration changes.
 *
 *  - LOW: flat tiles, nothing else. A resting board draws no frames at all.
 *  - MEDIUM: adds a drop shadow under each tile, a ring that expands from a merge, and a
 *    springier pop for new tiles. Still no frames at rest.
 *  - HIGH: adds a burst of particles on merges, a pulsing glow on tiles of 128 and up, and
 *    slow drifting lights behind the board. The glow and the lights keep moving at rest, so
 *    this level alone draws while nothing is happening (at about 30 frames per second).
 */
enum class Quality {
    LOW,
    MEDIUM,
    HIGH;

    /** One level cheaper, or LOW if already there. */
    fun lower(): Quality = if (this == HIGH) MEDIUM else LOW
}
