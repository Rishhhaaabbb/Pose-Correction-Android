package com.posemonitor2

import android.app.Application
import com.posemonitor2.settings.AppLocaleManager
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import java.lang.ref.WeakReference
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.data.local.ExerciseDataSeeder
import com.posemonitor2.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PoseMonitorApp : Application(), Configuration.Provider {

	@Inject lateinit var exerciseDataSeeder: ExerciseDataSeeder
	@Inject lateinit var workerFactory: HiltWorkerFactory

	private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var activityTracker: ActivityLifecycleCallbacks? = null

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

	override fun onCreate() {
		super.onCreate()
		AppLocaleManager.applyLocale(this)

		val tracker = object : ActivityLifecycleCallbacks {
			private val activities = mutableListOf<WeakReference<Activity>>()
			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { activities.add(WeakReference(activity)) }
			override fun onActivityStarted(activity: Activity) {}
			override fun onActivityResumed(activity: Activity) {}
			override fun onActivityPaused(activity: Activity) {}
			override fun onActivityStopped(activity: Activity) {}
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
			override fun onActivityDestroyed(activity: Activity) {}
			fun recreateAll() {
				activities.forEach { ref ->
					val act = ref.get()
					if (act != null && !act.isFinishing) try { act.recreate() } catch (_: Exception) {}
				}
			}
		}
		activityTracker = tracker
		registerActivityLifecycleCallbacks(tracker)

		val filter = IntentFilter().apply { addAction(com.posemonitor2.ui.theme.ThemePreferences.ACTION_THEME_CHANGED) }
		val receiver = object : BroadcastReceiver() {
			override fun onReceive(context: android.content.Context?, intent: Intent?) {
				try {
					(activityTracker as? Any)?.let {
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

		applicationScope.launch { exerciseDataSeeder.seedIfNeeded() }

		ProcessLifecycleOwner.get().lifecycle.addObserver(AppForegroundObserver(this))
	}
}

private class AppForegroundObserver(private val app: Application) : DefaultLifecycleObserver {
	override fun onStart(owner: LifecycleOwner) {
		Log.d("AppForegroundObserver", "App foregrounded — scheduling immediate sync")
		SyncWorker.enqueueImmediateSync(app)
	}
}
