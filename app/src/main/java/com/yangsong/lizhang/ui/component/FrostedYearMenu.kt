package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import dev.chrisbanes.haze.HazeState

/** 同窗的年份浮层；只提供菜单内容，不改变年份来源和统计业务。 */
@Composable
fun FrostedYearMenu(
    years: List<Int>,
    selectedYear: Int,
    hazeState: HazeState,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.home_choose_year)
    LazyColumn(
        modifier.width(168.dp).heightIn(max = 240.dp)
            .frostedGlassFrame(hazeState, RoundedCornerShape(GlassTokens.ControlRadius))
            .testTag("年份菜单").selectableGroup().semantics { paneTitle = title },
        state = rememberLazyListState(initialFirstVisibleItemIndex = years.indexOf(selectedYear).coerceAtLeast(0)),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        items(years, key = { it }) { year ->
            val selected = year == selectedYear
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .12f) else Color.Transparent)
                    .testTag("年份选项-$year")
                    .selectable(selected, role = Role.RadioButton, onClick = { onYearSelected(year) })
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                else Spacer(Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.year_format, year),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
