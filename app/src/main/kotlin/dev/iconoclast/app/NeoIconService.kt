package dev.iconoclast.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Taps the pack's icon in Neo Launcher's icon screen, for the one app [NeoCustomIcons]
 * opened it for. It reads only Neo's windows, acts only while [pending] is set, and shows
 * nothing: no overlay, button or notification.
 */
class NeoIconService : AccessibilityService() {
    override fun onServiceConnected() {
        Log.i(TAG, "neo service connected")
        running = true
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        running = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running = false
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private val handler = Handler(Looper.getMainLooper())
    private var polls = 0

    /**
     * Neo's screen sends its events before Compose has drawn the row, so an event only starts
     * a short poll: every [POLL_MS] until the row is there, at most [POLLS] times.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (pending == null || polls > 0) return
        polls = POLLS
        handler.post(::poll)
    }

    private fun poll() {
        if (pending == null || --polls <= 0) {
            polls = 0
            return
        }
        if (!tryTap()) handler.postDelayed(::poll, POLL_MS) else polls = 0
    }

    /** True once the screen was handled: tapped, or left with Back when the row is not as expected. */
    private fun tryTap(): Boolean {
        val target = pending ?: return true
        val root = windows.mapNotNull { it.root }.firstOrNull { r ->
            // Compose answers no text search: walk the tree.
            r.packageName == NeoCustomIcons.NEO && hasText(r, target.label)
        } ?: return false
        val row = topRow(root)
        if (row.size < target.total) {
            // Icons past the edge of the screen may not be in the tree yet: scroll the row.
            row.lastOrNull()?.parent?.takeIf { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            return false
        }
        pending = null
        val ok = row.size == target.total && row[target.index].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "neo ${target.component.flattenToShortString()}: ${row.size} icons, expected ${target.total}, tap ${target.index}: $ok")
        if (ok) tapped++ else performGlobalAction(GLOBAL_ACTION_BACK)
        return true
    }

    /**
     * The top row of Neo's icon screen: clickable nodes with no text inside, above the first
     * clickable node with text (the pack list). The app's own icon on the left is not clickable.
     */
    private fun topRow(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val clickable = ArrayList<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo) {
            if (n.isClickable) clickable += n
            for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)
        }
        walk(root)
        val firstList = clickable.firstOrNull { hasText(it) } ?: return emptyList()
        val listTop = Rect().also(firstList::getBoundsInScreen).top
        return clickable.filter { !hasText(it) && Rect().also(it::getBoundsInScreen).bottom <= listTop }
    }

    /** [n] or a node inside it has text, [exact] when given. */
    private fun hasText(n: AccessibilityNodeInfo, exact: String? = null): Boolean {
        val t = n.text?.toString()
        if (!t.isNullOrEmpty() && (exact == null || t == exact)) return true
        for (i in 0 until n.childCount) if (n.getChild(i)?.let { hasText(it, exact) } == true) return true
        return false
    }

    companion object {
        private const val TAG = "Iconoclast"

        /** The app whose Neo icon screen is about to open; cleared once tapped. */
        @Volatile var pending: NeoCustomIcons.Target? = null

        /** Taps made since the app started, so a run can count what Neo took. */
        @Volatile var tapped = 0

        private const val POLL_MS = 150L
        private const val POLLS = 40

        /** The user has turned the service on in Accessibility settings. */
        @Volatile var running = false

        @Volatile private var instance: NeoIconService? = null

        /** Presses Back, to leave a Neo screen the service could not act on. */
        fun back() {
            instance?.performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }
}
