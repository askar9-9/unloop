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
    // The footer container is not exposed to accessibility services, so labels carry no footer ancestor.
    private val footer = emptyList<String>()
    private fun shortsPage(key: Int, top: Int = 63, title: String? = "Video A", subscribe: String = "Subscribe to @a") =
        Detectors.detect(Detectors.YOUTUBE, listOfNotNull(
            UiNode("${Detectors.YOUTUBE}:id/reel_recycler", bounds = Bounds(0, 63, 1080, 2211)),
            UiNode("${Detectors.YOUTUBE}:id/reel_player_page_container", bounds = Bounds(0, top, 1080, top + 2148), key = key),
            UiNode(description = subscribe, bounds = Bounds(486, 2014, 692, 2098), ancestors = footer).takeIf { title != null },
            title?.let { UiNode(description = "$it #shorts #funny", bounds = Bounds(42, 2120, 912, 2168), ancestors = footer) },
            UiNode(description = "like this video along with 42 thousand other people", bounds = Bounds(912, 1252, 1080, 1410), ancestors = footer),
        ), screen)

    @Test fun lateTitleOnTheSameShortDoesNotExit() {
        val guard = ScrollGuard()
        guard.update(Detectors.YOUTUBE, shortsPage(1, title = null), 1000)
        val drawn = shortsPage(1)
        guard.update(Detectors.YOUTUBE, drawn, 2500)
        assertFalse(guard.pageChanged(drawn, 2500))
        val next = shortsPage(1, title = "Video B")
        guard.update(Detectors.YOUTUBE, next, 4000)
        assertTrue(guard.pageChanged(next, 4000))
    }

    @Test fun shortsContentSwapInSameViewExits() {
        val guard = ScrollGuard()
        guard.update(Detectors.YOUTUBE, shortsPage(1), 1000)
        val next = shortsPage(1, title = "Video B")
        guard.update(Detectors.YOUTUBE, next, 2000)
        assertTrue(guard.pageChanged(next, 2000))
    }
    @Test fun subscribingOnTheSameShortDoesNotExit() {
        val guard = ScrollGuard()
        guard.update(Detectors.YOUTUBE, shortsPage(1), 1000)
        val same = shortsPage(1, subscribe = "Unsubscribe from @a")
        guard.update(Detectors.YOUTUBE, same, 2000)
        assertFalse(guard.pageChanged(same, 2000))
    }

    @Test fun shortsPageChangeExitsWithoutScrollEvents() {
        val guard = ScrollGuard()
        guard.update(Detectors.YOUTUBE, shortsPage(1), 1000)
        assertFalse(guard.pageChanged(shortsPage(1), 1800))
        val next = shortsPage(2)
        guard.update(Detectors.YOUTUBE, next, 2000)
        assertTrue(guard.pageChanged(next, 2000))
        assertFalse(guard.pageChanged(next, 2100))
    }
    @Test fun shortsPageRebindDuringStartupIsNotASwipe() {
        val guard = ScrollGuard()
        guard.update(Detectors.YOUTUBE, shortsPage(1), 1000)
        guard.update(Detectors.YOUTUBE, shortsPage(2), 1300)
        assertFalse(guard.pageChanged(shortsPage(2), 1800))
    }
    @Test fun pageMidSwipeIsUnknown() {
        assertNull(shortsPage(2, top = 900).page)
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
