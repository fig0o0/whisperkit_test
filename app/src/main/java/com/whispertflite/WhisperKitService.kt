package com.whispertflite

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * 语音转录结果状态
 */
sealed class TranscriptionState {
    object Idle : TranscriptionState()
    object Initializing : TranscriptionState()
    object Processing : TranscriptionState()
    data class Success(val text: String) : TranscriptionState()
    data class Error(val message: String) : TranscriptionState()
}

/**
 * WhisperKit服务类，对WhisperKitNative进行封装，提供易用的API
 */
class WhisperKitService {
    private val TAG = "WhisperKitService"
    private val whisperKit = WhisperKitNative()
    private var nativePtr: Long = 0L
    
    private val _transcriptionState = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val transcriptionState: StateFlow<TranscriptionState> = _transcriptionState.asStateFlow()
    
    private val _isModelInitialized = MutableStateFlow(false)
    val isModelInitialized: StateFlow<Boolean> = _isModelInitialized.asStateFlow()
    
    /**
     * 初始化WhisperKit模型
     * 
     * @param context 应用上下文
     * @param modelName 模型名称，例如 "openai_whisper-tiny"
     * @param fromAssets 是否从assets加载模型
     */
    suspend fun initializeModel(context: Context, modelName: String = "openai_whisper-tiny", fromAssets: Boolean = true) {
        Log.d(TAG, "开始初始化模型: $modelName, 从assets加载: $fromAssets, 当前初始化状态: ${_isModelInitialized.value}")
        
        if (_isModelInitialized.value) {
            Log.d(TAG, "模型已经初始化，无需重复操作")
            return
        }
        
        _transcriptionState.value = TranscriptionState.Initializing
        
        withContext(Dispatchers.IO) {
            try {
                val modelPath: String
                
                if (fromAssets) {
                    // 从assets复制模型到应用私有目录
                    Log.d(TAG, "准备从assets复制模型文件")
                    modelPath = copyModelFromAssets(context, modelName)
                    Log.d(TAG, "模型已复制到: $modelPath")
                } else {
                    // 使用外部存储中的模型
                    modelPath = File(context.getExternalFilesDir(null), "models/$modelName").absolutePath
                    Log.d(TAG, "使用外部存储中的模型: $modelPath")
                }
                
                val reportPath = File(context.filesDir, "reports").absolutePath
                
                // 确保报告目录存在
                File(reportPath).also {
                    if (!it.exists()) {
                        val created = it.mkdirs()
                        Log.d(TAG, "创建报告目录: $reportPath, 结果: $created")
                    }
                }
                
                // 调用native方法初始化模型
                Log.d(TAG, "调用native方法初始化模型, 模型路径: $modelPath, 报告路径: $reportPath")
                nativePtr = whisperKit.init(
                    modelPath = modelPath,
                    audioPath = "",  // 初始化时不需要音频路径
                    reportPath = reportPath,
                    enableReport = true,
                    concurrentWorkers = 1
                )
                
                Log.d(TAG, "native初始化成功, 返回指针: $nativePtr")
                _isModelInitialized.value = true
                _transcriptionState.value = TranscriptionState.Idle
                Log.d(TAG, "模型初始化成功: $modelName")
            } catch (e: Exception) {
                Log.e(TAG, "模型初始化失败: ${e.message}", e)
                _transcriptionState.value = TranscriptionState.Error("模型初始化失败: ${e.message}")
                // 重置初始化状态
                _isModelInitialized.value = false
                // 如果有指针，释放它
                if (nativePtr != 0L) {
                    Log.d(TAG, "尝试释放失败的初始化资源: $nativePtr")
                    try {
                        whisperKit.release(nativePtr)
                    } catch (releaseEx: Exception) {
                        Log.e(TAG, "释放资源时发生异常: ${releaseEx.message}", releaseEx)
                    }
                    nativePtr = 0L
                }
                throw e
            }
        }
    }
    
    /**
     * 转录音频文件
     * 
     * @param audioFilePath 要转录的音频文件路径
     */
    suspend fun transcribeAudio(audioFilePath: String) {
        Log.d(TAG, "开始转录音频文件: $audioFilePath, 模型初始化状态: ${_isModelInitialized.value}")
        
        if (!_isModelInitialized.value) {
            Log.e(TAG, "尝试转录时模型未初始化")
            _transcriptionState.value = TranscriptionState.Error("模型未初始化")
            return
        }
        
        _transcriptionState.value = TranscriptionState.Processing
        
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "调用native转录方法, 指针: $nativePtr, 文件: $audioFilePath")
                val transcription = whisperKit.transcribe(nativePtr, audioFilePath)
                Log.d(TAG, "转录成功, 结果文本长度: ${transcription.length}")
                _transcriptionState.value = TranscriptionState.Success(transcription)
            } catch (e: Exception) {
                Log.e(TAG, "转录过程中发生异常: ${e.message}", e)
                _transcriptionState.value = TranscriptionState.Error("转录失败: ${e.message}")
            }
        }
    }
    
    /**
     * 从assets复制模型文件到应用私有目录
     * 
     * @param context 应用上下文
     * @param modelName 模型名称，例如 "openai_whisper-tiny"
     * @return 返回模型文件夹路径
     */
    private fun copyModelFromAssets(context: Context, modelName: String): String {
        Log.d(TAG, "开始从assets复制模型文件: $modelName")
        val modelDir = File(context.filesDir, "models/$modelName")
        if (!modelDir.exists()) {
            val created = modelDir.mkdirs()
            Log.d(TAG, "创建模型目录: ${modelDir.absolutePath}, 结果: $created")
        }
        
        try {
            val assetsList = context.assets.list("models/$modelName")
            if (assetsList == null || assetsList.isEmpty()) {
                Log.e(TAG, "模型文件夹为空或不存在: models/$modelName")
                throw IOException("模型文件夹为空或不存在: models/$modelName")
            }
            
            Log.d(TAG, "找到 ${assetsList.size} 个模型文件需要复制")
            
            for (fileName in assetsList) {
                val outFile = File(modelDir, fileName)
                
                if (!outFile.exists()) {
                    Log.d(TAG, "准备复制文件: $fileName")
                    val inputStream = context.assets.open("models/$modelName/$fileName")
                    
                    FileOutputStream(outFile).use { output ->
                        val bytesCopied = copyFile(inputStream, output)
                        Log.d(TAG, "文件复制完成: $fileName, 复制了 $bytesCopied 字节")
                    }
                    
                    inputStream.close()
                } else {
                    Log.d(TAG, "文件已存在，无需复制: $fileName")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "复制模型文件失败: ${e.message}", e)
            throw e
        }
        
        Log.d(TAG, "所有模型文件复制完成，模型路径: ${modelDir.absolutePath}")
        return modelDir.absolutePath
    }
    
    /**
     * 复制文件的辅助方法
     */
    private fun copyFile(input: InputStream, output: FileOutputStream): Long {
        val buffer = ByteArray(1024)
        var read: Int
        var totalBytes = 0L
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            totalBytes += read
        }
        return totalBytes
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "准备释放资源，当前指针: $nativePtr, 初始化状态: ${_isModelInitialized.value}")
        if (nativePtr != 0L) {
            try {
                Log.d(TAG, "调用native释放方法")
                whisperKit.release(nativePtr)
                Log.d(TAG, "资源释放成功")
            } catch (e: Exception) {
                Log.e(TAG, "释放资源时发生异常: ${e.message}", e)
            } finally {
                nativePtr = 0L
                _isModelInitialized.value = false
                _transcriptionState.value = TranscriptionState.Idle
            }
        } else {
            Log.d(TAG, "无需释放资源，指针为0")
        }
    }
}
