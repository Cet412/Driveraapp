package com.cera.driveraapp.camera

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class DummyLifecycleOwner : LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    // **Override property, bukan method**
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    init {
        // Set state ke CREATED → STARTED → RESUMED
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
}
