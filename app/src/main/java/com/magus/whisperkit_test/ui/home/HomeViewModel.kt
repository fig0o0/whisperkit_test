package com.magus.whisperkit_test.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.whispertflite.WhisperKitService
import com.whispertflite.TranscriptionState
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val TAG = "HomeViewModel"
    
    private val _message = MutableStateFlow("点击按钮开始转录")
    val message: StateFlow<String> = _message.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val whisperKitService = WhisperKitService()
    
    fun transcribeSampleFile() {
        viewModelScope.launch {
            Log.d(TAG, "开始转录示例文件，准备转录流程")
            _isLoading.value = true
            try {
                // 初始化模型
                Log.d(TAG, "准备初始化模型")
                whisperKitService.initializeModel(context)
                Log.d(TAG, "模型初始化完成")
                
                // 准备示例音频文件路径
                val samplePath = "audios/samples_jfk.wav"
                val outFile = context.filesDir.resolve("sample_audio.wav")
                Log.d(TAG, "示例音频源文件: $samplePath, 目标文件: ${outFile.absolutePath}")
                
                // 复制音频文件到应用目录
                try {
                    context.assets.open(samplePath).use { input ->
                        Log.d(TAG, "成功打开资源文件，大小: ${input.available()}")
                        outFile.outputStream().use { output ->
                            val bytesCopied = input.copyTo(output)
                            Log.d(TAG, "成功复制文件，复制了 $bytesCopied 字节")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "复制音频文件失败: ${e.message}", e)
                    _message.value = "复制音频文件失败: ${e.message}"
                    _isLoading.value = false
                    return@launch
                }
                
                // 监听转录状态
                Log.d(TAG, "开始监听转录状态")
                launch {
                    whisperKitService.transcriptionState.collectLatest { state ->
                        Log.d(TAG, "收到转录状态更新: $state")
                        when (state) {
                            is TranscriptionState.Success -> {
                                Log.d(TAG, "转录成功: ${state.text}")
                                _message.value = state.text
                                _isLoading.value = false
                            }
                            is TranscriptionState.Error -> {
                                Log.e(TAG, "转录错误: ${state.message}")
                                _message.value = "错误: ${state.message}"
                                _isLoading.value = false
                            }
                            is TranscriptionState.Initializing -> {
                                Log.d(TAG, "转录初始化中")
                            }
                            is TranscriptionState.Processing -> {
                                Log.d(TAG, "转录处理中")
                            }
                            else -> { 
                                Log.d(TAG, "其他转录状态: $state") 
                            }
                        }
                    }
                }
                
                // 开始转录
                Log.d(TAG, "开始转录音频文件: ${outFile.absolutePath}")
                whisperKitService.transcribeAudio(outFile.absolutePath)
                
            } catch (e: Exception) {
                Log.e(TAG, "转录过程中发生异常: ${e.message}", e)
                _message.value = "转录失败: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    override fun onCleared() {
        Log.d(TAG, "ViewModel正在清理，准备释放WhisperKitService资源")
        super.onCleared()
        try {
            whisperKitService.release()
            Log.d(TAG, "WhisperKitService资源释放成功")
        } catch (e: Exception) {
            Log.e(TAG, "释放WhisperKitService资源失败: ${e.message}", e)
        }
    }
} 