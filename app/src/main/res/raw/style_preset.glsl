// ============================================================
// 风格预设套用 —— GLSL Fragment Shader（安卓 GLES 3.0 / Vulkan）
// 与 style_preset.py 的 Reinhard 迁移算法完全一致。
//
// 用法：
//   1. 在 App 里用 CPU 提取参考图的 Lab 均值/标准差（一次性）
//   2. 把 6 个数字作为 uniform 传入本 shader
//   3. 每帧对照片纹理采样后调用 applyPreset()
//
// uniform 说明：
//   u_presetMean   : vec3 = 参考图的 Lab 均值 [L, a, b]   (L 0~100, a/b -128~127)
//   u_presetStd    : vec3 = 参考图的 Lab 标准差
//   u_targetMean   : vec3 = 目标照片的 Lab 均值（套用时现算，或 CPU 预传）
//   u_targetStd    : vec3 = 目标照片的 Lab 标准差
//   u_strength     : float = 迁移强度 0~1
//
// 注意：target 的均值/标准差需对「当前拍摄画面」实时统计。
// 简单做法是 CPU 端对下采样图算一次；若需精确，可用 mipmap 近似。
// ============================================================
#version 300 es
precision highp float;

in vec2 v_texCoord;
out vec4 fragColor;
uniform sampler2D u_texture;

uniform vec3 u_presetMean;   // 参考图 Lab 均值
uniform vec3 u_presetStd;    // 参考图 Lab 标准差
uniform vec3 u_targetMean;   // 目标图 Lab 均值
uniform vec3 u_targetStd;    // 目标图 Lab 标准差
uniform float u_strength;    // 0~1

// ---- sRGB -> 线性 ----
vec3 srgbToLinear(vec3 c) {
    return mix(
        c / 12.92,
        pow((c + 0.055) / 1.055, vec3(2.4)),
        step(vec3(0.04045), c)
    );
}

// ---- 线性 -> sRGB ----
vec3 linearToSrgb(vec3 c) {
    return mix(
        c * 12.92,
        1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055,
        step(vec3(0.0031308), c)
    );
}

// ---- RGB(线性) -> XYZ(D65) ----
vec3 rgbToXyz(vec3 c) {
    mat3 m = mat3(
        0.4124, 0.3576, 0.1805,
        0.2126, 0.7152, 0.0722,
        0.0193, 0.1192, 0.9505
    );
    return m * c;
}

// ---- XYZ(D65) -> RGB(线性) ----
vec3 xyzToRgb(vec3 c) {
    mat3 m = mat3(
         3.2406, -1.5372, -0.4986,
        -0.9689,  1.8758,  0.0415,
         0.0557, -0.2040,  1.0570
    );
    return m * c;
}

// ---- XYZ -> Lab ----
vec3 xyzToLab(vec3 c) {
    c.x /= 0.95047;
    c.y /= 1.0;
    c.z /= 1.08883;
    vec3 t = c;
    t.x = (t.x > 0.008856) ? pow(t.x, 1.0 / 3.0) : (7.787 * t.x + 16.0 / 116.0);
    t.y = (t.y > 0.008856) ? pow(t.y, 1.0 / 3.0) : (7.787 * t.y + 16.0 / 116.0);
    t.z = (t.z > 0.008856) ? pow(t.z, 1.0 / 3.0) : (7.787 * t.z + 16.0 / 116.0);
    float L = 116.0 * t.y - 16.0;
    float a = 500.0 * (t.x - t.y);
    float b = 200.0 * (t.y - t.z);
    return vec3(L, a, b);
}

// ---- Lab -> XYZ ----
vec3 labToXyz(vec3 c) {
    float fy = (c.x + 16.0) / 116.0;
    float fx = fy + c.y / 500.0;
    float fz = fy - c.z / 200.0;
    vec3 t;
    t.x = (fx > 0.206893) ? pow(fx, 3.0) : (fx - 16.0 / 116.0) / 7.787;
    t.y = (fy > 0.206893) ? pow(fy, 3.0) : (fy - 16.0 / 116.0) / 7.787;
    t.z = (fz > 0.206893) ? pow(fz, 3.0) : (fz - 16.0 / 116.0) / 7.787;
    return vec3(t.x * 0.95047, t.y * 1.0, t.z * 1.08883);
}

// ---- Lab -> RGB(sRGB编码) ----
vec3 labToSrgb(vec3 lab) {
    vec3 xyz = labToXyz(lab);
    vec3 lin = xyzToRgb(xyz);
    return linearToSrgb(clamp(lin, 0.0, 1.0));
}

// ---- 核心：套用风格预设（Reinhard 迁移，柔和版） ----
// 与 style_preset.py 的 apply_preset 完全一致：
//   - 亮度轴(L)：只迁 40%，均值限幅 ±10，标准差比例限幅 [0.7, 1.4]
//   - 色彩轴(a/b)：迁移核心，均值偏移软限幅 ±15，标准差比例限幅 [0.4, 2.5]
vec3 applyPreset(vec3 srgbColor) {
    // 1. sRGB -> Lab
    vec3 lin = srgbToLinear(srgbColor);
    vec3 lab = xyzToLab(rgbToXyz(lin));

    // 2. 逐轴计算迁移后的统计量
    vec3 rMean = u_targetMean;
    vec3 rStd  = u_targetStd;

    // L 轴（亮度）：保守迁移
    float mL = (u_presetMean.x - u_targetMean.x) * u_strength;
    mL = clamp(mL, -10.0, 10.0);
    rMean.x = u_targetMean.x + mL * 0.4;
    float ratioL = u_presetStd.x / (u_targetStd.x + 1e-6);
    ratioL = 1.0 + (ratioL - 1.0) * u_strength;
    ratioL = clamp(ratioL, 0.7, 1.4);
    rStd.x = u_targetStd.x * ratioL;

    // a 轴（红绿）：软限幅均值偏移
    float mA = (u_presetMean.y - u_targetMean.y) * u_strength;
    mA = tanh(mA / 15.0) * 15.0;
    rMean.y = u_targetMean.y + mA;
    float ratioA = u_presetStd.y / (u_targetStd.y + 1e-6);
    ratioA = 1.0 + (ratioA - 1.0) * u_strength;
    ratioA = clamp(ratioA, 0.4, 2.5);
    rStd.y = u_targetStd.y * ratioA;

    // b 轴（黄蓝）：软限幅均值偏移
    float mB = (u_presetMean.z - u_targetMean.z) * u_strength;
    mB = tanh(mB / 15.0) * 15.0;
    rMean.z = u_targetMean.z + mB;
    float ratioB = u_presetStd.z / (u_targetStd.z + 1e-6);
    ratioB = 1.0 + (ratioB - 1.0) * u_strength;
    ratioB = clamp(ratioB, 0.4, 2.5);
    rStd.z = u_targetStd.z * ratioB;

    // 3. 标准化 + 对齐
    vec3 outLab = (lab - u_targetMean) * (rStd / (u_targetStd + 1e-6)) + rMean;

    // 4. Lab -> sRGB
    return labToSrgb(outLab);
}

void main() {
    vec3 color = texture(u_texture, v_texCoord).rgb;
    vec3 result = applyPreset(color);
    fragColor = vec4(result, 1.0);
}
