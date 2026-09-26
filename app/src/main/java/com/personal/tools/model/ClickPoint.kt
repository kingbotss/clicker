package com.personal.tools.model

/**
 * A single tap target belonging to a profile. The engine taps [x],[y] then
 * waits [delayMs] before the next point. [x],[y],[delayMs] come first so the
 * engine can build points positionally; [id]/[label]/[position] are DB fields.
 */
data class ClickPoint(
    val x: Int,
    val y: Int,
    val delayMs: Long = 500L,
    val id: Long = 0L,
    val label: String = "",
    val position: Int = 0,
)
