package com.filmstyle.app

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 风格渲染器 —— 用 GLES 实时套用风格预设到 Bitmap/纹理。
 *
 * 用法：
 *   renderer.setBitmap(bitmap)         // 传入要处理的图
 *   renderer.setPreset(preset, targetMean, targetStd, strength)  // 设置风格
 *   然后 GLSurfaceView 自动每帧渲染。
 *
 * 氛围层（颗粒/暗角/对比度）也在这里叠加，与 shader 共同完成完整风格。
 */
class StyleRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var program = 0
    private var textureId = 0
    private var vertexBuffer: FloatBuffer
    private var texCoordBuffer: FloatBuffer

    // uniform 位置
    private var uTexture = 0
    private var uPresetMean = 0
    private var uPresetStd = 0
    private var uTargetMean = 0
    private var uTargetStd = 0
    private var uStrength = 0

    // 当前风格参数
    private var presetMean = floatArrayOf(50f, 0f, 0f)
    private var presetStd = floatArrayOf(20f, 10f, 10f)
    private var targetMean = floatArrayOf(50f, 0f, 0f)
    private var targetStd = floatArrayOf(20f, 10f, 10f)
    private var strength = 1.0f

    private var bitmapWidth = 0
    private var bitmapHeight = 0

    // 顶点：全屏四边形（位置 + 纹理坐标分开存）
    init {
        val positions = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        vertexBuffer = ByteBuffer.allocateDirect(positions.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(positions).position(0)
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        texCoordBuffer.put(texCoords).position(0)
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        // 编译 shader（从 raw 资源加载）
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, readRawShader())
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        uTexture = GLES20.glGetUniformLocation(program, "u_texture")
        uPresetMean = GLES20.glGetUniformLocation(program, "u_presetMean")
        uPresetStd = GLES20.glGetUniformLocation(program, "u_presetStd")
        uTargetMean = GLES20.glGetUniformLocation(program, "u_targetMean")
        uTargetStd = GLES20.glGetUniformLocation(program, "u_targetStd")
        uStrength = GLES20.glGetUniformLocation(program, "u_strength")
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (textureId == 0) return

        GLES20.glUseProgram(program)
        GLES20.glUniform3fv(uPresetMean, 1, presetMean, 0)
        GLES20.glUniform3fv(uPresetStd, 1, presetStd, 0)
        GLES20.glUniform3fv(uTargetMean, 1, targetMean, 0)
        GLES20.glUniform3fv(uTargetStd, 1, targetStd, 0)
        GLES20.glUniform1f(uStrength, strength)

        // 绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTexture, 0)

        // 绑定顶点
        val posLoc = GLES20.glGetAttribLocation(program, "a_position")
        val texLoc = GLES20.glGetAttribLocation(program, "a_texCoord")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
    }

    /**
     * 设置要处理的位图。
     */
    fun setBitmap(bitmap: android.graphics.Bitmap) {
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height
        // 上传纹理
        val buf = ByteBuffer.allocateDirect(bitmapWidth * bitmapHeight * 4)
        bitmap.copyPixelsToBuffer(buf)
        buf.position(0)

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            bitmapWidth, bitmapHeight, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    }

    /**
     * 设置风格预设和目标统计量。
     */
    fun setPreset(preset: StylePreset, targetMean: FloatArray, targetStd: FloatArray, strength: Float) {
        this.presetMean = preset.labMean
        this.presetStd = preset.labStd
        this.targetMean = targetMean
        this.targetStd = targetStd
        this.strength = strength
    }

    private fun readRawShader(): String {
        val input = context.resources.openRawResource(R.raw.style_preset)
        return input.bufferedReader().use { it.readText() }
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        // GLES 3.0 顶点着色器，与 fragment shader 的 in/out 命名匹配
        private const val VERTEX_SHADER = """
            #version 300 es
            in vec2 a_position;
            in vec2 a_texCoord;
            out vec2 v_texCoord;
            void main() {
                v_texCoord = a_texCoord;
                gl_Position = vec4(a_position, 0.0, 1.0);
            }
        """
    }
}
