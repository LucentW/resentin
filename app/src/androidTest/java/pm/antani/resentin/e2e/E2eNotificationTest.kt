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
        // Without the permission nothing can be posted (a clean runner): skip, do not fail.
        org.junit.Assume.assumeTrue("notifications are not allowed for the app", nm.areNotificationsEnabled())
        nm.cancelAll()
        (ctx.applicationContext as AppApplication).container.notificationRouter.notifyEncrypted("azzurra", "SecretNick")

        // notify() is asynchronous: give the system a moment to show it.
        fun find() = nm.activeNotifications.firstOrNull {
            val t = it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)
            t?.contains("cifrato", true) == true || t?.contains("encrypted", true) == true
        }
        var n = find()
        val end = System.currentTimeMillis() + 5_000
        while (n == null && System.currentTimeMillis() < end) { Thread.sleep(100); n = find() }
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
