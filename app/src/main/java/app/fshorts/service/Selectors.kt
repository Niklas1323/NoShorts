package app.fshorts.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class TabKind { HOME, SHORTS, SUBSCRIPTIONS, YOU, CREATE }

/**
 * Regeln, mit denen wir die Teile der YouTube-App finden.
 * Liegen in assets/selectors.json, damit wir sie ohne Code-Änderung anpassen können
 * (später per Remote-Config).
 */
data class Selectors(
    val youtubePackage: String,
    val barIds: List<String>,
    val maxBarHeightDp: Int,
    val labels: Map<TabKind, List<String>>,
    val searchLabels: List<String>,
    val shortsPlayerIds: List<String>,
    val coverIds: List<String>,
    val watchIds: List<String>,
    val badgeIdHints: List<String>,
    val badgeTextHints: List<String>,
    val shelfHeaderLabels: List<String>,
    val shelfHideLabels: List<String>,
    /** Menüpunkt auf einer einzelnen Short-Karte, der sie aus dem Feed nimmt ("Ausblenden"). */
    val cardHideLabels: List<String>,
    /** Teil der Beschreibung einer Karte, die beim Tippen ein Video/Short öffnet ("Short abspielen"). */
    val playHints: List<String>,
    val miniPlayerLabels: List<String>,
    val miniPlayerIds: List<String>,
    val feedIds: List<String>,
) {
    fun fullId(id: String) = if (id.contains(":id/")) id else "$youtubePackage:id/$id"

    companion object {
        fun load(context: Context): Selectors {
            val json = context.assets.open("selectors.json").bufferedReader().use { it.readText() }
            return parse(JSONObject(json))
        }

        private fun parse(o: JSONObject): Selectors {
            val labels = o.getJSONObject("labels")
            return Selectors(
                youtubePackage = o.getString("youtubePackage"),
                barIds = o.getJSONArray("barIds").toList(),
                maxBarHeightDp = o.optInt("maxBarHeightDp", 100),
                labels = mapOf(
                    TabKind.HOME to labels.getJSONArray("home").toList(),
                    TabKind.SHORTS to labels.getJSONArray("shorts").toList(),
                    TabKind.SUBSCRIPTIONS to labels.getJSONArray("subscriptions").toList(),
                    TabKind.YOU to labels.getJSONArray("you").toList(),
                    TabKind.CREATE to labels.getJSONArray("create").toList(),
                ),
                searchLabels = o.getJSONArray("searchLabels").toList(),
                shortsPlayerIds = o.getJSONArray("shortsPlayerIds").toList(),
                coverIds = o.optJSONArray("coverIds")?.toList().orEmpty(),
                watchIds = o.optJSONArray("watchIds")?.toList() ?: listOf("watch_player"),
                badgeIdHints = o.getJSONArray("badgeIdHints").toList(),
                badgeTextHints = o.getJSONArray("badgeTextHints").toList(),
                shelfHeaderLabels = o.optJSONArray("shelfHeaderLabels")?.toList() ?: listOf("Shorts"),
                shelfHideLabels = o.optJSONArray("shelfHideLabels")?.toList().orEmpty(),
                cardHideLabels = o.optJSONArray("cardHideLabels")?.toList().orEmpty(),
                playHints = o.optJSONArray("playHints")?.toList().orEmpty().map { it.lowercase() },
                miniPlayerLabels = o.optJSONArray("miniPlayerLabels")?.toList().orEmpty(),
                miniPlayerIds = o.optJSONArray("miniPlayerIds")?.toList().orEmpty(),
                feedIds = o.optJSONArray("feedIds")?.toList().orEmpty(),
            )
        }

        private fun JSONArray.toList(): List<String> = List(length()) { getString(it) }
    }
}
