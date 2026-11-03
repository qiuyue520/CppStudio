package com.xiaonai.cppstudio.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.xiaonai.cppstudio.cppstudio_application
import com.xiaonai.cppstudio.service.keep_alive_service

class app_lifecycle_observer(private val app: cppstudio_application) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        app.keep_alive_service_?.hide_notification()
    }

    override fun onStop(owner: LifecycleOwner) {
        app.keep_alive_service_?.show_notification()
    }

    companion object {
        fun init(app: cppstudio_application) {
            val observer = app_lifecycle_observer(app)
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        }
    }
}