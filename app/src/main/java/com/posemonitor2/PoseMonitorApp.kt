package com.posemonitor2

import android.app.Application
import com.posemonitor2.settings.AppLocaleManager
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import java.lang.ref.WeakReference
import com.data.local.ExerciseDataSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PoseMonitorApp : Application() {
	
	@Inject
	lateinit var exerciseDataSeeder: ExerciseDataSeeder

	private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private var activityTracker: ActivityLifecycleCallbacks? = null

	override fun onCreate() {
		super.onCreate()
		AppLocaleManager.applyLocale(this)
		// Track active activities so we can recreate them when the theme changes
		val tracker = object : ActivityLifecycleCallbacks {
			private val activities = mutableListOf<WeakReference<Activity>>()

			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
				activities.add(WeakReference(activity))
			}

			override fun onActivityStarted(activity: Activity) {}
			override fun onActivityResumed(activity: Activity) {}
			override fun onActivityPaused(activity: Activity) {}
			override fun onActivityStopped(activity: Activity) {}
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
			override fun onActivityDestroyed(activity: Activity) {}

			fun recreateAll() {
				activities.forEach { ref ->
					val act = ref.get()
					if (act != null && !act.isFinishing) {
						try { act.recreate() } catch (_: Exception) {}
					}
				}
			}
		}
		activityTracker = tracker
		registerActivityLifecycleCallbacks(tracker)

		// Listen for theme change broadcasts and recreate all activities when received
		val filter = IntentFilter().apply { addAction(com.posemonitor2.ui.theme.ThemePreferences.ACTION_THEME_CHANGED) }
		val receiver = object : BroadcastReceiver() {
			override fun onReceive(context: android.content.Context?, intent: Intent?) {
				try {
					(activityTracker as? Any)?.let {
						// call recreateAll via reflection to avoid exposing interface type
						val method = it::class.java.methods.firstOrNull { m -> m.name == "recreateAll" }
						method?.invoke(it)
					}
				} catch (_: Exception) {}
			}
		}

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
		} else {
			registerReceiver(receiver, filter)
		}
		
		applicationScope.launch {
			exerciseDataSeeder.seedIfNeeded()
		}
	}
}
