package com.filmstyle.app

import android.graphics.Bitmap
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 风格提取器 —— 从参考图提取「色彩 + 氛围」完整风格。
 *
 * 与 Python 参考实现 style_preset.py 的 extract_preset 完全一致。
 */
object StyleExtractor {

    /**
     * 从参考图提取风格预设。
     *
     * @param bitmap 参考图（用户从相册选）
     * @param name 预设名称（用户自定义）
     * @param withAtmosphere 是否同时提取氛围特征（颗粒/暗角/对比度）
     */
    fun extract(bitmap: Bitmap, name: String, withAtmosphere: Boolean = true): StylePreset {
        // 1. 下采样到 128px 宽（统计量不受影响，大幅提速）
        val w = 128
        val h = (bitmap.height.toFloat() / bitmap.width * w).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)

        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)

        // 2. 转 Lab 并累加均值
        val labs = ArrayList<FloatArray>(w * h)
        var sumL = 0.0
        var sumA = 0.0
        var sumB = 0.0
        for (p in pixels) {
            val r = (p shr 16 and 0xFF) / 255.0
            val g = (p shr 8 and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            val lab = rgbToLab(r, g, b)
            labs.add(lab)
            sumL += lab[0]
            sumA += lab[1]
            sumB += lab[2]
        }
        val n = labs.size.toDouble()
        val meanL = sumL / n
        val meanA = sumA / n
        val meanB = sumB / n

        // 3. 算标准差
        var varL = 0.0
        var varA = 0.0
        var varB = 0.0
        for (lab in labs) {
            varL += (lab[0] - meanL) * (lab[0] - meanL)
            varA += (lab[1] - meanA) * (lab[1] - meanA)
            varB += (lab[2] - meanB) * (lab[2] - meanB)
        }
        val stdL = sqrt(varL / n)
        val stdA = sqrt(varA / n)
        val stdB = sqrt(varB / n)

        // 4. 提取氛围特征
        var grain = 0.0f
        var vignette = 0.0f
        var contrast = 0.5f
        if (withAtmosphere) {
            grain = extractGrain(small)
            vignette = extractVignette(small)
            contrast = extractContrast(small, stdL)
        }

        return StylePreset(
            name = name,
            labMean = floatArrayOf(meanL.toFloat(), meanA.toFloat(), meanB.toFloat()),
            labStd = floatArrayOf(stdL.toFloat(), stdA.toFloat(), stdB.toFloat()),
            grain = grain,
            vignette = vignette,
            contrast = contrast
        )
    }

    /**
     * 提取颗粒强度：高频噪声的 RMS（拉普拉斯响应）。
     */
    private fun extractGrain(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 亮度
        val lum = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255.0
            val g = (p shr 8 and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            lum[i] = (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
        }

        // 拉普拉斯（4邻域）
        var sum = 0.0
        var count = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val lap = -4.0 * lum[idx] +
                    lum[idx - w] + lum[idx + w] + lum[idx - 1] + lum[idx + 1]
                sum += lap * lap
                count++
            }
        }
        val rms = sqrt(sum / count)
        return (rms / 0.25).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 提取暗角：四角亮度相对中心亮度的衰减。
     */
    private fun extractVignette(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val lum = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255.0
            val g = (p shr 8 and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            lum[i] = (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
        }

        val cy = h / 2
        val cx = w / 2
        val rh = (h / 10).coerceAtLeast(1)
        val rw = (w / 10).coerceAtLeast(1)

        // 中心平均
        var centerSum = 0.0
        var centerCount = 0
        for (y in cy - rh until cy + rh) {
            for (x in cx - rw until cx + rw) {
                centerSum += lum[y * w + x]
                centerCount++
            }
        }
        val center = centerSum / centerCount
        if (center < 1e-6) return 0f

        // 四角平均
        var cornerSum = 0.0
        var cornerCount = 0
        for (y in 0 until rh) {
            for (x in 0 until rw) cornerSum += lum[y * w + x].let { cornerCount++; it }
            for (x in w - rw until w) cornerSum += lum[y * w + x].let { cornerCount++; it }
        }
        for (y in h - rh until h) {
            for (x in 0 until rw) cornerSum += lum[y * w + x].let { cornerCount++; it }
            for (x in w - rw until w) cornerSum += lum[y * w + x].let { cornerCount++; it }
        }
        val corners = cornerSum / cornerCount
        val ratio = corners / center
        return ((1.0 - ratio) * 3.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 提取对比度：亮度 L 标准差归一化。
     */
    private fun extractContrast(bitmap: Bitmap, stdL: Double): Float {
        // stdL 已经算好传入（Lab 亮度标准差），归一化到 0~1
        return (stdL / 40.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * RGB(0-1) -> Lab（D65 sRGB 标准转换，与 shader/python 一致）。
     * public 以便 MainActivity 计算目标图统计量。
     */
    fun rgbToLab(r0: Double, g0: Double, b0: Double): FloatArray {
        fun toLin(c: Double): Double =
            if (c > 0.04045) ((c + 0.055) / 1.055).pow(2.4) else c / 12.92

        val r = toLin(r0)
        val g = toLin(g0)
        val b = toLin(b0)

        val x = 0.4124 * r + 0.3576 * g + 0.1805 * b
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = 0.0193 * r + 0.1192 * g + 0.9505 * b

        val xn = x / 0.95047
        val yn = y / 1.0
        val zn = z / 1.08883

        fun f(t: Double): Double =
            if (t > 0.008856) t.pow(1.0 / 3.0) else 7.787 * t + 16.0 / 116.0

        val fx = f(xn)
        val fy = f(yn)
        val fz = f(zn)

        val L = 116.0 * fy - 16.0
        val a = 500.0 * (fx - fy)
        val bb = 200.0 * (fy - fz)

        return floatArrayOf(L.toFloat(), a.toFloat(), bb.toFloat())
    }
}
