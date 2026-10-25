package ai.freya.app

import android.Manifest
import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end journey on a real emulator against a real FREYA backend (assistant-only mode,
 * deterministic mock provider — no API keys). UI Automator drives the app like a user, so
 * the live, state-driven animations keep running and screenshots show real states.
 *
 * Hard assertions: sign-up, Home renders, a typed message streams a real reply back.
 * Everything else is best-effort and logged, so a missing emulator capability (e.g. no
 * speech service) never hides the core result.
 */
@RunWith(AndroidJUnit4::class)
class FreyaJourneyTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val pkg = context.packageName
    private val outDir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    private val timeout = 15_000L

    @Test
    fun journey() {
        instrumentation.uiAutomation.grantRuntimePermission(pkg, Manifest.permission.RECORD_AUDIO)
        launch()

        // Diagnostics: what the accessibility layer exposes right after launch.
        Thread.sleep(3000)
        dismissSystemDialogs()
        val ua = instrumentation.uiAutomation
        Log.i(TAG, "active root pkg=${ua.rootInActiveWindow?.packageName} children=${ua.rootInActiveWindow?.childCount}")
        ua.windows.forEach { w -> Log.i(TAG, "window type=${w.type} layer=${w.layer} active=${w.isActive} title=${w.title} root=${w.root?.packageName}") }
        runCatching { device.dumpWindowHierarchy(File(outDir, "hierarchy-launch.xml")) }
            .onFailure { Log.w(TAG, "dump failed: ${it.message}") }

        // ---- sign up (fresh install) ----
        val createLink = device.wait(Until.findObject(By.textContains("Create an account")), timeout)
        shot("00-sign-in")
        if (createLink != null) {
            createLink.click()
            device.wait(Until.hasObject(By.text("Display name")), timeout)
            device.wait(Until.findObjects(By.clazz("android.widget.EditText")), timeout)
            setField(0, "Justin")
            setField(1, "journey+${System.currentTimeMillis()}@example.com")
            setField(2, "FreyaJourney#2026")
            hideKeyboard()
            // The register title is also "Create account"; the button label is the lower one.
            // (Compose exposes a button's label as a child text node of the clickable button.)
            device.findObjects(By.text("Create account")).maxBy { it.visibleBounds.top }.click()
        }

        // ---- home (idle) ----
        dismissSystemDialogs()
        val idle = device.wait(Until.findObject(By.text("STANDING BY")), 30_000)
        assertNotNull("Home did not show the idle Core", idle)
        Thread.sleep(1500)
        shot("01-home-idle-avatar")

        // ---- type a message; it must stream back from the backend ----
        val input = device.findObject(By.clazz("android.widget.EditText"))
        input.text = "hello FREYA"
        hideKeyboard()
        device.wait(Until.findObject(By.desc("Send")), timeout).click()
        Thread.sleep(250)
        shot("02-processing")
        val reply = device.wait(Until.findObject(By.textContains("Online and ready")), 30_000)
        assertNotNull("No streamed reply from the backend", reply)
        Thread.sleep(800)
        shot("03-conversation")

        step("speaking") {
            // Turn on spoken replies, then ask something; captures the SPEAKING state if the
            // emulator image has a TTS engine.
            device.findObject(By.text("SETTINGS")).click()
            device.wait(Until.hasObject(By.text("Appearance")), timeout)
            val label = findWithScroll(By.text("Voice output"))
            label?.parent?.findObject(By.checkable(true))?.let { if (!it.isChecked) it.click() }
            device.findObjects(By.text("FREYA")).maxByOrNull { it.visibleBounds.top }?.click()
            device.wait(Until.hasObject(By.text("STANDING BY")), timeout)
            device.findObject(By.clazz("android.widget.EditText")).text = "Tell me about memory"
            hideKeyboard()
            device.wait(Until.findObject(By.desc("Send")), timeout).click()
            device.findObject(By.desc("Show FREYA Core"))?.click()
            if (device.wait(Until.hasObject(By.text("SPEAKING")), 15_000)) {
                Thread.sleep(700); shot("05b-speaking")
                Thread.sleep(500); shot("05c-speaking-later")
            } else {
                Log.i(TAG, "no SPEAKING state (no TTS engine on this image?)")
            }
            device.wait(Until.hasObject(By.text("STANDING BY")), 30_000)
            device.findObject(By.desc("Show conversation"))?.click()
        }

        step("details") {
            device.findObject(By.text("Details"))?.click()
            Thread.sleep(600)
            shot("04-technical-details")
        }

        step("core-view") {
            device.findObject(By.desc("Show FREYA Core")).click()
            Thread.sleep(1200)
            shot("05-core-with-latest-reply")
        }

        step("listening") {
            val talk = device.findObject(By.desc("Push to talk"))
            if (talk == null) {
                Log.i(TAG, "Push to talk not offered (speech service unavailable on this image)")
                return@step
            }
            talk.click()
            if (device.wait(Until.hasObject(By.text("LISTENING TO YOU")), 5_000)) {
                Thread.sleep(700)
                shot("06-listening")
            }
            device.findObject(By.desc("Stop"))?.click()
            device.wait(Until.hasObject(By.text("STANDING BY")), 8_000)
        }

        // ---- Control Center ----
        step("control-center") {
            device.findObject(By.text("TOOLS")).click()
            device.wait(Until.hasObject(By.text("AI systems")), timeout)
            Thread.sleep(2500)
            shot("07-control-center")
            scroll(); Thread.sleep(500); shot("08-control-integrations")
            scroll(); Thread.sleep(500); shot("09-control-tasks")
        }

        step("approval") {
            val run = findWithScroll(By.text("Run"))
            run!!.click()
            device.wait(Until.hasObject(By.text("Run simulation")), timeout)
            setField(0, "demo_project")
            setField(1, "Journey test — should be denied")
            device.findObject(By.text("Review & approve")).click()
            device.wait(Until.hasObject(By.text("APPROVAL REQUIRED")), timeout)
            Thread.sleep(900)
            shot("10-awaiting-approval")
            device.findObject(By.text("Deny")).click()
            Thread.sleep(800)
        }

        step("read-only-task") {
            val runs = device.findObjects(By.text("Run"))
            // Second tool in the backend registry is inspect_graph (read-only).
            runs.getOrNull(1)?.click() ?: return@step
            device.wait(Until.hasObject(By.text("Run now")), timeout)
            device.findObjects(By.clazz("android.widget.EditText")).firstOrNull()?.text = "demo_graph"
            device.findObject(By.text("Run now")).click()
            Thread.sleep(400)
            shot("11-task-running")
            Thread.sleep(3000)
            shot("12-task-finished")
        }

        step("home-after-task") {
            device.findObjects(By.text("FREYA")).maxByOrNull { it.visibleBounds.top }?.click()
            Thread.sleep(1500)
            shot("13-home-after-task")
        }

        // ---- appearance: light theme + abstract core ----
        step("light-theme") {
            device.findObject(By.text("SETTINGS")).click()
            device.wait(Until.hasObject(By.text("Appearance")), timeout)
            device.findObject(By.text("Light"))?.click()
            device.findObject(By.text("Abstract core"))?.click()
            Thread.sleep(800)
            shot("14-settings-light")
            device.findObjects(By.text("FREYA")).maxByOrNull { it.visibleBounds.top }?.click()
            Thread.sleep(1500)
            shot("15-home-light-abstract-core")
            device.findObject(By.text("SETTINGS")).click()
            device.wait(Until.hasObject(By.text("Appearance")), timeout)
            device.findObject(By.text("Dark"))?.click()
            device.findObject(By.text("Avatar"))?.click()
        }
    }

    private fun launch() {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), timeout)
    }

    /** Cold CI emulators sometimes show "<System app> isn't responding"; that's not FREYA. */
    private fun dismissSystemDialogs() {
        repeat(3) {
            val wait = device.findObject(By.text("Wait")) ?: device.findObject(By.res("android:id/aerr_wait"))
            if (wait == null) return
            Log.i(TAG, "dismissing system ANR dialog")
            wait.click()
            Thread.sleep(1500)
        }
    }

    /** Re-finds the field each time: typing recomposes the screen and stales old references. */
    private fun setField(index: Int, value: String) {
        repeat(3) {
            try {
                device.findObjects(By.clazz("android.widget.EditText"))[index].text = value
                return
            } catch (e: androidx.test.uiautomator.StaleObjectException) {
                Thread.sleep(300)
            }
        }
        device.findObjects(By.clazz("android.widget.EditText"))[index].text = value
    }

    private fun hideKeyboard() {
        val imeShown = instrumentation.uiAutomation.windows.any {
            it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        if (imeShown) {
            device.pressBack()
            Thread.sleep(400)
        }
    }

    private fun scroll() {
        device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.7f)
    }

    private fun findWithScroll(selector: BySelector): UiObject2? {
        repeat(6) {
            device.findObject(selector)?.let { return it }
            scroll()
            Thread.sleep(300)
        }
        return device.findObject(selector)
    }

    private fun shot(name: String) {
        val ok = device.takeScreenshot(File(outDir, "$name.png"))
        Log.i(TAG, "screenshot $name -> $ok")
    }

    private inline fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "step '$name' skipped: ${t.message}")
            shot("zz-failed-$name")
        }
    }

    private companion object {
        const val TAG = "FreyaJourney"
    }
}
