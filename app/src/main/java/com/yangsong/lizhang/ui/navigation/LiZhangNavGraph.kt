package com.yangsong.lizhang.ui.navigation
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.yangsong.lizhang.core.common.NavigationConstants
import com.yangsong.lizhang.data.di.AppContainer
import com.yangsong.lizhang.ui.screen.*
import com.yangsong.lizhang.ui.viewmodel.*
private val mainTabs=listOf(AppDestination.Home,AppDestination.Contacts,AppDestination.AddGift,AppDestination.Settings)
private fun NavHostController.open(destination:AppDestination){navigate(destination.route){if(destination in mainTabs){popUpTo(AppDestination.Home.route){saveState=true};launchSingleTop=true;restoreState=true}}}
@Composable fun LiZhangNavGraph(appContainer:AppContainer){val nav=rememberNavController();val go:(AppDestination)->Unit={nav.open(it)};NavHost(nav,AppDestination.Home.route){
 composable(AppDestination.Home.route){HomeScreen(viewModel(factory=HomeViewModel.factory(appContainer.contactRepository,appContainer.giftRecordRepository)),go)}
 composable(AppDestination.Contacts.route){ContactsScreen(viewModel(factory=ContactsViewModel.factory(appContainer.contactRepository)),{nav.navigate(AppDestination.ContactDetail.createRoute(it))},{nav.navigate(AppDestination.ContactEditor.createRoute())},go)}
 composable(AppDestination.ContactDetail.route,arguments=listOf(navArgument(NavigationConstants.CONTACT_ID_ARGUMENT){type=NavType.LongType})){entry->val id=entry.arguments?.getLong(NavigationConstants.CONTACT_ID_ARGUMENT)?:return@composable;ContactDetailScreen(viewModel(key="contact-$id",factory=ContactDetailViewModel.factory(id,appContainer.contactRepository,appContainer.giftRecordRepository)),nav::popBackStack,{nav.navigate(AppDestination.ContactEditor.createRoute(id))}){nav.open(AppDestination.AddGift)}}
 composable(AppDestination.ContactEditor.route,arguments=listOf(navArgument(NavigationConstants.CONTACT_ID_ARGUMENT){type=NavType.LongType})){entry->val id=entry.arguments?.getLong(NavigationConstants.CONTACT_ID_ARGUMENT)?:return@composable;ContactEditorScreen(viewModel(key="contact-editor-$id",factory=ContactEditorViewModel.factory(id,appContainer.contactRepository)),nav::popBackStack){nav.popBackStack(AppDestination.Contacts.route,false)}}
 composable(AppDestination.AddGift.route){AddGiftScreen(viewModel(factory=GiftEditorViewModel.factory(appContainer.giftRecordRepository,appContainer.contactRepository)),nav::popBackStack,go)}
 composable(AppDestination.Search.route){SearchScreen(viewModel(factory=SearchViewModel.factory(appContainer.giftRecordRepository)),nav::popBackStack)}
 composable(AppDestination.Statistics.route){StatisticsScreen(viewModel(factory=StatisticsViewModel.factory(appContainer.giftRecordRepository)),nav::popBackStack)}
 composable(AppDestination.Settings.route){SettingsScreen(go)}
 composable(AppDestination.OcrImport.route){OcrImportScreen(viewModel(factory=OcrImportViewModel.factory()),nav::popBackStack)}
}}
