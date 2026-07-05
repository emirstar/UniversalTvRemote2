package com.batin.tvremote.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.batin.tvremote.data.model.ConnectionState
import com.batin.tvremote.ui.RootViewModel
import com.batin.tvremote.ui.discovery.DiscoveryScreen
import com.batin.tvremote.ui.pairing.PairingScreen
import com.batin.tvremote.ui.remote.RemoteScreen

@Composable
fun TvRemoteNavHost(navController: NavHostController = rememberNavController()) {
    val rootViewModel: RootViewModel = hiltViewModel()
    val state by rootViewModel.connectionState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val targetRoute = when (state) {
            is ConnectionState.AwaitingPairingCode, is ConnectionState.Pairing -> Destinations.PAIRING
            is ConnectionState.Connecting, is ConnectionState.Connected -> Destinations.REMOTE
            is ConnectionState.Idle, is ConnectionState.Discovering -> Destinations.DISCOVERY
            is ConnectionState.Error -> null // the visible screen shows this inline instead of navigating away
        }
        if (targetRoute != null && navController.currentDestination?.route != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.startDestinationId) { inclusive = targetRoute == Destinations.DISCOVERY }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Destinations.DISCOVERY) {
        composable(Destinations.DISCOVERY) { DiscoveryScreen() }
        composable(Destinations.PAIRING) { PairingScreen() }
        composable(Destinations.REMOTE) { RemoteScreen() }
    }
}
