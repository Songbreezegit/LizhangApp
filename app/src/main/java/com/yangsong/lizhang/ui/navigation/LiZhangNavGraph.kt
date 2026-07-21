package com.yangsong.lizhang.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yangsong.lizhang.core.common.NavigationConstants
import com.yangsong.lizhang.data.di.AppContainer
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.ui.screen.*
import com.yangsong.lizhang.ui.component.BottomNavBar
import com.yangsong.lizhang.ui.viewmodel.*

private val mainTabs = listOf(AppDestination.Home, AppDestination.Contacts, AppDestination.AddGift, AppDestination.Settings)

private fun NavHostController.open(destination: AppDestination) {
    navigate(destination.route) {
        if (destination in mainTabs) {
            popUpTo(AppDestination.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun LiZhangNavGraph(appContainer: AppContainer) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentMainTab = mainTabs.firstOrNull { it.route == backStackEntry?.destination?.route }
    val go: (AppDestination) -> Unit = { nav.open(it) }
    val openRecord: (Long) -> Unit = { nav.navigate(AppDestination.GiftRecordDetail.createRoute(it)) }
    Box(Modifier.fillMaxSize()) {
    NavHost(nav, AppDestination.Home.route) {
        composable(AppDestination.Home.route) {
            HomeScreen(viewModel(factory = HomeViewModel.factory(appContainer.contactRepository, appContainer.giftRecordRepository)), go, openRecord)
        }
        composable(AppDestination.Contacts.route) {
            ContactsScreen(
                viewModel(factory = ContactsViewModel.factory(appContainer.contactRepository)),
                { nav.navigate(AppDestination.ContactDetail.createRoute(it)) },
                { nav.navigate(AppDestination.ContactEditor.createRoute()) },
            )
        }
        composable(
            AppDestination.ContactDetail.route,
            arguments = listOf(navArgument(NavigationConstants.CONTACT_ID_ARGUMENT) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(NavigationConstants.CONTACT_ID_ARGUMENT) ?: return@composable
            ContactDetailScreen(
                viewModel(key = "contact-$id", factory = ContactDetailViewModel.factory(id, appContainer.contactRepository, appContainer.giftRecordRepository)),
                nav::popBackStack,
                { nav.navigate(AppDestination.ContactEditor.createRoute(id)) },
                { nav.open(AppDestination.ManualGift) },
                openRecord,
            )
        }
        composable(
            AppDestination.ContactEditor.route,
            arguments = listOf(navArgument(NavigationConstants.CONTACT_ID_ARGUMENT) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(NavigationConstants.CONTACT_ID_ARGUMENT) ?: return@composable
            ContactEditorScreen(
                viewModel(key = "contact-editor-$id", factory = ContactEditorViewModel.factory(id, appContainer.contactRepository)),
                nav::popBackStack,
            ) { nav.popBackStack(AppDestination.Contacts.route, false) }
        }
        composable(AppDestination.AddGift.route) {
            GiftEntryScreen(
                onManual = { nav.navigate(AppDestination.ManualGift.route) },
                onOcr = { nav.navigate(AppDestination.OcrImport.route) },
            )
        }
        composable(AppDestination.ManualGift.route) {
            AddGiftScreen(
                viewModel(factory = GiftEditorViewModel.factory(appContainer.giftRecordRepository, appContainer.contactRepository)),
                nav::popBackStack,
            )
        }
        composable(
            AppDestination.GiftRecordDetail.route,
            arguments = listOf(navArgument(NavigationConstants.RECORD_ID_ARGUMENT) { type = NavType.LongType }),
        ) { entry ->
            val recordId = entry.arguments?.getLong(NavigationConstants.RECORD_ID_ARGUMENT) ?: return@composable
            GiftRecordDetailScreen(
                viewModel(key = "gift-record-$recordId", factory = GiftRecordDetailViewModel.factory(recordId, appContainer.giftRecordRepository)),
                onBack = nav::popBackStack,
                onEdit = { nav.navigate(AppDestination.GiftRecordEditor.createRoute(it)) },
            )
        }
        composable(
            AppDestination.GiftRecordEditor.route,
            arguments = listOf(navArgument(NavigationConstants.RECORD_ID_ARGUMENT) { type = NavType.LongType }),
        ) { entry ->
            val recordId = entry.arguments?.getLong(NavigationConstants.RECORD_ID_ARGUMENT) ?: return@composable
            AddGiftScreen(
                viewModel(key = "gift-record-editor-$recordId", factory = GiftEditorViewModel.factory(appContainer.giftRecordRepository, appContainer.contactRepository, recordId)),
                onBack = nav::popBackStack,
            )
        }
        composable(AppDestination.ReceivedRecords.route) {
            DirectionRecordsScreen(
                viewModel(factory = DirectionRecordsViewModel.factory(GiftDirection.RECEIVED, appContainer.giftRecordRepository)),
                GiftDirection.RECEIVED,
                nav::popBackStack,
                openRecord,
            )
        }
        composable(AppDestination.GivenRecords.route) {
            DirectionRecordsScreen(
                viewModel(factory = DirectionRecordsViewModel.factory(GiftDirection.GIVEN, appContainer.giftRecordRepository)),
                GiftDirection.GIVEN,
                nav::popBackStack,
                openRecord,
            )
        }
        composable(AppDestination.Calendar.route) {
            CalendarScreen(viewModel(factory = CalendarViewModel.factory(appContainer.giftRecordRepository)), nav::popBackStack, openRecord)
        }
        composable(AppDestination.Notifications.route) {
            NotificationsScreen(
                viewModel(
                    factory = NotificationsViewModel.factory(
                        appContainer.giftRecordRepository,
                        appContainer.reminderRepository,
                    ),
                ),
                nav::popBackStack,
                openRecord,
            )
        }
        composable(AppDestination.Search.route) {
            SearchScreen(viewModel(factory = SearchViewModel.factory(appContainer.giftRecordRepository)), nav::popBackStack, openRecord)
        }
        composable(AppDestination.Statistics.route) {
            StatisticsScreen(viewModel(factory = StatisticsViewModel.factory(appContainer.giftRecordRepository)), nav::popBackStack)
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                viewModel(
                    factory = SettingsViewModel.factory(
                        appContainer.giftRecordRepository,
                        appContainer.backupRepository,
                    ),
                ),
            )
        }
        composable(AppDestination.OcrImport.route) {
            OcrImportScreen(
                viewModel(
                    factory = OcrImportViewModel.factory(
                        appContainer.ocrRecognitionRepository,
                        appContainer.ocrImportRepository,
                        appContainer.giftRecordRepository,
                    ),
                ),
                nav::popBackStack,
            )
        }
    }
    currentMainTab?.let { tab ->
        BottomNavBar(tab, go, Modifier.align(Alignment.BottomCenter))
    }
    }
}
