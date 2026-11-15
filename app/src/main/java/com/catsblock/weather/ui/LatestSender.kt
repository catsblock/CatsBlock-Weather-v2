package com.catsblock.weather.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class LatestSender<T>(scope: CoroutineScope, private val send: suspend (T) -> Unit) {
    private val channel = Channel<T>(Channel.CONFLATED)

    init {
        scope.launch {
            for (value in channel) send(value)
        }
    }

    fun submit(value: T) {
        channel.trySend(value)
    }
}
