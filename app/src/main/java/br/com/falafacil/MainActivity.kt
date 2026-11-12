package br.com.falafacil

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import br.com.falafacil.ui.FalaFacilScreen
import br.com.falafacil.ui.theme.FalaFacilTheme

class MainActivity : ComponentActivity() {
    private val viewModel: FalaFacilViewModel by viewModels()
    private var isAppLocked by mutableStateOf(false)
    private var lockTaskMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.initializeTextToSpeech()
        setContent {
            FalaFacilTheme {
                FalaFacilScreen(
                    viewModel = viewModel,
                    isAppLocked = isAppLocked,
                    lockTaskMessage = lockTaskMessage,
                    onToggleLockTask = ::toggleLockTask,
                    onDismissLockTaskMessage = { lockTaskMessage = null }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        refreshLockTaskState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
            refreshLockTaskState()
        }
    }

    private fun applyImmersiveMode() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun refreshLockTaskState() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        isAppLocked = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        if (isAppLocked) lockTaskMessage = null
    }

    private fun toggleLockTask() {
        lockTaskMessage = null
        try {
            if (isAppLocked) {
                stopLockTask()
                refreshLockTaskState()
            } else {
                startLockTask()
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing) {
                        refreshLockTaskState()
                        if (!isAppLocked) {
                            lockTaskMessage = getString(R.string.lock_task_enable_help)
                        }
                    }
                }, 1_000L)
            }
        } catch (_: SecurityException) {
            refreshLockTaskState()
            if (!isAppLocked) lockTaskMessage = getString(R.string.lock_task_enable_help)
        } catch (_: IllegalStateException) {
            refreshLockTaskState()
            if (!isAppLocked) lockTaskMessage = getString(R.string.lock_task_enable_help)
        }
    }

    override fun onDestroy() {
        viewModel.shutdownTextToSpeech()
        super.onDestroy()
    }
}
