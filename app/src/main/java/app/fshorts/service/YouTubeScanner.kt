package app.fshorts.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class TabInfo(
    val kind: TabKind,
    val bounds: Rect,
    val label: String?,
    val labelBounds: Rect?,
    val iconBounds: Rect?,
    val selected: Boolean,
    val badge: Boolean,
)

class FoundBar(
    val container: AccessibilityNodeInfo,
    val bounds: Rect,
    val tabs: Map<TabKind, TabInfo>,
    val nodes: Map<TabKind, AccessibilityNodeInfo>,
)

class Shelf(
    val bounds: Rect,
    val menuButton: AccessibilityNodeInfo?,
    val parts: List<AccessibilityNodeInfo>,
    val feed: AccessibilityNodeInfo,
    /** ⋮ der einzelnen Short-Karten (Regal ohne eigenes ⋮, z. B. auf "Abos": Karten einzeln ausblenden). */
    val cardMenuButtons: List<AccessibilityNodeInfo> = emptyList(),
)

// Shorts-Player von YouTube 21.41 (am Pixel nachgesehen): eine Seite pro Short in einer senkrechten Liste.
private const val SHORTS_PAGE_ID = "reel_player_page_container"
private const val EDIT_SUGGESTION_ID = "edit_suggestion"
private val SHEET_IDS = listOf("design_bottom_sheet", "touch_outside")
// Panel im YouTube-Fenster selbst (Werbe-Menü, auf der Video-Seite auch Kommentare), YouTube 21.42.
private const val PANEL_ID = "app_engagement_panel"

/**
 * Sucht in der YouTube-Oberfläche die untere Leiste, ihre Tabs und den Shorts-Player.
 * Verlässt sich nicht nur auf View-IDs: Wenn die ID nicht passt, suchen wir über die
 * Beschriftungen ("Shorts", "Startseite", ...) und die Position am unteren Rand.
 */
class YouTubeScanner(private val sel: Selectors, private val density: Float, private val tablet: Boolean = false) {

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
        return isShortsPlayerOpen(root, screen)
    }

    /** Nur der Shorts-Player (ohne Leiste zu suchen): schnell genug, um es direkt nach einem Tipp zu prüfen. */
    fun isShortsPlayerOpen(root: AccessibilityNodeInfo, screen: Rect): Boolean {
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

    /**
     * Woran man den gerade sichtbaren Short erkennt: Kanal und Titel (linke Spalte unten, z. B. "Zu Kanal „@X“").
     * Rechts stehen Like-Zahl & Co., die sich während des Ansehens ändern können – die zählen nicht.
     * Null, solange der Short noch lädt.
     */
    fun shortFingerprint(root: AccessibilityNodeInfo, screen: Rect): String? {
        val r = Rect()
        val page = root.findAccessibilityNodeInfosByViewId(sel.fullId(SHORTS_PAGE_ID))
            .filter { it.isVisibleToUser }
            .maxByOrNull { it.getBoundsInScreen(r); r.height() } ?: return null
        // Kanal = Bild mit Beschreibung ("Zu Kanal „@X“"), Titel = erste Beschreibung darunter.
        // Etiketten darüber ("Synchronisiert", "Shop") kommen und gehen und zählen darum nicht.
        var channel: String? = null
        var channelBottom = 0
        var title: String? = null
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            // Kanal und Titel liegen bei YouTube ~20 Ebenen tief.
            if (depth > 40 || title != null) return
            val desc = node.contentDescription?.toString()
            if (!desc.isNullOrBlank()) {
                node.getBoundsInScreen(r)
                if (r.left < screen.width() / 2 && r.right < screen.width() * 0.9f && r.top > screen.centerY()) {
                    // Kanalbild: klein und quadratisch. Bei Collab-Shorts ist es eine Gruppe ("Collab-Kanäle")
                    // statt eines Bildes; breite Etiketten zählen so trotzdem nicht.
                    val avatar = node.className?.toString()?.endsWith("ImageView") == true ||
                        (kotlin.math.abs(r.width() - r.height()) <= 8 && r.width() < screen.width() / 6)
                    if (channel == null && avatar) {
                        channel = desc
                        channelBottom = r.bottom
                    } else if (channel != null && r.top >= channelBottom - 2) {
                        title = desc
                    }
                }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i) ?: continue, depth + 1)
        }
        walk(page, 0)
        return channel?.let { c -> title?.let { "$c|$it" } ?: c }
    }

    /**
     * True, wenn ein Video gross offen ist (Wiedergabe-Seite): oben ein Player über die ganze Breite.
     * Der kleine Mini-Player unten zählt nicht.
     */
    fun findWatchPlayer(root: AccessibilityNodeInfo, screen: Rect): AccessibilityNodeInfo? {
        for (id in sel.watchIds) {
            for (node in root.findAccessibilityNodeInfosByViewId(sel.fullId(id))) {
                if (isLargePlayer(node, screen)) return node
            }
        }
        return null
    }

    /**
     * Prüft einen schon gefundenen Player neu (eine einzige Abfrage, schnell genug für jedes Bild).
     * Beim Verlassen des Videos rutscht die ganze Video-Seite nach unten weg: Sobald der Player tiefer
     * steht als beim Öffnen ([openTop]), ist das Video am Zugehen.
     */
    fun isStillLargePlayer(node: AccessibilityNodeInfo, openTop: Int, screen: Rect): Boolean {
        if (!node.refresh() || !isLargePlayer(node, screen)) return false
        return node.bounds().top <= openTop + 4 * density
    }

    fun topOf(node: AccessibilityNodeInfo): Int = node.bounds().top

    private fun isLargePlayer(node: AccessibilityNodeInfo, screen: Rect): Boolean {
        if (!node.isVisibleToUser) return false
        val r = node.bounds()
        // Tablet quer: Video-Seite mit zwei Spalten, der Player ist nur ~70 % breit (Handy: ganze Breite).
        val wide = r.width() >= screen.width() * (if (tablet) 0.6 else 0.9)
        val nearTop = r.top - screen.top < screen.height() * 0.35
        val playerSized = r.height() >= 150 * density && r.height() <= screen.height() * 0.7
        // Vollbild (z. B. quer): Der Player füllt den ganzen Bildschirm, das Video ist weiter offen.
        val fullscreen = r.top <= screen.top + 2 && r.height() >= screen.height() * 0.95
        return wide && nearTop && (playerSized || fullscreen)
    }

    /**
     * True, wenn unten der kleine Mini-Player zu sehen ist. Sucht nach Elementen, deren ID "miniplayer"
     * enthält (YouTube nennt sie z. B. modern_miniplayer_close). Mit [allowWalk] geht sie notfalls den
     * ganzen Baum durch, darum dann selten aufrufen.
     */
    fun isMiniPlayerVisible(root: AccessibilityNodeInfo, screen: Rect, allowWalk: Boolean): Boolean {
        // Am schnellsten: die bekannten IDs (auf dem Pixel nachgesehen, YouTube 21.41).
        for (id in sel.miniPlayerIds) {
            for (node in root.findAccessibilityNodeInfosByViewId(sel.fullId(id))) {
                if (node.isVisibleToUser && node.bounds().centerY() > screen.centerY()) return true
            }
        }
        // Schnell: kleine Knöpfe (Schliessen, Pause) unten rechts, wie beim Mini-Player.
        val maxButton = 72 * density
        for (label in sel.miniPlayerLabels) {
            for (hit in root.findAccessibilityNodeInfosByText(label)) {
                if (!hit.isVisibleToUser) continue
                val b = hit.bounds()
                val small = b.width() <= maxButton && b.height() <= maxButton
                if (small && b.centerX() > screen.width() * 0.45 && b.centerY() - screen.top > screen.height() * 0.45) return true
            }
        }
        // Langsam (bis zu 300 ms, wenn YouTube gerade umbaut): nur ab und zu, falls YouTube die IDs ändert.
        if (!allowWalk) return false
        var checked = 0
        val r = Rect()
        fun walk(node: AccessibilityNodeInfo, depth: Int): Boolean {
            if (checked++ > 3000 || depth > 40) return false
            val id = node.viewIdResourceName?.lowercase()
            if (id != null && (id.contains("miniplayer") || id.contains("mini_player")) && node.isVisibleToUser) {
                node.getBoundsInScreen(r)
                if (r.height() in 1 until screen.height() / 2) return true
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (walk(child, depth + 1)) return true
            }
            return false
        }
        return walk(root, 0)
    }

    /** True, wenn z. B. der grosse Video-Player über der Leiste liegt. */
    /** Ein Menü von unten (z. B. nach ⋮) in einem eigenen Fenster. */
    fun isBottomSheet(windowRoot: AccessibilityNodeInfo): Boolean =
        SHEET_IDS.any { windowRoot.findAccessibilityNodeInfosByViewId(sel.fullId(it)).isNotEmpty() }

    /** Oberkante des Menüs (es fährt von unten rein und wieder raus), oder null, wenn es keins gibt. */
    fun sheetTop(windowRoot: AccessibilityNodeInfo): Int? {
        val r = Rect()
        return windowRoot.findAccessibilityNodeInfosByViewId(sel.fullId(SHEET_IDS[0]))
            .filter { it.isVisibleToUser }
            .minOfOrNull { it.getBoundsInScreen(r); r.top }
    }

    /** Ganze Fläche des Menüs (für das "Loch" in unserer abgedunkelten Leiste), oder null. */
    fun sheetBounds(windowRoot: AccessibilityNodeInfo): Rect? {
        val r = Rect()
        return windowRoot.findAccessibilityNodeInfosByViewId(sel.fullId(SHEET_IDS[0]))
            .filter { it.isVisibleToUser }
            .map { it.getBoundsInScreen(r); Rect(r) }
            .filter { !it.isEmpty }
            .maxByOrNull { it.width().toLong() * it.height() }
    }

    /**
     * Oberkante eines Panels, das von unten im YouTube-Fenster selbst hochfährt (z. B. "Mein Anzeigen-Center"
     * nach ⋮ bei Werbung), oder null. Solange es offen ist, gibt YouTube seine Leiste nicht heraus.
     */
    fun panelTop(root: AccessibilityNodeInfo, screen: Rect): Int? {
        val r = Rect()
        return root.findAccessibilityNodeInfosByViewId(sel.fullId(PANEL_ID))
            .filter { it.isVisibleToUser }
            .map { it.getBoundsInScreen(r); Rect(r) }
            .filter { it.width() >= screen.width() * 0.9 && it.bottom >= screen.bottom - 2 && it.top < screen.bottom - 2 }
            .minOfOrNull { it.top }
    }

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
        val feed = findFeed(root, screen)
        for (label in (sel.shelfHeaderLabels + listOfNotNull(learnedShortsWord)).distinct()) {
            // Manche Überschriften (z. B. auf "Abos") haben nur eine Beschreibung, die findet die Textsuche
            // von Android nicht: dann in den Einträgen der Liste selbst nachsehen.
            val hits = root.findAccessibilityNodeInfosByText(label) + (feed?.let { headersIn(it, label) } ?: emptyList())
            for (hit in hits) {
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

    /** Überschriften [label] in den sichtbaren Einträgen der Liste (bis 3 Ebenen tief). */
    private fun headersIn(feed: AccessibilityNodeInfo, label: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (ownLabel(node).equals(label, ignoreCase = true)) {
                result += node
                return
            }
            if (depth >= 3) return
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (child.isVisibleToUser) walk(child, depth + 1)
            }
        }
        for (i in 0 until feed.childCount) {
            val item = feed.getChild(i) ?: continue
            if (item.isVisibleToUser) walk(item, 1)
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
        // Suchvorschlag "shorts" (Verlauf) ist kein Regal. Sonst tippten wir auf seinen Pfeil (füllt das Suchfeld) und
        // gaben auf: Zurück, und bis zum Neustart wurden gar keine Regale mehr ausgeblendet.
        if (item.findAccessibilityNodeInfosByViewId(sel.fullId(EDIT_SUGGESTION_ID)).isNotEmpty()) return null
        val parts = mutableListOf(item)
        val bounds = item.bounds()
        if (bounds.height() < 160 * density) {
            // Überschrift und Karten sind getrennte Einträge in der Liste: den nächsten Eintrag dazunehmen.
            // Die Liste ändert sich beim Scrollen laufend: Kinder können zwischen Zählen und Abfragen wegfallen.
            val index = (0 until feed.childCount).firstOrNull { runCatching { feed.getChild(it) }.getOrNull() == item }
            index?.let { runCatching { feed.getChild(it + 1) }.getOrNull() }?.let {
                bounds.union(it.bounds())
                parts += it
            }
        }
        if (bounds.height() > screen.height() * 0.8) return null
        if (!bounds.intersect(feed.bounds())) return null
        if (bounds.bottom > limitBottom) bounds.bottom = limitBottom
        if (bounds.height() <= 0) return null

        limitBottomForCards = limitBottom
        val menu = findMenuButton(item, headerBounds, screen, 0)
        val cardMenus = if (menu == null) {
            mutableListOf<AccessibilityNodeInfo>().also { out ->
                parts.forEach { findCardMenus(it, headerBounds, screen, 0, false, out) }
            }
        } else {
            emptyList()
        }
        return Shelf(bounds, menu, parts, feed, cardMenus)
    }

    /** Karten-⋮ nur über unserer Leiste (darunter liegt nichts Tippbares von uns). */
    private var limitBottomForCards = Int.MAX_VALUE

    private fun opensVideo(node: AccessibilityNodeInfo): Boolean {
        val d = node.contentDescription?.toString()?.lowercase() ?: return false
        return sel.playHints.any { d.contains(it) }
    }

    /** Kleine ⋮-Knöpfe auf den Karten unter der Überschrift (ganz sichtbar, nicht hinter unserer Leiste). */
    private fun findCardMenus(
        node: AccessibilityNodeInfo,
        header: Rect,
        screen: Rect,
        depth: Int,
        inRow: Boolean,
        out: MutableList<AccessibilityNodeInfo>,
    ) {
        val r = node.bounds()
        val small = r.width() in 1..(72 * density).toInt() && r.height() in 1..(72 * density).toInt()
        // Nur in der seitlich scrollbaren Kartenreihe des Regals: Das ⋮ eines normalen Videos darunter gehört nie dazu.
        if (inRow && node.isClickable && small && node.isVisibleToUser && r.top > header.bottom &&
            r.right <= screen.right && r.bottom <= limitBottomForCards && !opensVideo(node)
        ) {
            out += node
            return
        }
        if (depth >= 10) return
        val row = inRow || (node.isScrollable && r.width() >= screen.width() / 2 && r.height() < screen.height() / 2)
        for (i in 0 until node.childCount) {
            runCatching { node.getChild(i) }.getOrNull()?.let { findCardMenus(it, header, screen, depth + 1, row, out) }
        }
    }

    /** Neue Position eines bekannten Regals, ohne den ganzen Bildschirm neu zu durchsuchen (fürs Scrollen). */
    fun refreshShelf(shelf: Shelf, limitBottom: Int): Rect? {
        if (!shelf.feed.refresh()) return null
        val bounds = Rect()
        for (part in shelf.parts) {
            if (!part.refresh()) return null
            if (part.isVisibleToUser) bounds.union(part.bounds())
        }
        if (bounds.isEmpty || !bounds.intersect(shelf.feed.bounds())) return null
        if (bounds.bottom > limitBottom) bounds.bottom = limitBottom
        return if (bounds.height() > 0) bounds else null
    }

    /**
     * Wo die Oberkante der echten Leiste gerade ist. YouTube schiebt die Leiste beim Runterscrollen nach unten.
     * null = Leiste gibt es nicht mehr, Bildschirmunterkante = komplett versteckt.
     */
    fun liveTop(container: AccessibilityNodeInfo, screen: Rect): Int? {
        if (!container.refresh()) return null
        if (!container.isVisibleToUser) return screen.bottom
        val r = container.bounds()
        if (r.isEmpty || r.top >= screen.bottom) return screen.bottom
        return r.top
    }

    /**
     * Neues YouTube-Design: "Startseite | Abos | …" als Reiter oben auf der Startseite statt Abos in der Leiste.
     * Sucht den Reiter (sichtbarer Text, oberes Drittel des Bildschirms).
     */
    fun findTopTab(root: AccessibilityNodeInfo, screen: Rect, kind: TabKind, barLabel: String?): AccessibilityNodeInfo? {
        val words = (listOfNotNull(barLabel) + sel.labels[kind].orEmpty().take(2)).distinct()
        val r = Rect()
        for (word in words) {
            val hits = runCatching { root.findAccessibilityNodeInfosByText(word) }.getOrNull() ?: continue
            for (n in hits) {
                if (!n.text?.toString()?.trim().equals(word, ignoreCase = true) || !n.isVisibleToUser) continue
                n.getBoundsInScreen(r)
                if (r.top >= screen.top && r.bottom < screen.top + screen.height() / 3) return n
            }
        }
        return null
    }

    /** Reiter gewählt? Das steht am Text selbst oder an einem der zwei Elemente darüber. */
    fun isSelectedNear(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(3) {
            val cur = n ?: return false
            if (cur.isSelected) return true
            n = cur.parent
        }
        return false
    }

    /**
     * Die grosse senkrechte Liste der aktuellen Seite (Startseite, Abos ...). Solange es sie noch gibt, sind wir
     * auf derselben Seite, auch wenn YouTube seine Leiste beim Scrollen ganz entfernt hat.
     */
    fun findFeed(root: AccessibilityNodeInfo, screen: Rect): AccessibilityNodeInfo? {
        for (id in sel.feedIds) {
            for (node in root.findAccessibilityNodeInfosByViewId(sel.fullId(id))) {
                if (isFeed(node, screen)) return node
            }
        }
        var checked = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
            if (checked++ > 400 || depth > 20) return null
            if (isFeed(node, screen)) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1)?.let { return it }
            }
            return null
        }
        return walk(root, 0)
    }

    fun isFeedStillThere(feed: AccessibilityNodeInfo, screen: Rect): Boolean = feed.refresh() && isFeed(feed, screen)

    private fun isFeed(node: AccessibilityNodeInfo, screen: Rect): Boolean {
        if (!node.isScrollable || !node.isVisibleToUser) return false
        val r = node.bounds()
        return r.height() >= screen.height() * 0.4 && r.width() >= screen.width() * 0.9
    }

    /** Der ⋮-Knopf rechts neben der Überschrift "Shorts". */
    private fun findMenuButton(node: AccessibilityNodeInfo, header: Rect, screen: Rect, depth: Int): AccessibilityNodeInfo? {
        val r = node.bounds()
        val tolerance = (40 * density).toInt()
        // Muss in der Zeile der Überschrift beginnen: Das ⋮ der ersten Short-Karte direkt darunter zählt nicht
        // (Regal auf "Abos" hat gar kein eigenes ⋮).
        val nearHeader = r.centerY() in (header.top - tolerance)..(header.bottom + tolerance) && r.top < header.bottom
        // Klein in beide Richtungen und kein "… abspielen" (sonst wurde eine Short-Karte rechts als ⋮ getippt).
        if (node.isClickable && nearHeader && r.centerX() > screen.width() * 0.8 && r.width() <= 72 * density &&
            r.height() <= 72 * density && !opensVideo(node)
        ) {
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
        if (r.top - screen.top < screen.height() * 0.6) return null
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

    /**
     * Liest eine bekannte Leiste neu ein, auch wenn sie gerade weggescrollt ist (für Klicks auf ihre Knöpfe).
     */
    fun rebuildBar(container: AccessibilityNodeInfo): FoundBar? {
        if (!container.refresh()) return null
        val b = container.bounds()
        val tabs = mutableMapOf<TabKind, TabInfo>()
        val nodes = mutableMapOf<TabKind, AccessibilityNodeInfo>()
        collectTabs(container, 0, b, tabs, nodes, lenient = true)
        if (tabs.isEmpty()) return null
        return FoundBar(container, b, tabs, nodes)
    }

    private fun buildBar(container: AccessibilityNodeInfo, screen: Rect): FoundBar? {
        if (!container.isVisibleToUser) return null
        val b = Rect()
        container.getBoundsInScreen(b)
        if (b.width() < screen.width() * 0.8) return null
        if (b.height() > maxBarHeight || b.height() < 24 * density) return null
        if (b.top - screen.top < screen.height() * 0.6) return null

        val tabs = mutableMapOf<TabKind, TabInfo>()
        val nodes = mutableMapOf<TabKind, AccessibilityNodeInfo>()
        collectTabs(container, 0, b, tabs, nodes)
        // Unbekannte Sprache (Wörter passen nicht): Knöpfe nach ihrer Reihenfolge zuordnen. Nur bei YouTubes eigener
        // Leiste (bekannte ID), sonst könnte eine beliebige Knopfreihe unten als Leiste gelten.
        if ((tabs.size < 2 || (TabKind.HOME !in tabs && TabKind.SUBSCRIPTIONS !in tabs)) && isKnownBar(container)) {
            tabs.clear()
            nodes.clear()
            collectByPosition(container, b, tabs, nodes)
        }
        if (tabs.size < 2) return null
        if (TabKind.HOME !in tabs && TabKind.SUBSCRIPTIONS !in tabs) return null
        tabs[TabKind.SHORTS]?.label?.let { learnedShortsWord = it }
        return FoundBar(container, Rect(b), tabs, nodes)
    }

    /** Das Wort für "Shorts" in der Sprache der App (von der Leiste gelernt), für die Überschrift der Regale. */
    var learnedShortsWord: String? = null
        private set

    private fun isKnownBar(container: AccessibilityNodeInfo): Boolean {
        val id = container.viewIdResourceName ?: return false
        return id == learnedBarId || sel.barIds.any { sel.fullId(it) == id }
    }

    /**
     * Ordnet die Knöpfe nach der Lage zu, in jeder Sprache gleich: Startseite, Shorts, Erstellen, Abos, Mein YouTube
     * (ohne Erstellen: 4 Knöpfe). Rechts-nach-links-Sprachen (Arabisch, Hebräisch) zeigen sie gespiegelt: Dann steht
     * "Mein YouTube" (mit Profilbild) links. Erstellen hat als einziger keinen sichtbaren Text.
     */
    private fun collectByPosition(
        container: AccessibilityNodeInfo,
        barBounds: Rect,
        tabs: MutableMap<TabKind, TabInfo>,
        nodes: MutableMap<TabKind, AccessibilityNodeInfo>,
    ) {
        val found = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        collectClickable(container, 0, barBounds, found)
        if (found.size !in 4..5) return
        var ordered = found.sortedBy { it.second.left }
        val withText = ordered.map { deepTextNode(it.first, 0) != null }
        val textless = withText.count { !it }
        if (found.size == 5 && (textless != 1 || withText[2])) return
        if (found.size == 4 && textless != 0) return
        // Profilbild ganz links: gespiegelt.
        if (findThumbnail(ordered.first().first, 0) && !findThumbnail(ordered.last().first, 0)) ordered = ordered.reversed()
        val kinds = if (found.size == 5) {
            listOf(TabKind.HOME, TabKind.SHORTS, TabKind.CREATE, TabKind.SUBSCRIPTIONS, TabKind.YOU)
        } else {
            listOf(TabKind.HOME, TabKind.SHORTS, TabKind.SUBSCRIPTIONS, TabKind.YOU)
        }
        for ((i, kind) in kinds.withIndex()) {
            val (child, r) = ordered[i]
            val textNode = deepTextNode(child, 0)
            tabs[kind] = TabInfo(
                kind = kind,
                bounds = Rect(r),
                label = textNode?.text?.toString()?.trim(),
                labelBounds = textNode?.let { Rect().also(it::getBoundsInScreen) },
                iconBounds = findIcon(child, 0),
                selected = isSelectedDeep(child, 0),
                badge = hasBadge(child),
            )
            nodes[kind] = child
        }
    }

    private fun collectClickable(
        node: AccessibilityNodeInfo,
        depth: Int,
        barBounds: Rect,
        out: MutableList<Pair<AccessibilityNodeInfo, Rect>>,
    ) {
        val r = Rect()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            child.getBoundsInScreen(r)
            if (child.isClickable && r.width() in 1..(barBounds.width() / 2)) {
                out += child to Rect(r)
                continue
            }
            if (depth < 6) collectClickable(child, depth + 1, barBounds, out)
        }
    }

    private fun findThumbnail(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (node.viewIdResourceName?.contains("thumbnail") == true) return true
        if (depth >= 4) return false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findThumbnail(child, depth + 1)) return true
        }
        return false
    }

    private fun collectTabs(
        node: AccessibilityNodeInfo,
        depth: Int,
        barBounds: Rect,
        tabs: MutableMap<TabKind, TabInfo>,
        nodes: MutableMap<TabKind, AccessibilityNodeInfo>,
        lenient: Boolean = false,
    ) {
        val r = Rect()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            child.getBoundsInScreen(r)
            // lenient: Leiste ist weggescrollt, Grössen sind dann 0 – nur nach Beschriftung gehen.
            val tabSized = lenient || r.width() in 1..(barBounds.width() / 2)
            if (tabSized && (child.isClickable || ownLabel(child) != null)) {
                val kind = classify(deepLabel(child, 0))
                if (kind != null && kind !in tabs) {
                    val textNode = deepTextNode(child, 0)
                    tabs[kind] = TabInfo(
                        kind = kind,
                        bounds = Rect(r),
                        label = textNode?.text?.toString()?.trim(),
                        labelBounds = textNode?.let { Rect().also(it::getBoundsInScreen) },
                        iconBounds = findIcon(child, 0),
                        selected = isSelectedDeep(child, 0),
                        badge = hasBadge(child),
                    )
                    nodes[kind] = child
                }
                if (child.isClickable) continue
            }
            if (depth < 6) collectTabs(child, depth + 1, barBounds, tabs, nodes, lenient)
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
    private fun deepTextNode(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (!node.text?.toString()?.trim().isNullOrEmpty()) return node
        if (depth >= 4) return null
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> deepTextNode(child, depth + 1)?.let { return it } }
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

    /** Das Bild im Tab (Haus-Icon, Profilbild ...). */
    private fun findIcon(node: AccessibilityNodeInfo, depth: Int): Rect? {
        val r = Rect()
        node.getBoundsInScreen(r)
        val isImage = node.className?.contains("Image") == true
        val roughlySquare = r.width() > 0 && kotlin.math.abs(r.width() - r.height()) <= r.width() / 4
        if (isImage && roughlySquare && r.width() <= 48 * density && node.isVisibleToUser) return r
        if (depth >= 4) return null
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> findIcon(child, depth + 1)?.let { return it } }
        }
        return null
    }
}
