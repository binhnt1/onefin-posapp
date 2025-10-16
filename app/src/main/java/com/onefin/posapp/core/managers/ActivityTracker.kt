package com.onefin.posapp.core.managers

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityTracker @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private var currentActivityRef: WeakReference<Activity>? = null

    fun getCurrentActivity(): Activity? {
        return currentActivityRef?.get()
    }

    fun isActivityOfType(activityClass: Class<*>): Boolean {
        val current = getCurrentActivity()
        return current != null && current::class.java == activityClass
    }

    fun isLaunchedExternally(): Boolean {
        val currentActivity = getCurrentActivity() ?: return false
        return currentActivity.intent?.let { intent ->
            intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 &&
                    intent.component?.packageName != currentActivity.packageName
        } ?: false
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivityRef?.get() == activity) {
            currentActivityRef = null
        }
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}