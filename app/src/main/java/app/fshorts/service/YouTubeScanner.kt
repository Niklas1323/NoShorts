package app.fshorts.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class TabInfo(
    val kind: TabKind,
    val bounds: Rect,
    val label: String?,
    val selected: Boolean,
    val badge: Boolean,
    val avatarBounds: Rect?,
)

class FoundBar(
    val bounds: Rect,
    val tabs: Map<TabKind, TabInfo>,
    val nodes: Map<TabKind, AccessibilityNodeInfo>,
)

class Shelf(
    val bounds: Rect,
    val menuButton: AccessibilityNodeInfo?,
)

/**
 * Sucht in der YouTube-Oberfläche die untere Leiste, ihre Tabs und den Shorts-Player.
 * Verlässt sich nicht nur auf View-IDs: Wenn die ID nicht passt, suchen wir über die
 * Beschriftungen ("Shorts", "Startseite", ...) und die Position am unteren Rand.
 */
class YouTubeScanner(private val sel: Selectors, private val density: Float) {

    private var learnedBarId: String? = null
    private val maxBarHeight get() = (sel.maxBarHeightDp * density).toInt()

    fun findBar(root: AccessibilityNodeInfo, screen: Rect): FoundBar? {
        val ids = (listOfNotNull(learnedBarId) + sel.barIds.map(sel::fullId)).distinct()
        for (id in ids) {
            for (node in root.findAccessibilityNodeInfosByViewId(id)) {
                buildBar(node, screen)?.let { return it }
            }
        }
        for (label in sel.searchLabels) {
            for (hit in root.findAccessibilityNodeInfosByText(label)) {
                val container = climbToBar(hit, screen) ?: continue
                val bar = buildBar(container, screen) ?: continue
                container.viewIdResourceName?.let { learnedBarId = it }
                return bar
            }
        }
        return null
    }

    fun isShortsScreen(root: AccessibilityNodeInfo, bar: FoundBar?, screen: Rect): Boolean {
        if (bar?.tabs?.get(TabKind.SHORTS)?.selected == true) return true
        val screenArea = screen.width().toLong() * screen.height()
        val r = Rect()
        for (id in sel.shortsPlayerIds) {
            for (node in root.findAccessibilityNodeInfosByViewId(sel.fullId(id))) {
                if (!node.isVisibleToUser) continue
                node.getBoundsInScreen(r)
                if (r.width().toLong() * r.height() >= screenArea / 2) return true
            }
        }
        return false
    }

    /** True, wenn z. B. der grosse Video-Player über der Leiste liegt. */
    fun isBarCovered(root: AccessibilityNodeInfo, bar: FoundBar): Boolean {
        val r = Rect()
        for (id in sel.coverIds) {
            for (node in root.findAccessibilityNodeInfosByViewId(sel.fullId(id))) {
                if (!node.isVisibleToUser) continue
                node.getBoundsInScreen(r)
                if (Rect.intersects(r, bar.bounds)) return true
            }
        }
        return false
    }

    /**
     * Findet Shorts-Regale (Überschrift "Shorts" + Karten) in der scrollbaren Liste.
     * [limitBottom] ist die Oberkante unserer Leiste, darunter wird nichts abgedeckt.
     */
    fun findShelves(root: AccessibilityNodeInfo, screen: Rect, limitBottom: Int): List<Shelf> {
        val result = mutableListOf<Shelf>()
        for (label in sel.shelfHeaderLabels) {
            for (hit in root.findAccessibilityNodeInfosByText(label)) {
                if (!hit.isVisibleToUser) continue
                if (!ownLabel(hit).equals(label, ignoreCase = true)) continue
                val hb = hit.bounds()
                if (hb.top >= limitBottom || hb.left > screen.width() * 0.4) continue
                val shelf = shelfFor(hit, hb, screen, limitBottom) ?: continue
                if (result.none { Rect.intersects(it.bounds, shelf.bounds) }) result += shelf
            }
        }
        return result
    }

    private fun shelfFor(hit: AccessibilityNodeInfo, headerBounds: Rect, screen: Rect, limitBottom: Int): Shelf? {
        val path = mutableListOf<AccessibilityNodeInfo>()
        var node: AccessibilityNodeInfo? = hit
        while (node != null && path.size < 40) {
            path += node
            node = node.parent
        }
        val feedIdx = path.indexOfFirst { it.isScrollable && it.bounds().height() >= screen.height() * 0.4 }
        if (feedIdx <= 0) return null
        // Liegt die Überschrift in einer seitlich scrollbaren Leiste (z. B. Kanal-Tabs), ist es kein Regal.
        if (path.subList(0, feedIdx).any { it.isScrollable }) return null

        val feed = path[feedIdx]
        val item = path[feedIdx - 1]
        val bounds = item.bounds()
        if (bounds.height() < 160 * density) {
            // Überschrift und Karten sind getrennte Einträge in der Liste: den nächsten Eintrag dazunehmen.
            val index = (0 until feed.childCount).firstOrNull { feed.getChild(it) == item }
            index?.let { feed.getChild(it + 1) }?.let { bounds.union(it.bounds()) }
        }
        if (bounds.height() > screen.height() * 0.8) return null
        if (!bounds.intersect(feed.bounds())) return null
        if (bounds.bottom > limitBottom) bounds.bottom = limitBottom
        if (bounds.height() <= 0) return null

        return Shelf(bounds, findMenuButton(item, headerBounds, screen, 0))
    }

    /** Der ⋮-Knopf rechts neben der Überschrift "Shorts". */
    private fun findMenuButton(node: AccessibilityNodeInfo, header: Rect, screen: Rect, depth: Int): AccessibilityNodeInfo? {
        val r = node.bounds()
        val tolerance = (40 * density).toInt()
        val nearHeader = r.centerY() in (header.top - tolerance)..(header.bottom + tolerance)
        if (node.isClickable && nearHeader && r.centerX() > screen.width() * 0.8 && r.width() <= 72 * density) {
            return node
        }
        if (depth >= 8) return null
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> findMenuButton(child, header, screen, depth + 1)?.let { return it } }
        }
        return null
    }

    /** Sucht in allen YouTube-Fenstern einen Menüpunkt wie "Nicht interessiert". */
    fun findMenuItem(roots: List<AccessibilityNodeInfo>, labels: List<String>): AccessibilityNodeInfo? {
        for (root in roots) {
            for (label in labels) {
                for (hit in root.findAccessibilityNodeInfosByText(label)) {
                    val own = ownLabel(hit) ?: continue
                    if (hit.isVisibleToUser && own.equals(label, ignoreCase = true)) return hit
                }
            }
        }
        return null
    }

    private fun AccessibilityNodeInfo.bounds(): Rect = Rect().also { getBoundsInScreen(it) }

    private fun climbToBar(hit: AccessibilityNodeInfo, screen: Rect): AccessibilityNodeInfo? {
        if (!hit.isVisibleToUser) return null
        val r = Rect()
        hit.getBoundsInScreen(r)
        if (r.top < screen.height() * 0.6) return null
        var best: AccessibilityNodeInfo? = null
        var node: AccessibilityNodeInfo? = hit
        var depth = 0
        while (node != null && depth < 8) {
            node.getBoundsInScreen(r)
            if (r.height() > maxBarHeight) break
            if (r.width() >= screen.width() * 0.8) best = node
            node = node.parent
            depth++
        }
        return best
    }

    private fun buildBar(container: AccessibilityNodeInfo, screen: Rect): FoundBar? {
        if (!container.isVisibleToUser) return null
        val b = Rect()
        container.getBoundsInScreen(b)
        if (b.width() < screen.width() * 0.8) return null
        if (b.height() > maxBarHeight || b.height() < 24 * density) return null
        if (b.top < screen.height() * 0.6) return null

        val tabs = mutableMapOf<TabKind, TabInfo>()
        val nodes = mutableMapOf<TabKind, AccessibilityNodeInfo>()
        collectTabs(container, 0, b, tabs, nodes)
        if (tabs.size < 2) return null
        if (TabKind.HOME !in tabs && TabKind.SUBSCRIPTIONS !in tabs) return null
        return FoundBar(Rect(b), tabs, nodes)
    }

    private fun collectTabs(
        node: AccessibilityNodeInfo,
        depth: Int,
        barBounds: Rect,
        tabs: MutableMap<TabKind, TabInfo>,
        nodes: MutableMap<TabKind, AccessibilityNodeInfo>,
    ) {
        val r = Rect()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            child.getBoundsInScreen(r)
            val tabSized = r.width() in 1..(barBounds.width() / 2)
            if (tabSized && (child.isClickable || ownLabel(child) != null)) {
                val kind = classify(deepLabel(child, 0))
                if (kind != null && kind !in tabs) {
                    tabs[kind] = TabInfo(
                        kind = kind,
                        bounds = Rect(r),
                        label = deepText(child, 0),
                        selected = isSelectedDeep(child, 0),
                        badge = hasBadge(child),
                        avatarBounds = if (kind == TabKind.YOU) findAvatar(child, 0) else null,
                    )
                    nodes[kind] = child
                }
                if (child.isClickable) continue
            }
            if (depth < 6) collectTabs(child, depth + 1, barBounds, tabs, nodes)
        }
    }

    private fun classify(label: String?): TabKind? {
        val l = label?.trim()?.lowercase() ?: return null
        val order = listOf(TabKind.SHORTS, TabKind.HOME, TabKind.SUBSCRIPTIONS, TabKind.CREATE, TabKind.YOU)
        for (kind in order) {
            for (candidate in sel.labels[kind].orEmpty()) {
                val c = candidate.lowercase()
                if (l == c || l.startsWith("$c ") || l.startsWith("$c,") || l.startsWith("$c:")) return kind
            }
        }
        return null
    }

    private fun ownLabel(node: AccessibilityNodeInfo): String? =
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun deepLabel(node: AccessibilityNodeInfo, depth: Int): String? {
        ownLabel(node)?.let { return it }
        if (depth >= 4) return null
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> deepLabel(child, depth + 1)?.let { return it } }
        }
        return null
    }

    /** Sichtbarer Text unter dem Icon, z. B. "Abos" (ohne Zusatz wie "neue Inhalte"). */
    private fun deepText(node: AccessibilityNodeInfo, depth: Int): String? {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        if (depth >= 4) return null
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> deepText(child, depth + 1)?.let { return it } }
        }
        return null
    }

    private fun isSelectedDeep(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (node.isSelected) return true
        if (depth >= 3) return false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (isSelectedDeep(child, depth + 1)) return true
        }
        return false
    }

    private fun hasBadge(tab: AccessibilityNodeInfo): Boolean {
        val label = deepLabel(tab, 0)?.lowercase().orEmpty()
        if (sel.badgeTextHints.any { label.contains(it) }) return true
        return hasBadgeView(tab, 0)
    }

    private fun hasBadgeView(node: AccessibilityNodeInfo, depth: Int): Boolean {
        val id = node.viewIdResourceName?.substringAfter(":id/")?.lowercase()
        if (id != null && node.isVisibleToUser && sel.badgeIdHints.any { id.contains(it) }) return true
        if (depth >= 4) return false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasBadgeView(child, depth + 1)) return true
        }
        return false
    }

    private fun findAvatar(node: AccessibilityNodeInfo, depth: Int): Rect? {
        val r = Rect()
        node.getBoundsInScreen(r)
        val isImage = node.className?.contains("Image") == true
        val roughlySquare = r.width() > 0 && kotlin.math.abs(r.width() - r.height()) <= r.width() / 4
        if (isImage && roughlySquare && r.width() <= 48 * density && node.isVisibleToUser) return r
        if (depth >= 4) return null
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> findAvatar(child, depth + 1)?.let { return it } }
        }
        return null
    }
}
