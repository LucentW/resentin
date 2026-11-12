package pm.antani.resentin.e2e

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import pm.antani.resentin.AppApplication

/** An encrypted message's notification says only that: no nick, no text, here or on the lock screen. */
@RunWith(AndroidJUnit4::class)
class E2eNotificationTest {
    @Test
    fun theNotificationNamesNeitherThePeerNorTheText() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
        (ctx.applicationContext as AppApplication).container.notificationRouter.notifyEncrypted("azzurra", "SecretNick")

        val n = nm.activeNotifications.firstOrNull { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.contains("cifrato", true) == true
            || it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.contains("encrypted", true) == true }
        assertNotNull("posted (is POST_NOTIFICATIONS granted?)", n)
        val shown = n!!.notification
        val all = listOfNotNull(
            shown.extras.getCharSequence(Notification.EXTRA_TITLE), shown.extras.getCharSequence(Notification.EXTRA_TEXT),
            shown.extras.getCharSequence(Notification.EXTRA_SUB_TEXT), shown.extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            shown.publicVersion?.extras?.getCharSequence(Notification.EXTRA_TITLE), shown.publicVersion?.extras?.getCharSequence(Notification.EXTRA_TEXT),
        ).joinToString("|")
        assertFalse("no nick anywhere: $all", all.contains("SecretNick", ignoreCase = true))
        assertEquals(Notification.VISIBILITY_PRIVATE, shown.visibility)
        nm.cancelAll()
    }
}
