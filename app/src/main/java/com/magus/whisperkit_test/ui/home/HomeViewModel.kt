package com.magus.whisperkit_test.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    
    private val _message = MutableStateFlow("点击按钮显示消息")
    val message: StateFlow<String> = _message.asStateFlow()
    
    fun showHelloWorld() {
        _message.value = "Hello World!"
    }
} 