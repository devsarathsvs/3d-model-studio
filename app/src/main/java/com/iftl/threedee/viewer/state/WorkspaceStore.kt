package com.iftl.threedee.viewer.state

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Only user state is persisted: never native handles, bitmap data or GLB bytes. */
data class CardState(
    val assetPath: String,
    val centreX: Float,
    val centreY: Float,
    val widthDp: Float,
    val heightDp: Float,
    val maximized: Boolean,
    val interaction: Boolean,
    val labels: Boolean,
    val eye: List<Float>,
)

class WorkspaceStore(context: Context) {
    private val preferences = context.getSharedPreferences("workspace", Context.MODE_PRIVATE)

    fun load(): List<CardState> = decode(preferences.getString("cards", null))

    fun save(cards: List<CardState>) {
        preferences.edit { putString("cards", encode(cards)) }
    }

    companion object {
        fun encode(cards: List<CardState>): String =
            JSONArray()
                .apply {
                    cards.forEach { c ->
                        put(
                            JSONObject().apply {
                                put("asset", c.assetPath)
                                put("x", c.centreX)
                                put("y", c.centreY)
                                put("w", c.widthDp)
                                put("h", c.heightDp)
                                put("max", c.maximized)
                                put("interact", c.interaction)
                                put("labels", c.labels)
                                put("eye", JSONArray(c.eye))
                            }
                        )
                    }
                }
                .toString()

        fun decode(json: String?): List<CardState> =
            try {
                val array = JSONArray(json ?: "[]")
                (0 until array.length().coerceAtMost(20)).mapNotNull { i ->
                    runCatching {
                            val c = array.getJSONObject(i)
                            val eye = c.getJSONArray("eye")
                            require(eye.length() == 3)
                            CardState(
                                c.getString("asset"),
                                c.getDouble("x").toFloat().coerceIn(0f, 1f),
                                c.getDouble("y").toFloat().coerceIn(0f, 1f),
                                c.getDouble("w").toFloat().coerceIn(160f, 1400f),
                                c.getDouble("h").toFloat().coerceIn(160f, 1400f),
                                c.optBoolean("max"),
                                c.optBoolean("interact"),
                                c.optBoolean("labels"),
                                (0..2).map { eye.getDouble(it).toFloat() },
                            )
                        }
                        .getOrNull()
                }
            } catch (_: Exception) {
                emptyList()
            }
    }
}
