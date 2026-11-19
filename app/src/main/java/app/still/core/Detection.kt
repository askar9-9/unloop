package app.still.core

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}
data class UiNode(
    val id: String = "",
    val description: String = "",
    val bounds: Bounds,
    val clickable: Boolean = false,
    val selected: Boolean = false,
    val ancestors: List<String> = emptyList(),
    /** Identity of the underlying view, stable while the same view stays on screen. */
    val key: Int = 0,
)
/**
 * [page] is the key of the settled, fully visible video page view; null mid-swipe or when unknown.
 * [title] is a hash of that page's title label, null until YouTube has drawn it.
 */
data class Detection(
    val viewer: Boolean, val comments: Boolean, val masks: List<Bounds>, val pagerIds: Set<String>,
    val page: Int? = null, val title: Int? = null,
)

/** Conservative, version-dependent signatures. These are candidates, not device-verified fixtures. */
object Detectors {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"
    val packages = setOf(INSTAGRAM, YOUTUBE)
    private data class Profile(val pagers: Set<String>, val tabs: Set<String>, val labels: Set<String>,
        val pages: Set<String> = emptySet(),
    )
    private val profiles = mapOf(
        INSTAGRAM to Profile(
            setOf("clips_viewer_view_pager", "clips_viewer_viewpager", "clips_viewer_recycler_view"),
            setOf("clips_tab", "reels_tab"), setOf("reels", "видео reels"),
        ),
        YOUTUBE to Profile(
            setOf("reel_recycler", "reel_watch_fragment_root"),
            setOf("shorts_tab"), setOf("shorts"),
            // YouTube 21.x sends no scroll events in Shorts; a swipe shows up as a different page view.
            // It may also reuse the same page view and only swap its content, so the title area is part of the identity.
            setOf("reel_player_page_container"),
        ),
    )
    fun localId(id: String) = id.substringAfterLast('/')
    fun isComment(id: String): Boolean = localId(id).let {
        it.contains("comment", ignoreCase = true) || it.contains("replies", ignoreCase = true)
    }
    fun detect(packageName: String, nodes: List<UiNode>, screen: Bounds): Detection {
        val profile = profiles[packageName] ?: return Detection(false, false, emptyList(), emptySet())
        val pagerNodes = nodes.filter {
            localId(it.id) in profile.pagers && it.bounds.height > screen.height * 0.55 && it.bounds.width > screen.width * 0.7
        }
        val viewer = pagerNodes.isNotEmpty()
        val comments = nodes.any { isComment(it.id) && it.bounds.height > screen.height * 0.25 }
        val masks = if (viewer) emptyList() else nodes.filter { node ->
            val b = node.bounds
            val inNavigation = b.top >= screen.top + screen.height * 0.72 && b.bottom <= screen.bottom &&
                b.left >= screen.left && b.right <= screen.right && b.width in 1..(screen.width / 3) &&
                b.height in 1..(screen.height / 6)
            val label = node.description.trim().lowercase(java.util.Locale.ROOT)
            val knownId = localId(node.id) in profile.tabs
            val navigationAncestor = node.ancestors.any {
                val id = localId(it)
                id.contains("tab_bar") || id.contains("bottom_nav") || id.contains("pivot_bar") || id == "tabs"
            }
            inNavigation && (knownId || (node.clickable && label in profile.labels && navigationAncestor))
        }.map { it.bounds }.distinct()
        val page = if (!viewer) null else nodes.singleOrNull {
            val b = it.bounds
            localId(it.id) in profile.pages && b.height > screen.height * 0.55 && b.top >= screen.top && b.bottom <= screen.bottom
        }
        // Longest label in the page's bottom-left corner, left of the counters column and narrower than the time bar:
        // the title, or the channel when there is none. Found by position because Android hides the footer
        // container from accessibility services. Only its hash is kept.
        val title = page?.bounds?.let { p ->
            nodes.filter { n ->
                val b = n.bounds
                n.description.isNotBlank() && b.top >= p.top + p.height * 3 / 4 && b.bottom <= p.bottom &&
                    b.left < p.left + p.width * 4 / 5 && b.right <= p.left + p.width * 9 / 10
            }.maxByOrNull { it.description.length }?.description?.hashCode()
        }
        return Detection(viewer, comments, masks, pagerNodes.map { localId(it.id) }.toSet(), page?.key, title)
    }
}

data class ScrollSignal(
    val sourceIds: List<String>, val from: Int, val to: Int,
    val deltaY: Int, val sourceHeight: Int,
)

class ScrollGuard {
    private var session: String? = null
    private var enteredAt = 0L
    private var blocked = false
    private var firstPage: Int? = null
    private var firstTitle: Int? = null
    var sessionToken: Long = 0
        private set

    fun reset() { session = null; blocked = false; firstPage = null; firstTitle = null; sessionToken++ }
    fun update(packageName: String, detection: Detection, now: Long) {
        if (!detection.viewer) { reset(); return }
        if (session != packageName) { session = packageName; enteredAt = now; blocked = false; firstPage = null; firstTitle = null; sessionToken++ }
        val page = detection.page ?: return
        // The player may rebind its first page while it initializes, and draws the title whenever it is ready.
        if (firstPage == null || now - enteredAt < 700) { firstPage = page; firstTitle = detection.title }
        else if (page == firstPage && firstTitle == null) firstTitle = detection.title
    }
    fun pageChanged(detection: Detection, now: Long): Boolean {
        if (session == null || blocked || !detection.viewer || detection.comments || now - enteredAt < 700) return false
        val first = firstPage ?: return false
        val current = detection.page ?: return false
        val otherTitle = firstTitle != null && detection.title != null && detection.title != firstTitle
        if (current == first && !otherTitle) return false
        blocked = true
        return true
    }
    fun shouldExit(detection: Detection, signal: ScrollSignal, now: Long): Boolean {
        if (session == null || blocked || !detection.viewer || detection.comments || now - enteredAt < 700) return false
        if (signal.sourceIds.any(Detectors::isComment)) return false
        // A scrollable child (e.g. suggestions) inside a pager is not the pager itself.
        val sourceId = signal.sourceIds.firstOrNull() ?: return false
        if (Detectors.localId(sourceId) !in detection.pagerIds) return false
        val indexedMove = signal.from >= 0 && signal.to >= 0 && signal.from != signal.to
        val pageMove = signal.sourceHeight > 0 && kotlin.math.abs(signal.deltaY.toLong()) > signal.sourceHeight * 0.35
        if (!indexedMove && !pageMove) return false
        blocked = true
        return true
    }
}
