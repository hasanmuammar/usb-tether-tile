package dev.hasan.usbtethertile

enum class ShizukuStatus { READY, NEEDS_PERMISSION, NOT_RUNNING, NOT_INSTALLED }

/** Pure mapping from raw Shizuku checks to a status. */
fun mapStatus(installed: Boolean, pingBinder: Boolean, granted: Boolean): ShizukuStatus = when {
    !installed -> ShizukuStatus.NOT_INSTALLED
    !pingBinder -> ShizukuStatus.NOT_RUNNING
    granted -> ShizukuStatus.READY
    else -> ShizukuStatus.NEEDS_PERMISSION
}
