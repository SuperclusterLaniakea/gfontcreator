package com.gfontcreator.app.recognition

import com.gfontcreator.app.handwriting.StrokeProject

/**
 * NCNN 识别器的 JNI 桩（ref.md 4.2 / 依赖清单）。
 *
 * 接入步骤（生产化时执行）：
 * 1. app/build.gradle.kts 增加 NCNN AAR 依赖或 CMake 静态库；
 * 2. 将 Handwritten 项目训练好的 MobileNetV2 param/bin（INT8 量化）
 *    放入 app/src/main/assets/ncnn/（model.param + model.bin + labels.txt）；
 * 3. 实现 native 层：载入模型 → 64x64 灰度预处理 → 前向 → softmax 取 top-5；
 * 4. 在 RecognizerFactory 中切换为 NcnnRecognizer 实例。
 *
 * 模型未就绪时 isAvailable()==false，工厂自动回退 ProxyRecognizer。
 */
class NcnnRecognizer : HanziRecognizer {

    external fun initModel(modelPath: String, paramPath: String, labelPath: String): Boolean
    external fun recognizeBitmap(pixels: IntArray, width: Int, height: Int): FloatArray

    private var ready = false
    private var labels: List<Int> = emptyList()

    fun isAvailable(): Boolean = ready

    override fun recognize(project: StrokeProject, expectCodepoint: Int?): List<Candidate> {
        if (!ready) return emptyList()
        // native 前向由 JNI 实现；此处仅定义调用契约
        TODO("NCNN native 推理待接入：见类注释的接入步骤")
    }

    companion object {
        init {
            // System.loadLibrary("ncnn_recognizer")  // 接入 native 库后启用
        }
    }
}

/** 识别器工厂：NCNN 模型就绪则用真模型，否则回退代理校验 */
object RecognizerFactory {
    @Volatile private var ncnn: NcnnRecognizer? = null

    fun get(): HanziRecognizer {
        val n = ncnn
        if (n != null && n.isAvailable()) return n
        return ProxyRecognizer()
    }
}
