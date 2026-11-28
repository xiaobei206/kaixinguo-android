@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.kadaliao.kaixinguo.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.kadaliao.kaixinguo.cenc.Layout
import com.kadaliao.kaixinguo.model.Streams
import com.kadaliao.kaixinguo.model.debugLog
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.Executors

/**
 * StreamRegistry keeps the episodes' files the player may read, by the URI
 * it plays each at. It keeps the last few: the one playing, and one the
 * player may still read.
 */
class StreamRegistry(private val http: DataSource.Factory) : Streams {
    private val streams = LinkedHashMap<String, StreamSource>()

    /** One daemon keeps prefetches off ExoPlayer's loading thread. */
    private val reader = Executors.newSingleThreadExecutor { r ->
        Thread(r, "kaixinguo-stream-prefetch").apply { isDaemon = true }
    }

    override fun register(sources: List<String>, key: ByteArray?): String =
        add(StreamSource(sources, key, http))

    override fun preload(sources: List<String>, key: ByteArray?, bytes: Long): String {
        val source = StreamSource(sources, key, http)
        val uri = add(source)
        reader.execute { source.preload(bytes.coerceIn(64L shl 10, 8L shl 20)) }
        return uri
    }

    override fun has(uri: String): Boolean = get(uri) != null

    override fun drop(uri: String) {
        synchronized(this) { streams.remove(uri) }?.dropped = true
    }

    private fun add(source: StreamSource): String {
        val uri = "$SCHEME://episode/\${UUID.randomUUID()}.mp4"
        synchronized(this) {
            streams[uri] = source
            // The active stream, a just-finished stream and one read-ahead are enough.
            while (streams.size > 3) streams.remove(streams.keys.first())?.dropped = true
        }
        return uri
    }

    operator fun get(uri: String): StreamSource? = synchronized(this) { streams[uri] }

    companion object {
        const val SCHEME = "kaixinguo-stream"
    }
}

/**
 * StreamSource is one episode's file on the CDN: a URL and its backup, and
 * the key that decrypts it, or none for a clear file. The first read learns
 * the file's size and, for an encrypted one, its layout; every read after
 * shares them.
 */
class StreamSource(val sources: List<String>, private val key: ByteArray?, private val http: DataSource.Factory) {
    class File(val size: Long, val layout: Layout?)

    /** The source that answered last, which reads try first. */
    @Volatile
    var good = 0

    /** Beginning of the file, already decrypted; replaced whole as more bytes arrive. */
    @Volatile
    var prefix = ByteArray(0)
        private set

    /** True after the registry releases this URI; an in-flight prefetch discards its result. */
    @Volatile
    var dropped = false

    private var file: File? = null
    private var failure: IOException? = null

    /**
     * Learn file size and CENC layout on a loading thread. Failed prefetches
     * must not poison later playback, so only ordinary opens cache a failure.
     */
    @Synchronized
    fun prepare(soft: Boolean = false): File {
        file?.let { return it }
        if (!soft) failure?.let { throw it }
        var last = IOException("没有视频地址")
        for ((i, source) in sources.withIndex()) {
            try {
                val (head, total) = read(source, 0, 64L shl 10)
                val layout = key?.let { k ->
                    Layout.open(total, k) { off, n ->
                        if (off + n <= head.size) head.copyOfRange(off.toInt(), (off + n).toInt()) else read(source, off, n).first
                    }
                }
                good = i
                val clearHead = head.copyOf()
                layout?.decrypt(clearHead, 0)
                if (prefix.isEmpty()) prefix = clearHead
                return File(total, layout).also { file = it }
            } catch (e: InterruptedIOException) {
                throw e // the player gave up on this read: another may try again
            } catch (e: IOException) {
                debugLog { "stream: ${Uri.parse(source).host}: $e" }
                last = e
            } catch (e: RuntimeException) {
                debugLog { "stream: ${Uri.parse(source).host}: $e" }
                last = IOException(e)
            }
        }
        if (!soft) failure = last
        throw last
    }

    /**
     * Read the first bytes ahead and keep them decrypted. Errors are non-fatal:
     * normal playback can still use this URI and try the primary/backup source.
     */
    fun preload(bytes: Long) {
        if (dropped) return
        try {
            val f = prepare(soft = true)
            val from = prefix.size.toLong()
            val to = minOf(f.size, bytes)
            if (from >= to || dropped) return
            val got = read(sources[good], from, to - from).first
            f.layout?.decrypt(got, from)
            synchronized(this) {
                if (!dropped && prefix.size.toLong() == from) prefix += got
            }
            debugLog { "stream: read ahead ${prefix.size} bytes" }
        } catch (e: IOException) {
            debugLog { "stream: reading ahead: $e" }
        } catch (e: RuntimeException) {
            debugLog { "stream: reading ahead: $e" }
        }
    }

    /** Reads `count` bytes of `source` at `offset`, and the file's size. */
    private fun read(source: String, offset: Long, count: Long): Pair<ByteArray, Long> {
        val ds = http.createDataSource().apply { addTransferListener(PlaybackDiagnostics) }
        try {
            ds.open(DataSpec.Builder().setUri(source).setPosition(offset).setLength(count).build())
            val total = totalSize(ds.responseHeaders) ?: throw IOException("CDN 没有告知文件大小")
            val out = ByteArrayOutputStream(count.toInt().coerceAtMost(1 shl 20))
            val buf = ByteArray(32 shl 10)
            while (out.size() < count) {
                val n = ds.read(buf, 0, minOf(buf.size.toLong(), count - out.size()).toInt())
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buf, 0, n)
            }
            return out.toByteArray() to total
        } finally {
            ds.close()
        }
    }
}

/** The file's size, from the response to a range request, or to a request for the whole file. */
private fun totalSize(headers: Map<String, List<String>>): Long? {
    fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    header("Content-Range")?.let { cr ->
        return cr.substringAfterLast('/', "").trim().toLongOrNull()?.takeIf { it > 0 }
    }
    return header("Content-Length")?.trim()?.toLongOrNull()?.takeIf { it > 0 }
}

/**
 * CencDataSource reads an episode registered with [StreamRegistry]: each
 * range the player asks for comes from the CDN, decrypted in place as it
 * streams, so playback starts at once and can seek anywhere. It is the
 * Android counterpart of the iOS app's StreamLoader. A download that breaks
 * off goes on from where it stopped, from the other source when there is
 * one, twice at most.
 */
class CencDataSource(private val registry: StreamRegistry, private val http: DataSource.Factory) : BaseDataSource(true) {
    class Factory(private val registry: StreamRegistry, private val http: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = CencDataSource(registry, http)
    }

    private var spec: DataSpec? = null
    private var stream: StreamSource? = null
    private var layout: Layout? = null
    private var upstream: DataSource? = null
    private var position = 0L // where the next byte read belongs in the file
    private var end = 0L // past the last byte wanted
    private var source = 0
    private var tries = 0 // downloads of this range that broke off
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        transferInitializing(dataSpec)
        val s = registry[dataSpec.uri.toString()] ?: throw IOException("这一集的地址已经失效")
        val f = s.prepare()
        stream = s
        layout = f.layout
        position = dataSpec.position
        if (position > f.size) throw IOException("超出文件末尾")
        end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) f.size else minOf(f.size, position + dataSpec.length)
        source = s.good
        tries = 0
        if (position < end && position >= s.prefix.size) connect()
        opened = true
        transferStarted(dataSpec)
        return end - position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= end) return C.RESULT_END_OF_INPUT
        val want = minOf(length.toLong(), end - position).toInt()
        if (upstream == null) {
            // The prefix is already decrypted; copy it until playback catches up.
            val cached = stream!!.prefix
            if (position < cached.size) {
                val n = minOf(want.toLong(), cached.size - position).toInt()
                System.arraycopy(cached, position.toInt(), buffer, offset, n)
                position += n
                bytesTransferred(n)
                return n
            }
            connect()
        }
        while (true) {
            val n = try {
                upstream!!.read(buffer, offset, want)
            } catch (e: IOException) {
                if (e is InterruptedIOException || !again()) throw e
                connect()
                continue
            }
            if (n == C.RESULT_END_OF_INPUT) {
                // The CDN stopped short of the range.
                if (!again()) throw IOException("视频下载中断")
                connect()
                continue
            }
            layout?.decrypt(buffer, offset, n, position)
            position += n
            stream?.good = source
            bytesTransferred(n)
            return n
        }
    }

    /** Asks for the rest of the range, from the other source when there is one. */
    private fun connect() {
        val s = stream!!
        while (true) {
            closeUpstream()
            val ds = http.createDataSource().apply { addTransferListener(PlaybackDiagnostics) }
            try {
                ds.open(DataSpec.Builder().setUri(s.sources[source]).setPosition(position).setLength(end - position).build())
                upstream = ds
                return
            } catch (e: IOException) {
                runCatching { ds.close() }
                if (e is InterruptedIOException || !again()) throw e
            }
        }
    }

    /** Whether to try the range once more, switching sources if there is another. */
    private fun again(): Boolean {
        val s = stream ?: return false
        if (tries >= 2 || Thread.currentThread().isInterrupted) return false
        tries++
        source = (source + 1) % s.sources.size
        return true
    }

    private fun closeUpstream() {
        val ds = upstream ?: return
        upstream = null
        runCatching { ds.close() }
    }

    override fun getUri(): Uri? = spec?.uri

    override fun close() {
        spec = null
        try {
            closeUpstream()
        } finally {
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
