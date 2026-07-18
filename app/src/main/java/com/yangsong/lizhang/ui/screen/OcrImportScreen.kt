package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.theme.LiZhangSpacing
@Composable fun OcrImportScreen(onBack:()->Unit){Scaffold(topBar={AppTopBar(stringResource(R.string.nav_ocr),onBack)}){p->Column(Modifier.fillMaxSize().padding(p).padding(LiZhangSpacing.lg),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(LiZhangSpacing.lg)){Icon(Icons.Outlined.DocumentScanner,null,Modifier.size(64.dp),tint=MaterialTheme.colorScheme.primary);Text(stringResource(R.string.ocr_intro_title),style=MaterialTheme.typography.headlineSmall);Text(stringResource(R.string.ocr_intro_desc),color=MaterialTheme.colorScheme.onSurfaceVariant);Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Row(Modifier.fillMaxWidth().padding(LiZhangSpacing.md)){Icon(Icons.Outlined.VerifiedUser,null);Spacer(Modifier.width(LiZhangSpacing.sm));Text(stringResource(R.string.ocr_pending_notice))}};Spacer(Modifier.weight(1f));PrimaryButton(stringResource(R.string.ocr_camera),{},Modifier.fillMaxWidth(),icon=Icons.Outlined.PhotoCamera);SecondaryButton(stringResource(R.string.ocr_album),{},Modifier.fillMaxWidth(),icon=Icons.Outlined.PhotoLibrary);Text(stringResource(R.string.feature_developing),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
