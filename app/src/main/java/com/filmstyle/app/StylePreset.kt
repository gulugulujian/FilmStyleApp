package com.filmstyle.app

/**
 * 风格预设 —— 从参考图提取的可复用风格。
 *
 * 风格 = 色彩(Lab均值+标准差) + 氛围(颗粒/暗角/对比度)
 * 与 Python 参考实现 style_preset.py 完全一致。
 */
data class StylePreset(
    val name: String,
    val labMean: FloatArray,   // [L, a, b]
    val labStd: FloatArray,    // [L, a, b]
    val strength: Float = 1.0f,
    val grain: Float = 0.0f,       // 颗粒强度 0~1
    val vignette: Float = 0.0f,    // 暗角强度 0~1
    val contrast: Float = 0.5f     // 对比度 0.5=原样
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StylePreset) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String =
        "StylePreset(name=$name, grain=$grain, vignette=$vignette, contrast=$contrast)"
}
