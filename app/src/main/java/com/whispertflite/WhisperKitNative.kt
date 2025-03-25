package com.whispertflite

import android.util.Log

/**
 * WhisperKitNative类负责通过JNI调用与WhisperKit C++库交互
 * 提供语音识别功能
 */
class WhisperKitNative {
    companion object {
        init {
            Log.d("WhisperKit", "开始加载所有必需库...")
                    
            // 1. 先加载基础系统库
            Log.d("WhisperKit", "开始加载基础系统库...")
            try {
                System.loadLibrary("avutil")
                Log.d("WhisperKit", "✓ avutil 加载成功")
                System.loadLibrary("swresample")
                Log.d("WhisperKit", "✓ swresample 加载成功")
                System.loadLibrary("avcodec")
                Log.d("WhisperKit", "✓ avcodec 加载成功")
                System.loadLibrary("avformat")
                Log.d("WhisperKit", "✓ avformat 加载成功")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("WhisperKit", "❌ 基础系统库加载失败: ${e.message}")
                throw e
            }
            
            // 2. 加载SDL库
            Log.d("WhisperKit", "开始加载SDL库...")
            try {
                System.loadLibrary("SDL3")
                Log.d("WhisperKit", "✓ SDL3 加载成功")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("WhisperKit", "❌ SDL库加载失败: ${e.message}")
                throw e
            }
            
            // 3. 加载TensorFlow和加速库
            Log.d("WhisperKit", "开始加载TensorFlow和加速库...")
            try {
                System.loadLibrary("tensorflowlite")
                Log.d("WhisperKit", "✓ tensorflowlite 加载成功")
                
                // 4. 加载硬件加速库（无需条件判断，直接加载）
                System.loadLibrary("tensorflowlite_gpu_delegate")
                Log.d("WhisperKit", "✓ tensorflowlite_gpu_delegate 加载成功")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("WhisperKit", "❌ TensorFlow或加速库加载失败: ${e.message}")
                throw e
            }
            
            // 5. 加载高通设备QNN相关库（无需条件判断，直接加载）
            Log.d("WhisperKit", "开始加载QNN相关库...")
            try {
                // 先加载基础QNN库
                System.loadLibrary("QnnSystem")
                Log.d("WhisperKit", "✓ QnnSystem 加载成功")
                System.loadLibrary("QnnHtp")
                Log.d("WhisperKit", "✓ QnnHtp 加载成功")
                System.loadLibrary("QnnDsp")
                Log.d("WhisperKit", "✓ QnnDsp 加载成功")
                System.loadLibrary("QnnTFLiteDelegate")
                Log.d("WhisperKit", "✓ QnnTFLiteDelegate 加载成功")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("WhisperKit", "❌ QNN相关库加载失败: ${e.message}")
                // 这里不抛出异常，因为QNN库可能是可选的
                Log.w("WhisperKit", "继续加载其他库...")
            }
            
            // 6. 最后加载WhisperKit相关库
            Log.d("WhisperKit", "开始加载WhisperKit相关库...")
            try {
                System.loadLibrary("whisperkit")
                Log.d("WhisperKit", "✓ whisperkit 加载成功")
                System.loadLibrary("native-whisper")
                Log.d("WhisperKit", "✓ native-whisper 加载成功")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("WhisperKit", "❌ WhisperKit相关库加载失败: ${e.message}")
                throw e
            }
        }
    }

    /**
     * 初始化WhisperKit
     * 
     * @param modelPath 模型文件路径
     * @param audioPath 音频文件路径，可以为空字符串
     * @param reportPath 报告文件路径
     * @param enableReport 是否启用报告
     * @param concurrentWorkers 并发工作线程数量
     * @return 返回一个指向原生实例的指针
     */
    external fun init(
        modelPath: String,
        audioPath: String,
        reportPath: String,
        enableReport: Boolean,
        concurrentWorkers: Int
    ): Long

    /**
     * 转录音频文件
     *
     * @param nativePtr 由init方法返回的指针
     * @param audioPath 要转录的音频文件路径
     * @return 返回转录结果文本
     */
    external fun transcribe(nativePtr: Long, audioPath: String): String

    /**
     * 释放资源
     *
     * @param nativePtr 由init方法返回的指针
     */
    external fun release(nativePtr: Long)
}
