package com.filmstyle.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: GLSurfaceView
    private lateinit var renderer: StyleRenderer
    private lateinit var nameInput: EditText
    private lateinit var styleList: RecyclerView

    private var currentBitmap: Bitmap? = null
    private var currentPreset: StylePreset? = null
    private var presets: MutableList<StylePreset> = mutableListOf()

    companion object {
        private const val PICK_IMAGE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.preview_view)
        nameInput = findViewById(R.id.name_input)
        styleList = findViewById(R.id.style_list)

        // 初始化 GLES 3.0 渲染器
        previewView.setEGLContextClientVersion(3)
        renderer = StyleRenderer(this)
        previewView.setRenderer(renderer)
        previewView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        // 加载预设库
        presets = PresetStore.load(this).toMutableList()
        setupStyleList()

        // 按钮事件
        findViewById<Button>(R.id.btn_add_style).setOnClickListener {
            openGallery()
        }
        findViewById<Button>(R.id.btn_load_style).setOnClickListener {
            refreshPresetList()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                processReferenceImage(uri)
            }
        }
    }

    /**
     * 处理参考图：提取风格 -> 命名 -> 保存 -> 套用预览。
     */
    private fun processReferenceImage(uri: Uri) {
        try {
            val bitmap = loadBitmap(uri)
            val name = nameInput.text.toString().trim().ifEmpty { "我的风格 ${presets.size + 1}" }

            // 提取风格
            val preset = StyleExtractor.extract(bitmap, name)

            // 保存
            presets.add(preset)
            PresetStore.save(this, presets)
            refreshPresetList()

            // 套用预览（先用参考图本身作为预览目标，展示提取结果）
            currentBitmap = bitmap
            currentPreset = preset
            applyToPreview(bitmap, preset)

            Toast.makeText(this, "已提取风格「$name」：颗粒=${"%.2f".format(preset.grain)} 对比=${"%.2f".format(preset.contrast)}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "提取失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyToPreview(bitmap: Bitmap, preset: StylePreset) {
        // 计算目标图（当前预览图）的统计量
        val (mean, std) = computeStats(bitmap)
        renderer.setBitmap(bitmap)
        renderer.setPreset(preset, mean, std, strength = 1.0f)
        previewView.requestRender()
    }

    /**
     * 计算目标图的 Lab 统计量（下采样）。
     */
    private fun computeStats(bitmap: Bitmap): Pair<FloatArray, FloatArray> {
        val w = 64
        val h = (bitmap.height.toFloat() / bitmap.width * w).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)

        var sumL = 0.0; var sumA = 0.0; var sumB = 0.0
        val labs = ArrayList<FloatArray>(w * h)
        for (p in pixels) {
            val r = (p shr 16 and 0xFF) / 255.0
            val g = (p shr 8 and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            val lab = StyleExtractor.rgbToLab(r, g, b)
            labs.add(lab)
            sumL += lab[0]; sumA += lab[1]; sumB += lab[2]
        }
        val n = labs.size.toDouble()
        val meanL = sumL / n; val meanA = sumA / n; val meanB = sumB / n
        var varL = 0.0; var varA = 0.0; var varB = 0.0
        for (lab in labs) {
            varL += (lab[0] - meanL) * (lab[0] - meanL)
            varA += (lab[1] - meanA) * (lab[1] - meanA)
            varB += (lab[2] - meanB) * (lab[2] - meanB)
        }
        val mean = floatArrayOf(meanL.toFloat(), meanA.toFloat(), meanB.toFloat())
        val std = floatArrayOf(
            kotlin.math.sqrt(varL / n).toFloat(),
            kotlin.math.sqrt(varA / n).toFloat(),
            kotlin.math.sqrt(varB / n).toFloat()
        )
        return Pair(mean, std)
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val stream = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(stream)
    }

    private fun setupStyleList() {
        styleList.layoutManager = LinearLayoutManager(this)
        styleList.adapter = StyleAdapter(presets) { preset ->
            // 点击预设：套用到当前预览图
            currentBitmap?.let { bmp ->
                currentPreset = preset
                applyToPreview(bmp, preset)
            }
        }
    }

    private fun refreshPresetList() {
        presets = PresetStore.load(this).toMutableList()
        styleList.adapter = StyleAdapter(presets) { preset ->
            currentBitmap?.let { bmp ->
                currentPreset = preset
                applyToPreview(bmp, preset)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        previewView.onResume()
    }

    override fun onPause() {
        super.onPause()
        previewView.onPause()
    }
}

/**
 * 风格列表适配器。
 */
class StyleAdapter(
    private val presets: List<StylePreset>,
    private val onClick: (StylePreset) -> Unit
) : RecyclerView.Adapter<StyleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.style_name)
        val detailText: TextView = view.findViewById(R.id.style_detail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_style, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val preset = presets[position]
        holder.nameText.text = preset.name
        holder.detailText.text = "颗粒 ${"%.2f".format(preset.grain)} · 暗角 ${"%.2f".format(preset.vignette)} · 对比 ${"%.2f".format(preset.contrast)}"
        holder.itemView.setOnClickListener { onClick(preset) }
    }

    override fun getItemCount(): Int = presets.size
}
