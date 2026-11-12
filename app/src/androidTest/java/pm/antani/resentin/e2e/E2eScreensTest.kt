package pm.antani.resentin.e2e

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pm.antani.resentin.ui.e2e.KeyAlarmCard
import pm.antani.resentin.ui.e2e.SasCard
import pm.antani.resentin.ui.e2e.SystemRow

/** Draws the encrypted-chat cards once and saves them, so a person can look at them (files/screens in the app data). */
@RunWith(AndroidJUnit4::class)
class E2eScreensTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bmp.width > 0)
    }

    @Test
    fun cards() {
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.fillMaxWidth()) {
                    KeyAlarmCard(
                        "Alice",
                        AlarmRec("Alice", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefg", "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789+/ABCDE"),
                        onAccept = {}, onKeep = {},
                    )
                    SasCard(SasView("a", "compare", false, true, listOf(0, 23, 35, 30, 59, 5, 30)), {})
                    SasCard(SasView("b", "asked", false, false, null), {})
                    SystemRow("verified|false|", "Alice")
                    SystemRow("cancelled|true|mac-mismatch", "Alice")
                }
            }
        }
        shot("cards")
    }
}
