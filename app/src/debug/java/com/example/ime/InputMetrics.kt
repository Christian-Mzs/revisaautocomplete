package com.example.ime

import android.view.MotionEvent

/** Development-only numerical counters. No text, coordinates, editor identities or logging. */
object InputMetrics {
    private val counts = LongArray(18)
    private val latencyBuckets = LongArray(64)
    private var receivedAt = 0L
    private var latencyCount = 0L
    private var latencySum = 0L
    private var latencyMin = Long.MAX_VALUE
    private var latencyMax = 0L
    fun event(action: Int, pointers: Int) {
        val index = when (action) {
            MotionEvent.ACTION_DOWN -> 0
            MotionEvent.ACTION_UP -> 1
            MotionEvent.ACTION_CANCEL -> 2
            MotionEvent.ACTION_MOVE -> 3
            MotionEvent.ACTION_POINTER_DOWN -> 4
            MotionEvent.ACTION_POINTER_UP -> 5
            else -> return
        }
        counts[index]++
        counts[6] = maxOf(counts[6], pointers.toLong())
        receivedAt = System.nanoTime()
    }
    fun resolved() { counts[7]++ }
    fun miss() { counts[8]++ }
    fun activation() { counts[9]++ }
    fun abandoned() { counts[10]++ }
    fun character() { counts[11]++ }
    fun direct() { counts[12]++ }
    fun commit() {
        counts[13]++
        if (receivedAt == 0L) return
        val elapsed = (System.nanoTime() - receivedAt).coerceAtLeast(0)
        latencyCount++
        latencySum += elapsed
        latencyMin = minOf(latencyMin, elapsed)
        latencyMax = maxOf(latencyMax, elapsed)
        val bucket = if (elapsed == 0L) 0 else 64 - java.lang.Long.numberOfLeadingZeros(elapsed)
        latencyBuckets[bucket.coerceAtMost(63)]++
        receivedAt = 0
    }
    fun commitResult(ok: Boolean) { counts[if (ok) 14 else 15]++ }
    fun delete() { counts[16]++ }
    fun editorAction() { counts[17]++ }
    fun reset() {
        counts.fill(0); latencyBuckets.fill(0); receivedAt = 0
        latencyCount = 0; latencySum = 0; latencyMin = Long.MAX_VALUE; latencyMax = 0
    }
    /** One aggregate per closed input session, never a log per key. */
    fun finishSession() {
        android.util.Log.d("RevisaInputMetrics", snapshot().toString())
        reset()
    }
    /** p95 is a histogram upper bound in nanoseconds, not an exact percentile. */
    fun snapshot(): Map<String, Long> {
        val names = arrayOf("down", "up", "cancel", "move", "pointerDown", "pointerUp", "maxPointers",
            "resolved", "miss", "activation", "abandoned", "character", "direct", "commitAttempt",
            "commitAccepted", "commitRejected", "deleteAttempt", "editorActionAttempt")
        val result = names.indices.associate { names[it] to counts[it] }.toMutableMap()
        var cumulative = 0L
        var p95 = 0L
        if (latencyCount > 0) for (i in latencyBuckets.indices) {
            cumulative += latencyBuckets[i]
            if (cumulative >= (latencyCount * 95 + 99) / 100) {
                p95 = if (i == 63) Long.MAX_VALUE else (1L shl i); break
            }
        }
        result["latencyCount"] = latencyCount
        result["latencyMinNs"] = if (latencyCount == 0L) 0 else latencyMin
        result["latencyAverageNs"] = if (latencyCount == 0L) 0 else latencySum / latencyCount
        result["latencyP95UpperNs"] = p95
        result["latencyMaxNs"] = latencyMax
        return result
    }
}
