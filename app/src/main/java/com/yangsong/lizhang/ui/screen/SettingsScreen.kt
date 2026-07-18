package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.theme.LiZhangSpacing
@Composable fun SettingsScreen(onNavigate:(AppDestination)->Unit){
    Scaffold(topBar={AppTopBar(stringResource(R.string.nav_settings))},bottomBar={BottomNavBar(AppDestination.Settings,onNavigate)}){p->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal=LiZhangSpacing.md)){
            item{SectionHeader(stringResource(R.string.settings_data))}
            item{SettingsRow(Icons.Outlined.Backup,stringResource(R.string.settings_backup),onClick={});SettingsRow(Icons.Outlined.TableView,stringResource(R.string.settings_excel),onClick={});SettingsRow(Icons.Outlined.Description,stringResource(R.string.settings_csv),onClick={});HorizontalDivider()}
            item{SectionHeader(stringResource(R.string.settings_display))}
            item{SettingsRow(Icons.Outlined.Palette,stringResource(R.string.settings_theme),stringResource(R.string.settings_follow_system),onClick={});SettingsRow(Icons.Outlined.DarkMode,stringResource(R.string.settings_dark),stringResource(R.string.settings_follow_system),onClick={},trailing={Switch(false,{})});SettingsRow(Icons.Outlined.TextFields,stringResource(R.string.settings_font),onClick={});HorizontalDivider()}
            item{SectionHeader(stringResource(R.string.settings_about_group))}
            item{SettingsRow(Icons.Outlined.Info,stringResource(R.string.settings_about),stringResource(R.string.settings_version),onClick={});SettingsRow(Icons.Outlined.PrivacyTip,stringResource(R.string.settings_privacy),onClick={});Spacer(Modifier.height(LiZhangSpacing.xl))}
        }
    }
}
