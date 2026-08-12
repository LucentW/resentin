package pm.antani.resentin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder
import pm.antani.resentin.AppContainer
import pm.antani.resentin.BuildConfig
import pm.antani.resentin.irc.isQueryTarget
import pm.antani.resentin.ui.appsettings.AppSettingsScreen
import pm.antani.resentin.ui.appsettings.AppSettingsViewModel
import pm.antani.resentin.ui.channelsettings.ChannelSettingsScreen
import pm.antani.resentin.ui.channelsettings.ChannelSettingsViewModel
import pm.antani.resentin.ui.chat.ChatScreen
import pm.antani.resentin.ui.chat.ChatViewModel
import pm.antani.resentin.ui.home.HomeScreen
import pm.antani.resentin.ui.home.HomeViewModel
import pm.antani.resentin.ui.login.LoginScreen
import pm.antani.resentin.ui.login.LoginViewModel
import pm.antani.resentin.ui.members.MemberListScreen
import pm.antani.resentin.ui.members.MembersViewModel
import pm.antani.resentin.ui.networksettings.NetworkSettingsScreen
import pm.antani.resentin.ui.networksettings.NetworkSettingsViewModel
import pm.antani.resentin.ui.sharetarget.ShareTargetScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_CHAT = "chat/{networkSlug}/{channelName}"
private const val ROUTE_MEMBERS = "members/{networkSlug}/{channelName}"
private const val ROUTE_NETWORK_SETTINGS = "networksettings/{networkSlug}"
private const val ROUTE_CHANNEL_SETTINGS = "channelsettings/{networkSlug}/{channelName}"
private const val ROUTE_APP_SETTINGS = "appsettings"
private const val ROUTE_SHARE_TARGET = "share-target"

private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")

data class DeepLinkChat(val networkSlug: String, val channelName: String)

@Composable
fun AppRoot(
    container: AppContainer,
    deepLink: DeepLinkChat? = null,
    onDeepLinkConsumed: () -> Unit = {},
    sharePick: Boolean = false,
    onSharePickConsumed: () -> Unit = {},
) {
    val session by container.tokenStore.session.collectAsState()

    if (session == null) {
        val viewModel: LoginViewModel = viewModel(
            factory = LoginViewModel.factory(container.authRepository, BuildConfig.DEFAULT_SERVER_HOST),
        )
        LoginScreen(viewModel = viewModel)
        return
    }

    val currentSession = session!!
    val navController = rememberNavController()
    val appContext = LocalContext.current.applicationContext

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            navController.navigate("chat/${deepLink.networkSlug}/${encode(deepLink.channelName)}")
            onDeepLinkConsumed()
        }
    }

    // The share-target URIs themselves already live in container.pendingShareHolder
    // (MainActivity staged them before this fired) — this just navigates to the picker
    // that lets the user say which chat they're for.
    LaunchedEffect(sharePick) {
        if (sharePick) {
            navController.navigate(ROUTE_SHARE_TARGET)
            onSharePickConsumed()
        }
    }

    val onOpenQuery: (networkSlug: String, nick: String) -> Unit = { networkSlug, nick ->
        navController.navigate("chat/$networkSlug/${encode(nick)}")
    }

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(container.networksRepository),
            )
            HomeScreen(
                viewModel = viewModel,
                host = currentSession.host,
                onSignOut = { container.authRepository.signOut() },
                onChannelClick = { networkSlug, channelName ->
                    navController.navigate("chat/$networkSlug/${encode(channelName)}")
                },
                onNetworkSettingsClick = { networkSlug ->
                    navController.navigate("networksettings/$networkSlug")
                },
                onAppSettingsClick = { navController.navigate(ROUTE_APP_SETTINGS) },
            )
        }
        composable(ROUTE_APP_SETTINGS) {
            val viewModel: AppSettingsViewModel = viewModel(
                factory = AppSettingsViewModel.factory(container.userSettingsRepository, container.appPreferences),
            )
            AppSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SHARE_TARGET) {
            val viewModel: HomeViewModel = viewModel(
                key = ROUTE_SHARE_TARGET,
                factory = HomeViewModel.factory(container.networksRepository),
            )
            ShareTargetScreen(
                viewModel = viewModel,
                onChatSelected = { networkSlug, channelName ->
                    navController.navigate("chat/$networkSlug/${encode(channelName)}") {
                        popUpTo(ROUTE_HOME)
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_CHAT,
            arguments = listOf(
                navArgument("networkSlug") { type = NavType.StringType },
                navArgument("channelName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val networkSlug = backStackEntry.arguments?.getString("networkSlug").orEmpty()
            val channelName = decode(backStackEntry.arguments?.getString("channelName").orEmpty())
            val viewModel: ChatViewModel = viewModel(
                key = "$networkSlug/$channelName",
                factory = ChatViewModel.factory(
                    container.chatRepository,
                    container.networksRepository,
                    container.membersRepository,
                    container.appPreferences,
                    container.connectionManager,
                    container.openChatTracker,
                    container.pendingShareHolder,
                    appContext,
                    networkSlug,
                    channelName,
                    currentSession.username,
                ),
            )
            ChatScreen(
                viewModel = viewModel,
                title = if (channelName == "\$server") "Server" else channelName,
                networkSlug = networkSlug,
                viewerUsername = currentSession.username,
                isQuery = isQueryTarget(channelName),
                onBack = { navController.popBackStack() },
                onMembersClick = {
                    navController.navigate("members/$networkSlug/${encode(channelName)}")
                },
                onSettingsClick = {
                    navController.navigate("channelsettings/$networkSlug/${encode(channelName)}")
                },
                onOpenQuery = onOpenQuery,
            )
        }
        composable(
            ROUTE_MEMBERS,
            arguments = listOf(
                navArgument("networkSlug") { type = NavType.StringType },
                navArgument("channelName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val networkSlug = backStackEntry.arguments?.getString("networkSlug").orEmpty()
            val channelName = decode(backStackEntry.arguments?.getString("channelName").orEmpty())
            val viewModel: MembersViewModel = viewModel(
                key = "members/$networkSlug/$channelName",
                factory = MembersViewModel.factory(
                    container.membersRepository,
                    container.networksRepository,
                    networkSlug,
                    channelName,
                    currentSession.username,
                ),
            )
            MemberListScreen(
                viewModel = viewModel,
                title = channelName,
                networkSlug = networkSlug,
                viewerUsername = currentSession.username,
                onBack = { navController.popBackStack() },
                onOpenQuery = onOpenQuery,
            )
        }
        composable(
            ROUTE_NETWORK_SETTINGS,
            arguments = listOf(navArgument("networkSlug") { type = NavType.StringType }),
        ) { backStackEntry ->
            val networkSlug = backStackEntry.arguments?.getString("networkSlug").orEmpty()
            val viewModel: NetworkSettingsViewModel = viewModel(
                key = "networksettings/$networkSlug",
                factory = NetworkSettingsViewModel.factory(container.networksRepository, networkSlug),
            )
            NetworkSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(
            ROUTE_CHANNEL_SETTINGS,
            arguments = listOf(
                navArgument("networkSlug") { type = NavType.StringType },
                navArgument("channelName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val networkSlug = backStackEntry.arguments?.getString("networkSlug").orEmpty()
            val channelName = decode(backStackEntry.arguments?.getString("channelName").orEmpty())
            val viewModel: ChannelSettingsViewModel = viewModel(
                key = "channelsettings/$networkSlug/$channelName",
                factory = ChannelSettingsViewModel.factory(container.networksRepository, networkSlug, channelName),
            )
            ChannelSettingsScreen(
                viewModel = viewModel,
                title = channelName,
                onBack = { navController.popBackStack() },
                onParted = {
                    navController.popBackStack(ROUTE_HOME, inclusive = false)
                },
            )
        }
    }
}
