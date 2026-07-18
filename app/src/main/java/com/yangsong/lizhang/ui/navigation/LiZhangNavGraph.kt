package com.yangsong.lizhang.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yangsong.lizhang.core.common.NavigationConstants
import com.yangsong.lizhang.data.di.AppContainer
import com.yangsong.lizhang.ui.screen.ContactDetailScreen
import com.yangsong.lizhang.ui.screen.ContactsScreen
import com.yangsong.lizhang.ui.screen.HomeScreen
import com.yangsong.lizhang.ui.screen.PlaceholderScreen
import com.yangsong.lizhang.ui.viewmodel.ContactDetailViewModel
import com.yangsong.lizhang.ui.viewmodel.ContactsViewModel
import com.yangsong.lizhang.ui.viewmodel.HomeViewModel

@Composable
fun LiZhangNavGraph(appContainer: AppContainer) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppDestination.Home.route) {
        composable(AppDestination.Home.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(appContainer.contactRepository, appContainer.giftRecordRepository),
            )
            HomeScreen(
                viewModel = viewModel,
                onNavigate = { destination -> navController.navigate(destination.route) },
            )
        }
        composable(AppDestination.Contacts.route) {
            val viewModel: ContactsViewModel = viewModel(factory = ContactsViewModel.factory(appContainer.contactRepository))
            ContactsScreen(
                viewModel = viewModel,
                onContactClick = { contactId -> navController.navigate(AppDestination.ContactDetail.createRoute(contactId)) },
            )
        }
        composable(
            route = AppDestination.ContactDetail.route,
            arguments = listOf(navArgument(NavigationConstants.CONTACT_ID_ARGUMENT) { type = NavType.LongType }),
        ) { entry ->
            val contactId = entry.arguments?.getLong(NavigationConstants.CONTACT_ID_ARGUMENT) ?: return@composable
            val viewModel: ContactDetailViewModel = viewModel(
                key = "contact-$contactId",
                factory = ContactDetailViewModel.factory(
                    contactId,
                    appContainer.contactRepository,
                    appContainer.giftRecordRepository,
                ),
            )
            ContactDetailScreen(viewModel = viewModel, onBack = navController::popBackStack)
        }
        composable(AppDestination.AddGift.route) { PlaceholderScreen(AppDestination.AddGift, navController::popBackStack) }
        composable(AppDestination.Statistics.route) { PlaceholderScreen(AppDestination.Statistics, navController::popBackStack) }
        composable(AppDestination.Search.route) { PlaceholderScreen(AppDestination.Search, navController::popBackStack) }
        composable(AppDestination.Settings.route) { PlaceholderScreen(AppDestination.Settings, navController::popBackStack) }
    }
}
