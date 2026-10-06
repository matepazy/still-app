package app.still.ui

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.snapshots.Snapshot
import app.still.MainActivity
import app.still.StillApplication
import app.still.data.settings.ThemePreference
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Runs on the separate preview package; compares animation work and real tab selection. */
class NavigationAnimationInstrumentation : Instrumentation() {
    private var baseline = false
    private var cycles = 3
    @Volatile private var releasedAt = 0L
    @Volatile private var upDispatchMs = 0L
    @Volatile private var downDrawMs = -1L
    @Volatile private var upDrawMs = -1L
    @Volatile private var pressedAt = 0L
    private val draws = AtomicInteger()
    @Volatile private var requestedTab = ""
    @Volatile private var destinationDrawMs = -1L

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        baseline = arguments?.getString("baseline") == "true"
        cycles = arguments?.getString("cycles")?.toIntOrNull() ?: 3
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val report = JSONObject()
        check(targetContext.packageName != "app.still") { "Use an isolated preview package" }
        runOnMainSync { } // Let Application.onCreate finish before accessing its container.
        val settings = (targetContext.applicationContext as StillApplication).container.settingsRepository
        val original = runBlocking { settings.settings.first() }
        var exitCode = 0
        try {
            runBlocking { settings.setTheme(ThemePreference.Fall) }
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_OPEN_TODAY, true))
            runOnMainSync {
                NavigationDrawObserver.onDraw = { route ->
                    if (releasedAt != 0L && destinationDrawMs < 0 && route == requestedTab) {
                        destinationDrawMs = SystemClock.uptimeMillis() - releasedAt
                    }
                }
                val callback = activity.window.callback
                activity.window.callback = object : Window.Callback by callback {
                    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            pressedAt = event.eventTime
                            downDrawMs = -1L
                        }
                        val start = SystemClock.uptimeMillis()
                        val handled = callback.dispatchTouchEvent(event)
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            releasedAt = event.eventTime
                            upDispatchMs = SystemClock.uptimeMillis() - start
                            upDrawMs = -1L
                        }
                        return handled
                    }
                }
                activity.window.decorView.viewTreeObserver.addOnDrawListener(ViewTreeObserver.OnDrawListener {
                    draws.incrementAndGet()
                    val now = SystemClock.uptimeMillis()
                    if (pressedAt != 0L && downDrawMs < 0) downDrawMs = now - pressedAt
                    if (releasedAt != 0L && upDrawMs < 0) upDrawMs = now - releasedAt

                })
            }
            awaitText("Screen time today")
            SystemClock.sleep(2000)

            val applies = AtomicInteger()
            val observer = Snapshot.registerApplyObserver { _, _ -> applies.incrementAndGet() }
            val first = checkNotNull(uiAutomation.takeScreenshot())
            SystemClock.sleep(1000)
            val second = checkNotNull(uiAutomation.takeScreenshot())
            observer.dispose()
            val changed = cardDifference(first, second)
            first.recycle()
            second.recycle()
            report.put("leafChangedPixels", changed)
            report.put("idleSnapshotApplies", applies.get())
            check(changed > 100) { "Leaves are frozen: $changed changed pixels" }
            if (!baseline) check(applies.get() <= 5) {
                "Ambient animation is still applying snapshots: ${applies.get()}"
            }

            // Warm each destination before measuring repeated switches in both directions.
            listOf("Timeline", "Apps", "Statistics", "Today").forEach { selectTab(it); SystemClock.sleep(700) }
            val samples = JSONArray()
            report.put("tabSamples", samples)
            val routes = listOf("Timeline", "Apps", "Statistics", "Today", "Apps", "Timeline", "Today", "Statistics")
            repeat(cycles) {
                routes.forEach { label ->
                    val selectedMs = selectTab(label)
                    SystemClock.sleep(650)
                    samples.put(JSONObject().put("tab", label).put("selectionMs", selectedMs)
                        .put("upDispatchMs", upDispatchMs).put("pressToDrawMs", downDrawMs).put("releaseToDrawMs", upDrawMs)
                        .put("destinationDrawMs", destinationDrawMs))
                    if (!baseline) {
                        check(downDrawMs in 0..100 && upDrawMs in 0..100) {
                            "Delayed draw on $label: press=$downDrawMs ms, release=$upDrawMs ms"
                        }
                        // Content includes chart layout; measure it separately from immediate bar feedback.
                        check(destinationDrawMs in 0..250) { "Delayed destination draw on $label: $destinationDrawMs ms" }
                    }
                }
            }
            selectTab("Apps")
            SystemClock.sleep(1000)
            val inactiveStart = draws.get()
            SystemClock.sleep(500)
            val inactiveDraws = draws.get() - inactiveStart
            report.put("inactiveDraws", inactiveDraws)
            if (!baseline) check(inactiveDraws <= 2) { "Hidden leaves keep drawing: $inactiveDraws frames" }
            selectTab("Today")
            SystemClock.sleep(300)
            val resumedFirst = checkNotNull(uiAutomation.takeScreenshot())
            SystemClock.sleep(500)
            val resumedSecond = checkNotNull(uiAutomation.takeScreenshot())
            check(cardDifference(resumedFirst, resumedSecond) > 100) { "Leaves did not resume after switching tabs" }
            resumedFirst.recycle()
            resumedSecond.recycle()

            runOnMainSync { activity.moveTaskToBack(true) }
            SystemClock.sleep(500)
            val backgroundStart = draws.get()
            SystemClock.sleep(500)
            report.put("backgroundDraws", draws.get() - backgroundStart)
            if (!baseline) check(draws.get() == backgroundStart) { "Background window keeps drawing" }
            targetContext.startActivity(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            awaitText("Screen time today")
            SystemClock.sleep(500)
            val foregroundFirst = checkNotNull(uiAutomation.takeScreenshot())
            SystemClock.sleep(500)
            val foregroundSecond = checkNotNull(uiAutomation.takeScreenshot())
            check(cardDifference(foregroundFirst, foregroundSecond) > 100) { "Leaves did not resume from background" }
            foregroundFirst.recycle()
            foregroundSecond.recycle()

            val file = File(targetContext.filesDir, "navigation-animation-report.json")
            file.writeText(report.toString(2))
            result.putString("report", report.toString())
            result.putString("result", "PASS")
        } catch (error: Throwable) {
            exitCode = 1
            result.putString("error", error.stackTraceToString())
            result.putString("report", report.toString())
        } finally {
            runOnMainSync { NavigationDrawObserver.onDraw = null }
            runBlocking { settings.setTheme(original.theme) }
        }
        finish(exitCode, result)
    }

    private fun awaitText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            if (findText(text).isNotEmpty()) return
            SystemClock.sleep(50)
        }
        error("Missing screen text: $text")
    }

    private fun selectTab(label: String): Long {
        val node = findText(label)
            .filter { it.text?.toString() == label }
            .maxBy { bounds(it).bottom }
        val rect = bounds(node)
        val ancestors = generateSequence(node) { it.parent }.take(4).toList()
        val x = rect.exactCenterX()
        val y = rect.exactCenterY()
        requestedTab = label.lowercase(java.util.Locale.ROOT)
        destinationDrawMs = -1L
        val down = SystemClock.uptimeMillis()
        releasedAt = 0L
        sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0).apply { source = 0x1002 })
        SystemClock.sleep(60)
        val released = SystemClock.uptimeMillis()
        sendPointerSync(MotionEvent.obtain(down, released, MotionEvent.ACTION_UP, x, y, 0).apply { source = 0x1002 })
        val deadline = released + 2000
        while (SystemClock.uptimeMillis() < deadline) {
            if (ancestors.any { it.refresh() && it.isSelected }) return SystemClock.uptimeMillis() - released
            SystemClock.sleep(5)
        }
        error("Tab selection timed out: $label")
    }

    private fun bounds(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    // Compose exposes virtual children, rather than implementing the platform text-search API.
    private fun findText(text: String): List<AccessibilityNodeInfo> {
        val matches = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            if (node.text?.toString()?.contains(text) == true) matches += node
            repeat(node.childCount) { index -> node.getChild(index)?.let(::visit) }
        }
        uiAutomation.rootInActiveWindow?.let(::visit)
        return matches
    }

    private fun cardDifference(first: Bitmap, second: Bitmap): Int {
        check(first.width == second.width && first.height == second.height)
        var changed = 0
        for (y in (first.height * .15f).toInt() until (first.height * .32f).toInt() step 2) {
            for (x in (first.width * .03f).toInt() until (first.width * .97f).toInt() step 2) {
                if (first.getPixel(x, y) != second.getPixel(x, y)) changed++
            }
        }
        return changed
    }
}
