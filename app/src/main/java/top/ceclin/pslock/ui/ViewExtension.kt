package top.ceclin.pslock.ui

import android.view.View
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

@UseExperimental(ExperimentalCoroutinesApi::class)
fun View.clickEventFlow() = callbackFlow<View> {
    setOnClickListener {
        if (!isClosedForSend) {
            offer(it)
        }
    }
    awaitClose {
        setOnClickListener(null)
    }
}.conflate()