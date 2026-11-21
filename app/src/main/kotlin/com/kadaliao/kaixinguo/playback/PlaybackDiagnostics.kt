@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.kadaliao.kaixinguo.playback

import android.media.MediaCodecList
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.TreeMap

/** Network graphs are sampled from actual CDN HTTP transfers beneath CencDataSource. */
object PlaybackDiagnostics : TransferListener {
    data class Snapshot(
        val connectionSpeedBps: Long, val networkSamples: List<Long>,
        val networkBytesLast20Seconds: Long, val networkActive: Boolean,
        val videoCodec: String, val audioCodec: String, val decoderName: String,
        val decoderHardware: Boolean?, val width: Int, val height: Int,
        val frameRate: Float, val bitrate: Int, val droppedFrames: Long,
    )

    // 80 fixed columns at 250 ms each form a 20-second history.
    private const val BUCKET_MS = 250L
    private const val BUCKETS = 80
    private const val SPEED_HOLD_MS = 8_000L
    private val lock = Any()
    private val bytesByBucket = TreeMap<Long, Long>()
    private var activeTransfers = 0
    @Volatile private var bandwidthEstimateBps = 0L
    @Volatile private var bandwidthEstimateAt = 0L
    @Volatile private var lastValidSpeedBps = 0L
    @Volatile private var lastValidSpeedAt = 0L
    @Volatile private var videoCodec = ""
    @Volatile private var audioCodec = ""
    @Volatile private var decoderName = ""
    @Volatile private var decoderHardware: Boolean? = null
    @Volatile private var width = 0
    @Volatile private var height = 0
    @Volatile private var frameRate = 0f
    @Volatile private var bitrate = 0
    @Volatile private var droppedFrames = 0L

    fun resetMedia() {
        videoCodec = ""; audioCodec = ""; decoderName = ""; decoderHardware = null
        width = 0; height = 0; frameRate = 0f; bitrate = 0; droppedFrames = 0L
        bandwidthEstimateBps = 0L; bandwidthEstimateAt = 0L
        lastValidSpeedBps = 0L; lastValidSpeedAt = 0L
        synchronized(lock) { bytesByBucket.clear() }
    }

    fun onBandwidthEstimate(bitsPerSecond: Long) {
        if (bitsPerSecond > 0) {
            bandwidthEstimateBps = bitsPerSecond
            bandwidthEstimateAt = SystemClock.elapsedRealtime()
        }
    }

    fun onVideoFormat(format: Format) {
        videoCodec = codecLabel(format)
        width = format.width.takeIf { it > 0 } ?: 0
        height = format.height.takeIf { it > 0 } ?: 0
        frameRate = format.frameRate.takeIf { it > 0f && it.isFinite() } ?: 0f
        bitrate = format.bitrate.takeIf { it > 0 } ?: 0
    }

    fun onAudioFormat(format: Format) { audioCodec = codecLabel(format) }
    fun onDecoderInitialized(name: String) { decoderName = name; decoderHardware = isHardwareDecoder(name) }
    fun onDroppedVideoFrames(count: Int) { if (count > 0) droppedFrames += count.toLong() }

    fun snapshot(): Snapshot {
        val now = SystemClock.elapsedRealtime()
        val bucket = now / BUCKET_MS
        val samples = synchronized(lock) {
            val oldest = bucket - BUCKETS + 1
            while (bytesByBucket.isNotEmpty() && bytesByBucket.firstKey() < oldest) bytesByBucket.pollFirstEntry()
            (0 until BUCKETS).map { bytesByBucket[bucket - BUCKETS + 1 + it] ?: 0L }
        }

        // Four 250 ms buckets make a rolling one-second throughput sample.
        // Keep the last useful estimate briefly through short-drama request gaps,
        // instead of flashing "—" whenever one sampling interval is idle.
        val recentBytes = samples.takeLast(4).sum()
        val measuredBps = recentBytes * 8L
        val estimateFresh = bandwidthEstimateAt > 0L && now - bandwidthEstimateAt < SPEED_HOLD_MS
        val hasRecentNetworkBytes = recentBytes > 0L
        val candidateSpeed = when {
            hasRecentNetworkBytes && estimateFresh -> maxOf(measuredBps, bandwidthEstimateBps)
            hasRecentNetworkBytes -> measuredBps
            estimateFresh && bandwidthEstimateBps > 0L -> bandwidthEstimateBps
            else -> 0L
        }
        // A bandwidth estimate is timestamped when Media3 produces it. Re-reading
        // the same estimate must not restart its 8-second hold window on every UI
        // frame; real received bytes, on the other hand, refresh the sample time.
        val candidateSampleAt = if (hasRecentNetworkBytes) now else bandwidthEstimateAt
        if (candidateSpeed > 0L && candidateSampleAt > lastValidSpeedAt) {
            lastValidSpeedBps = candidateSpeed
            lastValidSpeedAt = candidateSampleAt
        }
        val speed = if (lastValidSpeedBps > 0L && now - lastValidSpeedAt < SPEED_HOLD_MS) {
            lastValidSpeedBps
        } else {
            0L
        }

        val active: Boolean
        synchronized(lock) { active = activeTransfers > 0 }
        return Snapshot(
            speed, samples, samples.sum(), active, videoCodec, audioCodec, decoderName,
            decoderHardware, width, height, frameRate, bitrate, droppedFrames,
        )
    }

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (isNetwork) synchronized(lock) { activeTransfers++ }
    }
    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (!isNetwork || bytesTransferred <= 0) return
        val bucket = SystemClock.elapsedRealtime() / BUCKET_MS
        synchronized(lock) {
            bytesByBucket[bucket] = (bytesByBucket[bucket] ?: 0L) + bytesTransferred
            val oldest = bucket - BUCKETS + 1
            while (bytesByBucket.isNotEmpty() && bytesByBucket.firstKey() < oldest) bytesByBucket.pollFirstEntry()
        }
    }
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (isNetwork) synchronized(lock) { activeTransfers = (activeTransfers - 1).coerceAtLeast(0) }
    }

    private fun codecLabel(format: Format): String {
        format.codecs?.takeIf { it.isNotBlank() }?.let { return it }
        return format.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: "未知"
    }

    @Suppress("DEPRECATION")
    private fun isHardwareDecoder(name: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == name }?.isHardwareAccelerated
                ?: legacyHardwareGuess(name)
        } else legacyHardwareGuess(name)
    } catch (_: Exception) { legacyHardwareGuess(name) }

    private fun legacyHardwareGuess(name: String): Boolean =
        !name.startsWith("OMX.google.", true) && !name.startsWith("c2.android.", true)
}
