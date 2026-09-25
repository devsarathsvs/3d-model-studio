package com.iftl.threedee.viewer.data

import org.json.JSONException
import org.json.JSONObject

/**
 * gltfio (FilamentAsset.getExtras) hands back each glTF node's raw `extras` JSON as a string. This
 * pulls out the "prop" field the task's models use to carry part label text.
 */
object ExtrasLabelParser {
    fun labelText(extrasJson: String?): String? {
        if (extrasJson.isNullOrBlank()) return null
        return try {
            val prop = JSONObject(extrasJson).optString("prop", "")
            prop.ifBlank { null }
        } catch (e: JSONException) {
            null
        }
    }
}
