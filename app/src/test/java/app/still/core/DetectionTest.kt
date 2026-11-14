package app.still.core

import org.junit.Assert.*
import org.junit.Test

/** Synthetic anonymous trees. They document rules, not compatibility with a live app release. */
class DetectionTest {
    private val screen = Bounds(0, 0, 1080, 2400)
    private fun tab(id: String = "", description: String = "", ancestors: List<String> = emptyList()) = UiNode(
        id, description, Bounds(400, 2180, 600, 2310), clickable = true, ancestors = ancestors,
    )
    private fun viewer(pkg: String): Detection {
        val id = if (pkg == Detectors.YOUTUBE) "reel_recycler" else "clips_viewer_view_pager"
        return Detectors.detect(pkg, listOf(UiNode("$pkg:id/$id", bounds = screen)), screen)
    }
    private fun swipe(id: String = "reel_recycler") = ScrollSignal(listOf(id), 0, 1, 1800, 2400)

    @Test fun instagramNavigationIsMasked() {
        val node = tab("com.instagram.android:id/clips_tab")
        assertEquals(listOf(node.bounds), Detectors.detect(Detectors.INSTAGRAM, listOf(node), screen).masks)
    }
    @Test fun youtubeNavigationLabelRequiresNavigationAncestor() {
        val node = tab(description = "Shorts", ancestors = listOf("com.google.android.youtube:id/pivot_bar"))
        assertEquals(1, Detectors.detect(Detectors.YOUTUBE, listOf(node), screen).masks.size)
        assertTrue(Detectors.detect(Detectors.YOUTUBE, listOf(node.copy(ancestors = emptyList())), screen).masks.isEmpty())
    }
    @Test fun russianInstagramLabelIsSupported() {
        val node = tab(description = "Видео Reels", ancestors = listOf("com.instagram.android:id/tab_bar"))
        assertEquals(1, Detectors.detect(Detectors.INSTAGRAM, listOf(node), screen).masks.size)
    }
    @Test fun ordinaryVideoAndMessagesAreNotMasked() {
        val ordinary = tab(description = "Shorts about cooking")
        val message = tab("com.instagram.android:id/direct_tab", "Сообщения")
        assertTrue(Detectors.detect(Detectors.YOUTUBE, listOf(ordinary), screen).masks.isEmpty())
        assertTrue(Detectors.detect(Detectors.INSTAGRAM, listOf(message), screen).masks.isEmpty())
    }
    @Test fun knownIdOutsideNavigationIsNotMasked() {
        val node = tab("com.instagram.android:id/clips_tab").copy(bounds = Bounds(100, 100, 300, 230))
        assertTrue(Detectors.detect(Detectors.INSTAGRAM, listOf(node), screen).masks.isEmpty())
    }
    @Test fun inlinePlayerIsNotFullScreenViewer() {
        val node = UiNode("com.google.android.youtube:id/reel_recycler", bounds = Bounds(0, 100, 1080, 700))
        assertFalse(Detectors.detect(Detectors.YOUTUBE, listOf(node), screen).viewer)
    }
    @Test fun unknownAppNeverMatches() {
        assertEquals(Detection(false, false, emptyList(), emptySet()), Detectors.detect("other.app", listOf(tab("reels_tab")), screen))
    }
    @Test fun firstConfirmedSwipeExitsOnlyOnce() {
        val guard = ScrollGuard()
        val detection = viewer(Detectors.YOUTUBE)
        guard.update(Detectors.YOUTUBE, detection, 1000)
        assertTrue(guard.shouldExit(detection, swipe(), 1800))
        assertFalse(guard.shouldExit(detection, swipe(), 2000))
    }
    @Test fun playerInitializationDoesNotExit() {
        val guard = ScrollGuard()
        val detection = viewer(Detectors.YOUTUBE)
        guard.update(Detectors.YOUTUBE, detection, 1000)
        assertFalse(guard.shouldExit(detection, swipe(), 1100))
        assertFalse(guard.shouldExit(detection, swipe().copy(from = 0, to = 0, deltaY = 0), 1800))
    }
    @Test fun commentsDoNotExitOrConsumeFirstSwipe() {
        val guard = ScrollGuard()
        val detection = viewer(Detectors.YOUTUBE)
        guard.update(Detectors.YOUTUBE, detection, 1000)
        assertFalse(guard.shouldExit(detection.copy(comments = true), swipe(), 1800))
        assertFalse(guard.shouldExit(detection, swipe().copy(sourceIds = listOf("comment_recycler", "reel_recycler")), 1800))
        assertTrue(guard.shouldExit(detection, swipe(), 1900))
    }
    @Test fun leavingAndReenteringStartsFreshSession() {
        val guard = ScrollGuard()
        val detection = viewer(Detectors.INSTAGRAM)
        guard.update(Detectors.INSTAGRAM, detection, 0)
        val oldSession = guard.sessionToken
        assertTrue(guard.shouldExit(detection, swipe("clips_viewer_view_pager"), 1000))
        guard.reset()
        guard.update(Detectors.INSTAGRAM, detection, 2000)
        assertNotEquals(oldSession, guard.sessionToken)
        assertTrue(guard.shouldExit(detection, swipe("clips_viewer_view_pager"), 3000))
    }
    @Test fun unrelatedScrollIsIgnored() {
        val guard = ScrollGuard()
        val detection = viewer(Detectors.YOUTUBE)
        guard.update(Detectors.YOUTUBE, detection, 1000)
        assertFalse(guard.shouldExit(detection, swipe("suggestions_list"), 1800))
        assertFalse(guard.shouldExit(detection, swipe().copy(sourceIds = listOf("suggestions_list", "reel_recycler")), 1800))
    }
}
