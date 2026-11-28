package com.kadaliao.kaixinguo.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.ArrayDeque
import kotlin.coroutines.cancellation.CancellationException

/**
 * Feed is a list of series loaded a page at a time. It belongs to the main
 * thread: its scope runs there, and the views read its state there.
 */
class Feed(
    private val scope: CoroutineScope,
    private val fetch: (suspend (cursor: String) -> FeedPage)?,
) {
    var cards by mutableStateOf(emptyList<Card>())
        private set
    private var seen = HashSet<String>()
    private var cursor = ""
    var more by mutableStateOf(true)
        private set
    var started by mutableStateOf(false)
        internal set
    var loading by mutableStateOf(false)
        private set

    /** The cards came from the disk cache: the first page replaces them. */
    var stale by mutableStateOf(false)
        internal set
    var error by mutableStateOf<Throwable?>(null)
        private set
    /** Preserved across an automatic retry so diagnostics remain available while retrying. */
    var lastFailure by mutableStateOf<Throwable?>(null)
        private set
    private var errorAt = 0L // System.nanoTime of the last failure

    /** Route identity and request state included in the one-tap diagnostic report. */
    var diagnosticName: String = "内容列表"
        internal set
    var diagnosticContext: String = ""
        internal set
    var cacheCardCount: Int = 0
        internal set

    var requestAttempts: Int = 0
        private set
    var successfulRequests: Int = 0
        private set
    var automaticRetryCount: Int = 0
        private set
    var lastRequestAt: String? = null
        private set
    var lastSuccessAt: String? = null
        private set
    var lastFailureAt: String? = null
        private set
    var lastRequestedCursorPresent: Boolean = false
        private set
    var lastRequestedCursorLength: Int = 0
        private set
    private val diagnosticLock = Any()
    private val diagnosticEvents = ArrayDeque<String>()
    private var lastRequestStartedNanos = 0L
    val currentCursorPresent: Boolean get() = cursor.isNotEmpty()
    val currentCursorLength: Int get() = cursor.length

    /** Retries made by themselves since the last success. */
    private var tries = 0

    /** Whether a retry is on its way by itself. */
    var retrying by mutableStateOf(false)
        private set

    internal var onPage: ((FeedPage) -> Unit)? = null

    /** Sorts the cards after each page, when set. */
    internal var order: ((List<Card>) -> List<Card>)? = null

    internal fun add(p: FeedPage) {
        var incoming = p.cards
        if (stale) {
            // Fresh series first, then those of the cache, so the grid
            // never shrinks under the viewer.
            incoming = incoming + cards
            cards = emptyList()
            seen = HashSet()
            stale = false
        }
        var list = ArrayList(cards)
        var added = 0
        for (k in incoming) {
            if (k.id.isNotEmpty() && seen.add(k.id)) {
                list += k
                added++
            }
        }
        cursor = p.cursor
        more = p.more
        // Search pages repeat what came before: a page with nothing new
        // ends the feed, or the grid would ask for more forever.
        if (added == 0 && p.cards.isNotEmpty()) more = false
        order?.let { list = ArrayList(it(list)) }
        cards = list
    }

    /** Whether the grid should ask for the next page. */
    val wantsMore: Boolean get() = fetch != null && (stale || more || !started)

    /**
     * Fetches the next page, unless one is on its way, the feed has no
     * more, or the last try failed a moment ago.
     */
    fun loadMore() {
        val fetch = fetch ?: return
        if (loading || (started && !stale && !more)) return
        if (error != null && System.nanoTime() - errorAt < 5_000_000_000L) return
        loading = true
        started = true
        val cursor = if (stale) "" else this.cursor
        recordRequest(cursor)
        scope.launch {
            try {
                val p = fetch(cursor)
                loading = false
                markSuccess(p)
                error = null
                lastFailure = null
                tries = 0
                add(p)
                onPage?.invoke(p)
            } catch (e: CancellationException) {
                loading = false
                throw e
            } catch (e: Exception) {
                loading = false
                error = e
                lastFailure = e
                errorAt = System.nanoTime()
                markFailure(e)
                debugLog { "feed: $e" }
                scheduleRetry()
            }
        }
    }

    /**
     * Tries again by itself after 2, 4, 8 and 16 seconds, while the feed
     * has nothing to show; then it waits for the viewer.
     */
    private fun scheduleRetry() {
        if (cards.isNotEmpty() || tries >= 4) {
            retrying = false
            return
        }
        val wait = 2000L shl tries
        tries++
        automaticRetryCount++
        retrying = true
        scope.launch {
            delay(wait)
            retrying = false
            error = null
            loadMore()
        }
    }

    /** Fetches again after an error, now. */
    fun retry() {
        if (loading) return
        tries = 0
        error = null
        retrying = false
        // A successful empty first page is still retryable (notably for search).
        // Reset only an exhausted feed with no cards; populated feeds retain pagination.
        if (cards.isEmpty() && started && !more) {
            cursor = ""
            more = true
            started = false
            seen.clear()
        }
        loadMore()
    }

    /**
     * Fetches the first page again, as the viewer pulls the grid down.
     * Merging keeps the cards there are after the fresh ones; else the
     * fresh page replaces them.
     */
    suspend fun refresh(merging: Boolean) {
        val fetch = fetch ?: return
        if (loading) return
        loading = true
        recordRequest("")
        try {
            val p = fetch("")
            markSuccess(p)
            val had = cards.isNotEmpty()
            val oldCursor = cursor
            val oldMore = more
            if (merging) {
                stale = true
            } else {
                cards = emptyList()
                seen = HashSet()
            }
            error = null
            lastFailure = null
            tries = 0
            add(p)
            if (merging && had) {
                // The grid goes on from where it had got to, not from the
                // fresh page's second.
                cursor = oldCursor
                more = oldMore
            }
            onPage?.invoke(p)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e
            lastFailure = e
            errorAt = System.nanoTime()
            markFailure(e)
        } finally {
            loading = false
        }
    }

    private fun recordRequest(requestCursor: String) {
        requestAttempts++
        lastRequestAt = Instant.now().toString()
        lastRequestedCursorPresent = requestCursor.isNotEmpty()
        lastRequestedCursorLength = requestCursor.length
        lastRequestStartedNanos = System.nanoTime()
        recordDiagnosticEvent(
            "${lastRequestAt} | #${requestAttempts} START cursorPresent=${lastRequestedCursorPresent} cursorLength=${lastRequestedCursorLength}"
        )
    }

    private fun markSuccess(page: FeedPage) {
        successfulRequests++
        lastSuccessAt = Instant.now().toString()
        val elapsedMs = ((System.nanoTime() - lastRequestStartedNanos).coerceAtLeast(0L)) / 1_000_000L
        recordDiagnosticEvent(
            "${lastSuccessAt} | #${requestAttempts} SUCCESS durationMs=${elapsedMs} returnedCards=${page.cards.size} hasMore=${page.more} nextCursorPresent=${page.cursor.isNotEmpty()} nextCursorLength=${page.cursor.length}"
        )
    }

    private fun markFailure(error: Throwable) {
        lastFailureAt = Instant.now().toString()
        val elapsedMs = ((System.nanoTime() - lastRequestStartedNanos).coerceAtLeast(0L)) / 1_000_000L
        recordDiagnosticEvent(
            "${lastFailureAt} | #${requestAttempts} FAILURE durationMs=${elapsedMs} exception=${error.javaClass.name} message=${sanitizeDiagnosticText(error.message ?: error.toString())}"
        )
    }

    private fun recordDiagnosticEvent(message: String) {
        synchronized(diagnosticLock) {
            diagnosticEvents.addLast(message.take(1200))
            while (diagnosticEvents.size > 40) diagnosticEvents.removeFirst()
        }
    }

    internal fun diagnosticHistory(): List<String> =
        synchronized(diagnosticLock) { diagnosticEvents.toList() }

    companion object {
        /** A feed of these cards alone, with nothing to fetch. */
        fun of(scope: CoroutineScope, cards: List<Card>): Feed = Feed(scope, null).apply {
            add(FeedPage(cards))
            started = true
            more = false
        }
    }
}
