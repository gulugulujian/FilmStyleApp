package com.filmstyle.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 预设库存储 —— 用 SharedPreferences 存 JSON。
 * 支持命名、增删改查。
 */
object PresetStore {

    private const val PREFS_NAME = "film_styles"
    private const val KEY = "presets"

    fun save(context: Context, presets: List<StylePreset>) {
        val arr = JSONArray()
        for (p in presets) {
            val o = JSONObject()
            o.put("name", p.name)
            o.put("lab_mean", JSONArray(p.labMean.toList()))
            o.put("lab_std", JSONArray(p.labStd.toList()))
            o.put("strength", p.strength)
            o.put("grain", p.grain)
            o.put("vignette", p.vignette)
            o.put("contrast", p.contrast)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun load(context: Context): List<StylePreset> {
        val json = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val result = ArrayList<StylePreset>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(StylePreset(
                    name = o.getString("name"),
                    labMean = floatArrayOf(
                        o.getJSONArray("lab_mean").getDouble(0).toFloat(),
                        o.getJSONArray("lab_mean").getDouble(1).toFloat(),
                        o.getJSONArray("lab_mean").getDouble(2).toFloat()
                    ),
                    labStd = floatArrayOf(
                        o.getJSONArray("lab_std").getDouble(0).toFloat(),
                        o.getJSONArray("lab_std").getDouble(1).toFloat(),
                        o.getJSONArray("lab_std").getDouble(2).toFloat()
                    ),
                    strength = o.optDouble("strength", 1.0).toFloat(),
                    grain = o.optDouble("grain", 0.0).toFloat(),
                    vignette = o.optDouble("vignette", 0.0).toFloat(),
                    contrast = o.optDouble("contrast", 0.5).toFloat()
                ))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
