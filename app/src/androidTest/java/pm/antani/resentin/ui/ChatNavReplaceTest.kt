package pm.antani.resentin.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for navigateToChat: chat/A -> chat/B must replace (never
 * stack, never silent no-op). `launchSingleTop`/`restoreState` both match by
 * route *pattern* and turn chat->chat into a no-op — navigateToChat must use
 * neither, only popUpTo(HOME).
 */
@RunWith(AndroidJUnit4::class)
class ChatNavReplaceTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController

    private fun setUpNav() {
        rule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Text("home") }
                composable("chat/{networkSlug}/{channelName}?jumpTo={jumpTo}") { Text("chat") }
            }
        }
        rule.runOnIdle { nav.navigateToChat("a", "#x") }
        rule.waitForIdle()
        assertEquals(listOf("home", "chat(a/#x)"), stack())
    }

    private fun stack(): List<String> = nav.currentBackStack.value
        .filter { it.destination.route != null }
        .map {
            val route = it.destination.route
            if (route?.startsWith("chat") == true) {
                "chat(${it.arguments?.getString("networkSlug")}/${it.arguments?.getString("channelName")})"
            } else {
                route ?: "?"
            }
        }

    @Test
    fun openDifferentChat_replacesInsteadOfStacking() {
        setUpNav()
        rule.runOnIdle { nav.navigateToChat("a", "y") }
        rule.waitForIdle()
        assertEquals(listOf("home", "chat(a/y)"), stack())
    }

    @Test
    fun reopenSameChat_isNoop() {
        setUpNav()
        rule.runOnIdle { nav.navigateToChat("a", "#x") }
        rule.waitForIdle()
        assertEquals(listOf("home", "chat(a/#x)"), stack())
    }

    @Test
    fun sameChatWithNewJumpTo_isNoop() {
        setUpNav()
        rule.runOnIdle { nav.navigateToChat("a", "#x", jumpTo = 123L) }
        rule.waitForIdle()
        assertEquals(listOf("home", "chat(a/#x)"), stack())
    }
}
