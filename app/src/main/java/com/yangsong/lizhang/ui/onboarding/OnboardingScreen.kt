package com.yangsong.lizhang.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.onboarding.OnboardingMode
import com.yangsong.lizhang.ui.component.*
import kotlinx.coroutines.launch

/** 首次启动与手动查看共享三页视图，完成后的去向由入口决定。 */
@Composable
fun OnboardingScreen(mode: OnboardingMode, onFinish: () -> Unit, onBack: () -> Unit = {}, initialPage: Int = 0) {
    val pager = rememberPagerState(initialPage = initialPage) { 3 }
    val scope = rememberCoroutineScope()
    BackHandler(pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
    AppScaffold(
        topBar = {
            AppTopBar(stringResource(R.string.settings_onboarding),
                onBack = if (mode == OnboardingMode.REVIEW && pager.currentPage == 0) onBack else null,
                action = {
                    if (pager.currentPage < 2) GlassTextButton(onFinish, Modifier.testTag("引导跳过")) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val pageDescription = stringResource(R.string.onboarding_page_indicator, pager.currentPage + 1, 3)
                Row(Modifier.testTag("引导页码").semantics { contentDescription = pageDescription },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { page ->
                        Box(Modifier.size(8.dp).background(
                            if (page == pager.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .25f), CircleShape))
                    }
                }
                GlassButton(
                    onClick = {
                        if (pager.currentPage == 2) onFinish()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth().testTag(if (pager.currentPage == 2) "引导完成" else "引导下一步"),
                    enabled = !pager.isScrollInProgress,
                ) {
                    Text(stringResource(if (pager.currentPage < 2) R.string.onboarding_next
                        else if (mode == OnboardingMode.REVIEW) R.string.onboarding_done else R.string.onboarding_start))
                }
            }
        },
    ) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(padding).testTag("引导页面容器")) { page ->
            OnboardingPage(page)
        }
    }
}

@Composable
private fun OnboardingPage(page: Int) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)
        .testTag("引导页面${page + 1}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
        PageIllustration(when (page) {
            0 -> R.drawable.launcher_cat
            1 -> R.drawable.page_add_cat
            else -> R.drawable.page_settings_cat
        }, Modifier.fillMaxWidth().height(if (page == 1) 100.dp else 160.dp))
        Text(stringResource(when (page) {
            0 -> R.string.onboarding_intro_title
            1 -> R.string.onboarding_features_title
            else -> R.string.onboarding_privacy_title
        }), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (page == 1) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    FeatureItem(LiZhangIcons.NotebookPen, R.string.onboarding_record_title, R.string.onboarding_record_body)
                    FeatureItem(LiZhangIcons.UsersRound, R.string.onboarding_contacts_title, R.string.onboarding_contacts_body)
                    FeatureItem(LiZhangIcons.Bell, R.string.onboarding_reminders_title, R.string.onboarding_reminders_body)
                }
            }
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(if (page == 0) R.string.onboarding_intro_body else R.string.onboarding_privacy_body),
                    Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun FeatureItem(icon: ImageVector, title: Int, body: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(28.dp), tint = featureIconColor())
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
