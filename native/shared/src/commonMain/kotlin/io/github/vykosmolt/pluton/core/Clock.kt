package io.github.vykosmolt.pluton.core

/** Epoch milliseconds, common to Android, the JVM and iOS (`System` is JVM-only). */
internal fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
