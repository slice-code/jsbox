package com.example

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object RemotePlayManager {
    private val _playTriggerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val playTriggerFlow = _playTriggerFlow.asSharedFlow()

    fun triggerPlay() {
        _playTriggerFlow.tryEmit(Unit)
    }
}
