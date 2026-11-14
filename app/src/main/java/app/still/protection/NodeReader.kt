package app.still.protection

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.still.core.Bounds
import app.still.core.UiNode

/** Snapshots are ephemeral. Never log or persist text, descriptions, or the tree. */
object NodeReader {
    fun snapshot(root: AccessibilityNodeInfo): List<UiNode> {
        val result = mutableListOf<UiNode>()
        fun visit(node: AccessibilityNodeInfo, ancestors: List<String>, depth: Int) {
            if (depth > 40 || result.size >= 600 || !node.isVisibleToUser) return
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val id = node.viewIdResourceName.orEmpty()
            result += UiNode(
                id, node.contentDescription?.toString().orEmpty().take(100),
                Bounds(rect.left, rect.top, rect.right, rect.bottom), node.isClickable, node.isSelected, ancestors,
            )
            val path = (ancestors + id).takeLast(16)
            for (i in 0 until minOf(node.childCount, 100)) {
                val child = node.getChild(i) ?: continue
                try { visit(child, path, depth + 1) } finally { recycle(child) }
            }
        }
        visit(root, emptyList(), 0)
        return result
    }
    @Suppress("DEPRECATION")
    fun recycle(node: AccessibilityNodeInfo) { node.recycle() }

    fun sourcePath(source: AccessibilityNodeInfo): List<String> {
        val ids = mutableListOf(source.viewIdResourceName.orEmpty())
        var parent = source.parent
        var depth = 0
        while (parent != null && depth++ < 16) {
            val current = parent
            ids += current.viewIdResourceName.orEmpty()
            parent = current.parent
            recycle(current)
        }
        parent?.let(::recycle)
        return ids
    }
}
