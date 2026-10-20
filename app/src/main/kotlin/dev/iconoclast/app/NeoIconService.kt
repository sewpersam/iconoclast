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
 * opened it for. It reads only Neo's windows, acts only while [pending] is set and only on
 * Neo's icon screen titled with that app's name, and shows nothing: no overlay, button or
 * notification.
 */
class NeoIconService : AccessibilityService() {
    override fun onServiceConnected() {
        Log.i(TAG, "neo service connected")
        running = true
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        running = false
        instance = null
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

    /** Neo's activity in front, from its last window change: only `ComposeActivity` holds the icon screen. */
    private var front: String? = null

    /**
     * Neo's screen sends its events before Compose has drawn the row, so an event only starts
     * a short poll: every [POLL_MS] until the row is there, at most [POLLS] times.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Activities report their class; Neo's own popups and Compose's inner views report a view class.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className?.startsWith("com.saggitt.omega.") == true) {
            front = event.className.toString()
            if (pending != null) Log.i(TAG, "neo front $front")
        }
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

    /** Neo's icon screen for [target], when it is the window in front, else null. */
    private fun screenFor(target: NeoCustomIcons.Target): AccessibilityNodeInfo? {
        if (front != COMPOSE) return null
        val root = rootInActiveWindow?.takeIf { it.packageName == NeoCustomIcons.NEO } ?: return null
        // The screen's first text is its title, the app's name. Compose answers no text search: walk the tree.
        return root.takeIf { firstText(it) == target.label }
    }

    /** The row's icons seen so far for [seenFor], in order, while the row is walked. */
    private val seen = ArrayList<AccessibilityNodeInfo>()
    private var seenFor: NeoCustomIcons.Target? = null
    private var rewound = false
    private var walked = false
    private var walks = 0

    /**
     * True once the screen was handled: tapped, or left with Back when the row is not as
     * expected. Compose puts only the icons on screen in the tree, so with many packs the row
     * is walked: back to its start, then a page at a time to its end, keeping each icon once
     * (a node is the same node across pages). One step per call: a scroll needs a frame.
     */
    private fun tryTap(): Boolean {
        val target = pending ?: return true
        val root = screenFor(target) ?: return false
        if (seenFor != target) {
            seenFor = target
            seen.clear()
            rewound = false
            walked = false
            walks = 0
        }
        val row = topRow(root)
        if (row.isEmpty()) return false
        val scroller = scroller(row.first())
        if (!rewound) {
            rewound = true
            if (scroller != null && can(scroller, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)) {
                scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                rewound = false
                return false
            }
        }
        if (!walked) {
            for (n in row) if (n !in seen) seen += n
            if (scroller != null && can(scroller, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)) {
                scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                return false
            }
            walked = true
            if (seen.size != target.total) {
                // A page can be read mid-scroll, or the user left mid-walk: walk once more.
                if (++walks < WALKS) {
                    Log.i(TAG, "neo ${target.key}: ${seen.size} icons, expected ${target.total}: walking again")
                    seen.clear()
                    rewound = false
                    walked = false
                    return false
                }
                return done(target, false, "${seen.size} icons, expected ${target.total}")
            }
        }
        // The whole row is known; the pack's icon may have scrolled off on the way to the end.
        val node = row.firstOrNull { it == seen[target.index] }
        if (node == null) {
            if (scroller != null && can(scroller, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)) {
                scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                return false
            }
            return done(target, false, "icon ${target.index} not found")
        }
        return done(target, node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "${seen.size} icons, tap ${target.index}")
    }

    private fun done(target: NeoCustomIcons.Target, ok: Boolean, what: String): Boolean {
        pending = null
        seenFor = null
        seen.clear()
        Log.i(TAG, "neo ${target.key}: $what: $ok")
        if (ok) tapped++ else performGlobalAction(GLOBAL_ACTION_BACK)
        return true
    }

    /** The nearest scrollable node holding [n]: the row. */
    private fun scroller(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p = n.parent
        while (p != null && !p.isScrollable) p = p.parent
        return p
    }

    /** Compose lists a scroll action only while the row can move that way. */
    private fun can(n: AccessibilityNodeInfo, action: AccessibilityNodeInfo.AccessibilityAction) = action in n.actionList

    /** Leaves [target]'s icon screen with Back when it is in front and the tap never came. False when it is not in front. */
    private fun leave(target: NeoCustomIcons.Target): Boolean {
        screenFor(target) ?: return false
        if (pending == target) pending = null
        seenFor = null
        seen.clear()
        Log.i(TAG, "neo ${target.key}: no tap, Back")
        return performGlobalAction(GLOBAL_ACTION_BACK)
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

    /** [n] or a node inside it has text. */
    private fun hasText(n: AccessibilityNodeInfo): Boolean = firstText(n) != null

    /** The first non-empty text in [n]'s tree, depth first. */
    private fun firstText(n: AccessibilityNodeInfo): String? {
        n.text?.toString()?.takeIf { it.isNotEmpty() }?.let { return it }
        for (i in 0 until n.childCount) n.getChild(i)?.let(::firstText)?.let { return it }
        return null
    }

    companion object {
        private const val TAG = "Iconoclast"
        private const val COMPOSE = "${NeoCustomIcons.NEO}.compose.ComposeActivity"

        /** The app whose Neo icon screen is about to open; cleared once tapped. */
        @Volatile var pending: NeoCustomIcons.Target? = null

        /** Taps made since the app started, so a run can count what Neo took. */
        @Volatile var tapped = 0

        private const val POLL_MS = 150L
        private const val POLLS = 40
        private const val WALKS = 2

        /** The user has turned the service on in Accessibility settings. */
        @Volatile var running = false

        @Volatile private var instance: NeoIconService? = null

        /**
         * Leaves [target]'s Neo icon screen when it is still in front with no tap made. False
         * when it is not in front: the screen is locked, or the user went elsewhere. Main thread.
         */
        fun leaveIfStuck(target: NeoCustomIcons.Target): Boolean = instance?.leave(target) ?: false
    }
}
