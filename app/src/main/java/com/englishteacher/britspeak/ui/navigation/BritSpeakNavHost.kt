package com.englishteacher.britspeak.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.englishteacher.britspeak.ui.chat.ChatScreen
import com.englishteacher.britspeak.ui.history.HistoryScreen
import com.englishteacher.britspeak.ui.settings.SettingsScreen
import com.englishteacher.britspeak.ui.topic.TopicScreen

private object Routes {
    const val PRACTICE = "practice"
    const val PRACTICE_PATTERN = "practice?topicId={topicId}&sessionId={sessionId}"
    const val TOPICS = "topics"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs =
    listOf(
        Tab(Routes.PRACTICE, "Practice", Icons.Filled.Chat),
        Tab(Routes.TOPICS, "Topics", Icons.Filled.ViewList),
        Tab(Routes.HISTORY, "History", Icons.Filled.History),
        Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
    )

@Composable
fun BritSpeakNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentRoute?.startsWith(tab.route) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.PRACTICE_PATTERN,
            modifier = Modifier.padding(padding),
        ) {
            composable(
                route = Routes.PRACTICE_PATTERN,
                arguments =
                    listOf(
                        navArgument("topicId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("sessionId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
            ) { entry ->
                ChatScreen(
                    topicId = entry.arguments?.getString("topicId"),
                    sessionId = entry.arguments?.getString("sessionId"),
                )
            }

            composable(Routes.TOPICS) {
                TopicScreen(
                    onTopicSelected = { topicId ->
                        navController.navigate("${Routes.PRACTICE}?topicId=$topicId") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpen = { sessionId ->
                        navController.navigate("${Routes.PRACTICE}?sessionId=$sessionId") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
