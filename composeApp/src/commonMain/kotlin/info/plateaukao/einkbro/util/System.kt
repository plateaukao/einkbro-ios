package info.plateaukao.einkbro.util

import kotlinx.datetime.Clock

/** Stand-in for java.lang.System where ported code calls currentTimeMillis(). */
object System {
    fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
