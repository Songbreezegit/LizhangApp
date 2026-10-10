package com.yangsong.lizhang.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.yangsong.lizhang.ui.component.LocalAppearanceActions
import com.yangsong.lizhang.ui.component.AppearanceTransitionHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yangsong.lizhang.core.common.NavigationConstants
import com.yangsong.lizhang.R
import com.yangsong.lizhang.data.di.AppContainer
import com.yangsong.lizhang.data.onboarding.GuidePracticeStore
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.ui.screen.*
import com.yangsong.lizhang.ui.component.BottomNavBar
import com.yangsong.lizhang.ui.component.CenteredSnackbarHost
import com.yangsong.lizhang.ui.viewmodel.*
import com.yangsong.lizhang.ui.onboarding.OnboardingScreen
import com.yangsong.lizhang.domain.onboarding.OnboardingMode
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import com.yangsong.lizhang.ui.onboarding.FeatureGuideOverlay
import com.yangsong.lizhang.ui.onboarding.LocalActiveFeatureGuideTarget
import com.yangsong.lizhang.ui.onboarding.featureGuidePresentation
import com.yangsong.lizhang.ui.onboarding.recordGuideRoutes
import com.yangsong.lizhang.ui.onboarding.GuidePracticeScreen
import com.yangsong.lizhang.ui.onboarding.FeatureGuideTargetRegistry
import com.yangsong.lizhang.ui.onboarding.LocalFeatureGuideTargetRegistry

private val mainTabs = listOf(AppDestination.Home, AppDestination.Contacts, AppDestination.AddGift, AppDestination.Settings)
private const val CREATED_CONTACT_RESULT = "contact_created_id"

/** 返回转场开始即回填，恢复交互后消费；结果只属于原记账条目。 */
@Composable
private fun CreatedContactResultEffect(nav: NavHostController, entry: NavBackStackEntry, editor: GiftEditorViewModel) {
    val createdContactId by entry.savedStateHandle.getStateFlow<Long?>(CREATED_CONTACT_RESULT, null).collectAsStateWithLifecycle()
    val lifecycleState by entry.lifecycle.currentStateAsState()
    var appliedContactId by remember(entry) { mutableStateOf<Long?>(null) }
    LaunchedEffect(createdContactId, lifecycleState) {
        val id = createdContactId ?: return@LaunchedEffect
        if (id > 0 && nav.currentBackStackEntry === entry && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            // 页面在返回动画中也能展示正确的人，避免保存栏在转场期间仍保存原联系人。
            if (appliedContactId != id) {
                editor.selectCreatedContact(id)
                appliedContactId = id
            }
            if (lifecycleState == Lifecycle.State.RESUMED) entry.savedStateHandle[CREATED_CONTACT_RESULT] = null
        }
    }
}

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
fun LiZhangNavGraph(
    appContainer: AppContainer,
    reminderLaunchRequest: ReminderLaunchRequest? = null,
    onReminderRequestConsumed: () -> Unit = {},
    openRemindersRequest: Long? = null,
    onOpenRemindersConsumed: () -> Unit = {},
    onboardingViewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(appContainer.onboardingRepository)),
    guideEnabled: Boolean = true,
) {
    val nav = rememberNavController()
    val hazeState = rememberHazeState()
    val guideRegistry = remember { FeatureGuideTargetRegistry() }
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val reminderLaunchViewModel: ReminderLaunchViewModel = viewModel(
        factory = ReminderLaunchViewModel.factory(appContainer.giftRecordRepository),
    )
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var contactsSelectionMode by remember { mutableStateOf(false) }
    val unavailableMessage = stringResource(R.string.reminder_record_unavailable)
    val backStackEntry by nav.currentBackStackEntryAsState()
    val visibleEntries by nav.visibleEntries.collectAsStateWithLifecycle()
    val transition = LocalAppearanceActions.current as? AppearanceTransitionHost
    val navigationReady = backStackEntry?.lifecycle?.currentStateAsState()?.value == Lifecycle.State.RESUMED
    val guide = if (guideEnabled && navigationReady) featureGuidePresentation(onboardingState, backStackEntry?.destination?.route) else null
    // 导航条目恢复到可交互状态后才能消费语言交接，不按固定帧数估计。
    SideEffect { transition?.navigationReady(navigationReady) }
    val currentMainTab = mainTabs.firstOrNull { it.route == backStackEntry?.destination?.route }
    val layoutDirection = LocalLayoutDirection.current
    val pageEasing = CubicBezierEasing(.2f, .8f, .2f, 1f)
    fun motionDirection(from: String?, to: String?, returning: Boolean = false): Int {
        val before = mainTabs.indexOfFirst { it.route == from }
        val after = mainTabs.indexOfFirst { it.route == to }
        val forward = if (before >= 0 && after >= 0) after > before else !returning
        return (if (forward) 1 else -1) * (if (layoutDirection == LayoutDirection.Rtl) -1 else 1)
    }
    val go: (AppDestination) -> Unit = {
        nav.open(it)
    }
    LaunchedEffect(backStackEntry?.destination?.route, navigationReady, onboardingState.featureGuideStep) {
        if (guideEnabled && navigationReady && backStackEntry?.destination?.route in recordGuideRoutes &&
            onboardingState.featureGuideStep == FeatureGuideStep.ADD_RECORD) {
            onboardingViewModel.advanceFeatureGuide(FeatureGuideStep.ADD_RECORD)
        }
    }
    val openRecord: (Long) -> Unit = { nav.navigate(AppDestination.GiftRecordDetail.createRoute(it)) }
    LaunchedEffect(openRemindersRequest) {
        if (openRemindersRequest != null) {
            nav.navigate(AppDestination.Notifications.route) { launchSingleTop = true }
            onOpenRemindersConsumed()
        }
    }

    LaunchedEffect(reminderLaunchRequest?.requestKey) {
        reminderLaunchRequest?.let { request ->
            reminderLaunchViewModel.resolve(request.recordId)
            onReminderRequestConsumed()
        }
    }
    LaunchedEffect(reminderLaunchViewModel) {
        reminderLaunchViewModel.results.collect { result ->
            when (result) {
                is ReminderLaunchResult.OpenRecord -> nav.navigate(
                    AppDestination.GiftRecordDetail.createRoute(result.recordId),
                ) {
                    launchSingleTop = true
                }
                ReminderLaunchResult.RecordUnavailable -> {
                    nav.open(AppDestination.Home)
                    snackbar.showSnackbar(unavailableMessage)
                }
            }
        }
    }
    CompositionLocalProvider(LocalFeatureGuideTargetRegistry provides guideRegistry,
        LocalActiveFeatureGuideTarget provides guide?.target) {
    Box(Modifier.fillMaxSize()) {
    NavHost(
        nav, AppDestination.Home.route, Modifier.hazeSource(hazeState).testTag("业务导航页面"),
        enterTransition = {
            if (initialState.destination.route == targetState.destination.route) EnterTransition.None
            // 日历先亮出日期网格，短淡入避免密集日期控件参与整页滑动合成。
            else if (targetState.destination.route == AppDestination.Calendar.route) fadeIn(tween(120), initialAlpha = .75f)
            else {
                val direction = motionDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(tween(360, easing = pageEasing)) { it / 8 * direction } +
                    fadeIn(tween(240), initialAlpha = .45f)
            }
        },
        exitTransition = {
            if (initialState.destination.route == targetState.destination.route) ExitTransition.None
            else if (targetState.destination.route == AppDestination.Calendar.route) fadeOut(tween(120))
            else {
                val direction = motionDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(tween(260, easing = pageEasing)) { -it / 12 * direction } + fadeOut(tween(220))
            }
        },
        popEnterTransition = {
            if (targetState.destination.route == AppDestination.Calendar.route) fadeIn(tween(120), initialAlpha = .75f)
            else {
                val direction = motionDirection(initialState.destination.route, targetState.destination.route, returning = true)
                slideInHorizontally(tween(360, easing = pageEasing)) { it / 8 * direction } + fadeIn(tween(240), initialAlpha = .45f)
            }
        },
        popExitTransition = {
            if (targetState.destination.route == AppDestination.Calendar.route) fadeOut(tween(120))
            else {
                val direction = motionDirection(initialState.destination.route, targetState.destination.route, returning = true)
                slideOutHorizontally(tween(260, easing = pageEasing)) { -it / 12 * direction } + fadeOut(tween(220))
            }
        },
    ) {
        composable(AppDestination.Home.route) {
            HomeScreen(viewModel(factory = HomeViewModel.factory(appContainer.contactRepository, appContainer.giftRecordRepository)), go, openRecord)
        }
        composable(AppDestination.Contacts.route) {
            ContactsScreen(
                viewModel(factory = ContactsViewModel.factory(appContainer.contactRepository)),
                { nav.navigate(AppDestination.ContactDetail.createRoute(it)) },
                { nav.navigate(AppDestination.ContactEditor.createRoute()) },
                { nav.navigate(AppDestination.ContactImport.route) { launchSingleTop = true } },
                onSelectionModeChange = { contactsSelectionMode = it },
            )
        }
        composable(AppDestination.ContactImport.route) {
            ContactImportScreen(
                viewModel(factory = ContactImportViewModel.factory(appContainer.deviceContactRepository, appContainer.contactRepository)),
                onBack = { nav.popBackStack() },
                onImported = { result ->
                    nav.popBackStack()
                    scope.launch {
                        snackbar.showSnackbar(context.getString(
                            R.string.contact_import_summary, result.imported, result.skipped, result.possibleDuplicatesUnselected,
                        ))
                    }
                },
                onboardingViewModel = onboardingViewModel,
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
                { nav.navigate(AppDestination.AddGiftForContact.createRoute(id)) },
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
                onBack = { if (nav.currentBackStackEntry === entry) nav.popBackStack() },
                onSaved = { savedContactId ->
                    if (nav.currentBackStackEntry === entry) {
                        val caller = nav.previousBackStackEntry
                        if (id == NavigationConstants.NEW_CONTACT_ID && caller?.destination?.route in
                            recordGuideRoutes + AppDestination.GiftRecordEditor.route) {
                            caller?.savedStateHandle?.set(CREATED_CONTACT_RESULT, savedContactId)
                        }
                        nav.popBackStack()
                    }
                },
            ) { if (nav.currentBackStackEntry === entry) nav.popBackStack(AppDestination.Contacts.route, false) }
        }
        composable(AppDestination.AddGift.route) { entry ->
            val editor: GiftEditorViewModel = viewModel(factory = GiftEditorViewModel.factory(appContainer.giftRecordRepository, appContainer.contactRepository))
            CreatedContactResultEffect(nav, entry, editor)
            AddGiftScreen(
                editor,
                onBack = { if (nav.currentBackStackEntry === entry) nav.popBackStack() },
                onCreateContact = {
                    if (nav.currentBackStackEntry === entry && entry.lifecycle.currentState == Lifecycle.State.RESUMED)
                        nav.navigate(AppDestination.ContactEditor.createRoute())
                },
            )
        }
        composable(
            AppDestination.AddGiftForContact.route,
            arguments = listOf(navArgument(NavigationConstants.CONTACT_ID_ARGUMENT) { type = NavType.LongType }),
        ) { entry ->
            val contactId = entry.arguments?.getLong(NavigationConstants.CONTACT_ID_ARGUMENT) ?: return@composable
            val editor: GiftEditorViewModel = viewModel(
                    key = "gift-for-contact-$contactId",
                    factory = GiftEditorViewModel.factory(
                        appContainer.giftRecordRepository,
                        appContainer.contactRepository,
                        initialContactId = contactId,
                    ),
                )
            CreatedContactResultEffect(nav, entry, editor)
            AddGiftScreen(
                editor,
                onBack = { if (nav.currentBackStackEntry === entry) nav.popBackStack() },
                onCreateContact = {
                    if (nav.currentBackStackEntry === entry && entry.lifecycle.currentState == Lifecycle.State.RESUMED)
                        nav.navigate(AppDestination.ContactEditor.createRoute())
                },
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
            val editor: GiftEditorViewModel = viewModel(key = "gift-record-editor-$recordId", factory = GiftEditorViewModel.factory(appContainer.giftRecordRepository, appContainer.contactRepository, recordId))
            CreatedContactResultEffect(nav, entry, editor)
            AddGiftScreen(
                editor,
                onBack = { if (nav.currentBackStackEntry === entry) nav.popBackStack() },
                onCreateContact = {
                    if (nav.currentBackStackEntry === entry && entry.lifecycle.currentState == Lifecycle.State.RESUMED)
                        nav.navigate(AppDestination.ContactEditor.createRoute())
                },
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
                        appContainer.reminderRepository,
                    ),
                ),
                nav::popBackStack,
                onboardingViewModel = onboardingViewModel,
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
                        appContainer.themeRepository,
                    ),
                ),
                onFontGuide = { nav.navigate(AppDestination.FontGuide.route) },
                onAbout = { nav.navigate(AppDestination.About.route) },
                onPrivacy = { nav.navigate(AppDestination.Privacy.route) },
                onTerms = { nav.navigate(AppDestination.Terms.route) },
                onHelp = { nav.navigate(AppDestination.Help.route) },
            )
        }
        composable(AppDestination.Onboarding.route) {
            OnboardingScreen(OnboardingMode.REVIEW, onFinish = {
                onboardingViewModel.finish(OnboardingMode.REVIEW)
                nav.popBackStack()
            }, onBack = { nav.popBackStack() })
        }
        composable(AppDestination.GuidePractice.route) { entry ->
            // 练习 ViewModel 没有业务仓储依赖，示例只在此路由的内存中流转。
            val practice: GuidePracticeViewModel = viewModel(factory = GuidePracticeViewModel.factory(::GuidePracticeStore))
            var exiting by remember(entry) { mutableStateOf(false) }
            val finishPractice = {
                if (!exiting && nav.currentBackStackEntry === entry) {
                    exiting = true
                    practice.clear()
                    nav.popBackStack()
                }
                Unit
            }
            GuidePracticeScreen(practice, onExit = finishPractice, onComplete = finishPractice)
        }
        composable(AppDestination.FontGuide.route) {
            FontGuideScreen(nav::popBackStack)
        }
        composable(AppDestination.About.route) {
            AboutScreen(nav::popBackStack)
        }
        composable(AppDestination.Privacy.route) {
            LegalDocumentScreen(
                viewModel(factory = LegalDocumentViewModel.factory(appContainer.legalDocumentRepository, LegalDocumentType.PRIVACY)),
                nav::popBackStack,
            )
        }
        composable(AppDestination.Terms.route) {
            LegalDocumentScreen(
                viewModel(factory = LegalDocumentViewModel.factory(appContainer.legalDocumentRepository, LegalDocumentType.TERMS)),
                nav::popBackStack,
            )
        }
        composable(AppDestination.Help.route) {
            LegalDocumentScreen(
                viewModel(factory = LegalDocumentViewModel.factory(appContainer.legalDocumentRepository, LegalDocumentType.HELP)),
                nav::popBackStack,
            )
        }
    }
    // 普通页面退场继续保留浮钮位置，主标签切换仍使用既有跟随动效。
    var lastMainRoute by rememberSaveable { mutableStateOf(AppDestination.Home.route) }
    SideEffect { currentMainTab?.let { lastMainRoute = it.route } }
    val barTab = currentMainTab ?: mainTabs.first { it.route == lastMainRoute }
    // 导航图在返回转场中仍绘制原页面，不能只看已经改变的当前路由。
    // 进入记账时立即交出底部区域；返回时等所有记账条目真正退场后再恢复导航。
    val editorRoutes = recordGuideRoutes + AppDestination.GiftRecordEditor.route
    val editorVisible = backStackEntry?.destination?.route in editorRoutes ||
        visibleEntries.any { it.destination.route in editorRoutes }
    val showBottomBar = currentMainTab != null && currentMainTab != AppDestination.AddGift && !editorVisible &&
        !(currentMainTab == AppDestination.Contacts && contactsSelectionMode)
    AnimatedVisibility(
        visible = showBottomBar,
        modifier = Modifier.align(Alignment.BottomCenter).testTag("底部导航出入场"),
        enter = slideInVertically(tween(320, easing = pageEasing)) { it / 3 } + fadeIn(tween(240)),
        exit = slideOutVertically(tween(220, easing = pageEasing)) { it / 3 } + fadeOut(tween(180)),
    ) {
        // AnimatedVisibility 的退出动画会保留子树；记账页面出现时必须停止实际绘制与点击。
        if (!editorVisible) BottomNavBar(barTab, go, Modifier, hazeState, enabled = showBottomBar)
    }
    CenteredSnackbarHost(snackbar)
    guide?.let { currentGuide ->
        val finishCurrentGuide = {
            currentGuide.page?.let(onboardingViewModel::completePageGuide)
                ?: onboardingViewModel.skipFeatureGuide()
        }
        FeatureGuideOverlay(
            state = onboardingState,
            isHome = true,
            registry = guideRegistry,
            step = currentGuide.step,
            target = currentGuide.target,
            onNext = {
                if (currentGuide.page != null) onboardingViewModel.completePageGuide(currentGuide.page)
                else if (currentGuide.step == FeatureGuideStep.ADD_RECORD) {
                    onboardingViewModel.advanceFeatureGuide(FeatureGuideStep.ADD_RECORD)
                    nav.open(AppDestination.AddGift)
                } else onboardingViewModel.advanceFeatureGuide(currentGuide.step)
            },
            onSkip = finishCurrentGuide,
        )
    }
    }
    }
}
