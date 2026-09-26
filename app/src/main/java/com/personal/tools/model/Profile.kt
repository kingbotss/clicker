package com.personal.tools.model

/** A named set of tap points, e.g. one per app/purpose. */
data class Profile(
    val id: Long,
    val name: String,
) {
    override fun toString(): String = name // used by the Spinner adapter
}
