package app.still.ui

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.MonotonicFrameClock
import app.still.MainActivity
import app.still.StillApplication
import app.still.data.settings.SettingsRepository
import app.still.data.settings.LastDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Runs only in a preview package, preserving the user's installed Still settings. */
class ReduceMotionInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        check(targetContext.packageName != "app.still") { "Use an isolated preview package" }
        runOnMainSync { }
        val repository = (targetContext.applicationContext as StillApplication).container.settingsRepository
        val original = runBlocking { repository.settings.first() }
        val result = Bundle()
        var exitCode = 0
        try {
            runBlocking { repository.setReduceMotion(false) }
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_OPEN_TODAY, true))
            click("Settings")
            val toggle = awaitNode("Reduce motion")
            check(!toggle.isChecked) { "Reduce motion should start off" }
            click("Reduce motion")
            await { runBlocking { repository.settings.first().reduceMotion } }
            check(awaitNode("Reduce motion").isChecked) { "Switch did not update" }
            // Read the persisted value through a second repository, then recreate the UI.
            check(runBlocking { SettingsRepository(targetContext).settings.first().reduceMotion })
            runOnMainSync { activity.recreate() }
            SystemClock.sleep(800)
            check(awaitNode("Reduce motion").isChecked) { "Setting did not survive recreation" }
            result.putString("setting", "UI toggle, persistence and recreation passed")

            verifyIconMotion()
            result.putString("icons", "Disabled taps, mid-animation cancellation and re-enable passed")

            click("Back")
            listOf("Apps", "Statistics", "Timeline", "Today", "Timeline", "Apps", "Today").forEach { tab ->
                click(tab)
                await { runBlocking { repository.settings.first().lastDestination == LastDestination.valueOf(tab) } }
            }
            result.putString("navigation", "Seven repeated tab switches passed with motion reduced")
            click("Settings")
            click("Reduce motion")
            await { !runBlocking { repository.settings.first().reduceMotion } }
            check(!awaitNode("Reduce motion").isChecked)
            result.putString("result", "PASS")
        } catch (error: Throwable) {
            exitCode = 1
            result.putString("error", error.stackTraceToString())
        } finally {
            runBlocking {
                repository.setReduceMotion(original.reduceMotion)
                repository.setLastDestination(original.lastDestination)
            }
        }
        finish(exitCode, result)
    }

    private fun verifyIconMotion() {
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(System.nanoTime())
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + clock)
        lateinit var motion: NavigationIconMotion
        try {
            runOnMainSync {
                motion = NavigationIconMotion(scope)
                motion.setMotionReduced(true)
                listOf(TodayRoute, TimelineRoute, AppsRoute, StatisticsRoute).forEach { route ->
                    motion.press(route, alreadySelected = false)
                    motion.play(route, alreadySelected = false)
                    check(motion.progress(route) == 1f && motion.fillingRoute == null)
                }
                motion.setMotionReduced(false)
                motion.press(AppsRoute, alreadySelected = false)
            }
            SystemClock.sleep(120)
            runOnMainSync {
                check(motion.progress(AppsRoute) in 0.01f..0.99f) { "Normal icon feedback did not animate" }
                motion.setMotionReduced(true)
                check(motion.progress(AppsRoute) == 1f && motion.fillingRoute == null)
            }
            SystemClock.sleep(650)
            runOnMainSync {
                check(motion.progress(AppsRoute) == 1f)
                motion.setMotionReduced(false)
                motion.play(StatisticsRoute, alreadySelected = true)
                check(motion.progress(StatisticsRoute) == 0f)
            }
            SystemClock.sleep(700)
            runOnMainSync { check(motion.progress(StatisticsRoute) == 1f) }
        } finally {
            scope.cancel()
        }
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        error("Timed out waiting for reduced-motion state")
    }

    private fun awaitNode(label: String): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await {
            fun visit(node: AccessibilityNodeInfo) {
                if (node.text?.toString() == label || node.contentDescription?.toString() == label ||
                    node.text?.toString()?.startsWith("$label\n") == true) found = node
                repeat(node.childCount) { index -> node.getChild(index)?.let(::visit) }
            }
            uiAutomation.rootInActiveWindow?.let(::visit)
            found != null
        }
        return generateSequence(found!!) { it.parent }.firstOrNull { it.isClickable } ?: found!!
    }

    private fun click(label: String) {
        val bounds = Rect().also(awaitNode(label)::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN,
            bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = 0x1002 })
        SystemClock.sleep(60)
        sendPointerSync(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
            bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = 0x1002 })
        SystemClock.sleep(400)
    }
}
