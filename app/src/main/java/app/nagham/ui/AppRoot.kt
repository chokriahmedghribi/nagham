@file:OptIn(ExperimentalMaterial3Api::class)

package app.nagham.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.nagham.AppContainer

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "الرئيسية", Icons.Filled.Home),
    Tab("search", "بحث", Icons.Filled.Search),
    Tab("library", "مكتبتي", Icons.Filled.LibraryMusic),
    Tab("wrapped", "ملخصي", Icons.Filled.AutoAwesome),
)

@Composable
fun AppRoot(container: AppContainer) {
    val nav = rememberNavController()
    val favIds by container.repo.favoriteIds().collectAsState(initial = emptyList())
    val favSet = remember(favIds) { favIds.toSet() }
    val dlIds by container.repo.downloadedIds().collectAsState(initial = emptyList())
    val dlSet = remember(dlIds) { dlIds.toSet() }
    val dlError by container.downloads.error.collectAsState()
    val spMsg by container.spotify.message.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(spMsg) {
        spMsg?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            container.spotify.clearMessage()
        }
    }
    androidx.compose.runtime.LaunchedEffect(dlError) {
        dlError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            container.downloads.clearError()
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val fullScreen = route == "player" || route == "audio" || route == "sources"

    CompositionLocalProvider(LocalApp provides container, LocalFavorites provides favSet, LocalDownloaded provides dlSet) {
        Scaffold(
            bottomBar = {
                if (route != "player") {
                    Column {
                        if (route != "audio" && route != "sources") SpotifyBar()
                        if (route != "audio" && route != "sources") MiniPlayer(onOpen = { nav.navigate("player") })
                        if (!fullScreen) {
                            NavigationBar {
                                tabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = route == tab.route,
                                        onClick = {
                                            nav.navigate(tab.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(tab.icon, tab.label) },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) { pad ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
                composable("home") { HomeScreen(onAudio = { nav.navigate("audio") }, onSources = { nav.navigate("sources") }) }
                composable("sources") { SourcesScreen(onBack = { nav.popBackStack() }) }
                composable("search") { SearchScreen() }
                composable("library") { LibraryScreen(onOpenPlaylist = { nav.navigate("playlist/$it") }) }
                composable(
                    "playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { PlaylistScreen(it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }) }
                composable("wrapped") { WrappedScreen() }
                composable("audio") { AudioScreen(onBack = { nav.popBackStack() }) }
                composable("player") {
                    NowPlayingScreen(onBack = { nav.popBackStack() }, onAudio = { nav.navigate("audio") })
                }
            }
        }
    }
}
