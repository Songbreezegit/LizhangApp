package com.yangsong.lizhang.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 礼账统一线性图标：采用 Lucide 官方轮廓、圆角端点和 1.8dp 线宽。
 * 路径随应用保存在本地，不增加网络请求或运行时依赖；许可见 third_party/lucide。
 * 原有需要跟随布局方向的箭头继续自动镜像，其余图标保持原始语义。
 */
object LiZhangIcons {
    val DatabaseBackup: ImageVector by lazy {
        icon("database-backup", paths = listOf(
            IconPath("M3 5a9 3 0 1 0 18 0a9 3 0 1 0 -18 0Z"),
            IconPath("M3 12a9 3 0 0 0 5 2.69"),
            IconPath("M21 9.3V5"),
            IconPath("M3 5v14a9 3 0 0 0 6.47 2.88"),
            IconPath("M12 12v4h4"),
            IconPath("M13 20a5 5 0 0 0 9-3 4.5 4.5 0 0 0-4.5-4.5c-1.33 0-2.54.54-3.41 1.41L12 16"),
        ))
    }

    val Sheet: ImageVector by lazy {
        icon("sheet", paths = listOf(
            IconPath("M5 3H19a2 2 0 0 1 2 2V19a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2Z"),
            IconPath("M3 9L21 9"),
            IconPath("M3 15L21 15"),
            IconPath("M9 9L9 21"),
            IconPath("M15 9L15 21"),
        ))
    }

    val FileText: ImageVector by lazy {
        icon("file-text", paths = listOf(
            IconPath("M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z"),
            IconPath("M14 2v5a1 1 0 0 0 1 1h5"),
            IconPath("M10 9H8"),
            IconPath("M16 13H8"),
            IconPath("M16 17H8"),
        ))
    }

    val Globe: ImageVector by lazy {
        icon("globe", paths = listOf(
            IconPath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"),
            IconPath("M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"),
            IconPath("M2 12h20"),
        ))
    }

    val Palette: ImageVector by lazy {
        icon("palette", paths = listOf(
            IconPath("M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z"),
            IconPath("M13 6.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", filled = true),
            IconPath("M17 10.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", filled = true),
            IconPath("M6 12.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", filled = true),
            IconPath("M8 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", filled = true),
        ))
    }

    val Moon: ImageVector by lazy {
        icon("moon", paths = listOf(
            IconPath("M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401"),
        ))
    }

    val Type: ImageVector by lazy {
        icon("type", paths = listOf(
            IconPath("M12 4v16"),
            IconPath("M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2"),
            IconPath("M9 20h6"),
        ))
    }

    val Info: ImageVector by lazy {
        icon("info", paths = listOf(
            IconPath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"),
            IconPath("M12 16v-4"),
            IconPath("M12 8h.01"),
        ))
    }

    val ShieldCheck: ImageVector by lazy {
        icon("shield-check", paths = listOf(
            IconPath("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"),
            IconPath("m9 12 2 2 4-4"),
        ))
    }

    val CircleHelp: ImageVector by lazy {
        icon("circle-question-mark", paths = listOf(
            IconPath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"),
            IconPath("M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"),
            IconPath("M12 17h.01"),
        ))
    }

    val Trash: ImageVector by lazy {
        icon("trash", paths = listOf(
            IconPath("M10 11v6"),
            IconPath("M14 11v6"),
            IconPath("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"),
            IconPath("M3 6h18"),
            IconPath("M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"),
        ))
    }

    val Search: ImageVector by lazy {
        icon("search", paths = listOf(
            IconPath("m21 21-4.34-4.34"),
            IconPath("M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z"),
        ))
    }

    val UserSearch: ImageVector by lazy {
        icon("user-search", paths = listOf(
            IconPath("M6 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z"),
            IconPath("M10.3 15H7a4 4 0 0 0-4 4v2"),
            IconPath("M14 17a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
            IconPath("m21 21-1.9-1.9"),
        ))
    }

    val CalendarDays: ImageVector by lazy {
        icon("calendar-days", paths = listOf(
            IconPath("M8 2v3"),
            IconPath("M16 2v3"),
            IconPath("M5 3H19a2 2 0 0 1 2 2V19a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2Z"),
            IconPath("M3 9h18"),
            IconPath("M8 13h.01"),
            IconPath("M12 13h.01"),
            IconPath("M16 13h.01"),
            IconPath("M8 17h.01"),
            IconPath("M12 17h.01"),
            IconPath("M16 17h.01"),
        ))
    }

    val Check: ImageVector by lazy {
        icon("check", paths = listOf(
            IconPath("M20 6 9 17l-5-5"),
        ))
    }

    val ChevronRight: ImageVector by lazy {
        icon("chevron-right", paths = listOf(
            IconPath("m9 18 6-6-6-6"),
        ))
    }

    val ChevronLeft: ImageVector by lazy {
        icon("chevron-left", paths = listOf(
            IconPath("m15 18-6-6 6-6"),
        ))
    }

    val ChevronDown: ImageVector by lazy {
        icon("chevron-down", paths = listOf(
            IconPath("m6 9 6 6 6-6"),
        ))
    }

    val ChevronUp: ImageVector by lazy {
        icon("chevron-up", paths = listOf(
            IconPath("m18 15-6-6-6 6"),
        ))
    }

    val UserRoundPlus: ImageVector by lazy {
        icon("user-round-plus", paths = listOf(
            IconPath("M2 21a8 8 0 0 1 13.292-6"),
            IconPath("M5 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0Z"),
            IconPath("M19 16v6"),
            IconPath("M22 19h-6"),
        ))
    }

    val CircleUserRound: ImageVector by lazy {
        icon("circle-user-round", paths = listOf(
            IconPath("M17.925 20.056a6 6 0 0 0-11.851.001"),
            IconPath("M8 11a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z"),
            IconPath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"),
        ))
    }

    val Gift: ImageVector by lazy {
        icon("gift", paths = listOf(
            IconPath("M12 7v14"),
            IconPath("M20 11v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8"),
            IconPath("M7.5 7a1 1 0 0 1 0-5A4.8 8 0 0 1 12 7a4.8 8 0 0 1 4.5-5 1 1 0 0 1 0 5"),
            IconPath("M4 7H20a1 1 0 0 1 1 1V10a1 1 0 0 1 -1 1H4a1 1 0 0 1 -1 -1V8a1 1 0 0 1 1 -1Z"),
        ))
    }

    val MailCheck: ImageVector by lazy {
        icon("mail-check", paths = listOf(
            IconPath("M22 13V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v12c0 1.1.9 2 2 2h8"),
            IconPath("m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"),
            IconPath("m16 19 2 2 4-4"),
        ))
    }

    val ChartColumns: ImageVector by lazy {
        icon("chart-no-axes-column", paths = listOf(
            IconPath("M5 21v-6"),
            IconPath("M12 21V3"),
            IconPath("M19 21V9"),
        ))
    }

    val House: ImageVector by lazy {
        icon("house", paths = listOf(
            IconPath("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8"),
            IconPath("M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"),
        ))
    }

    val UsersRound: ImageVector by lazy {
        icon("users-round", paths = listOf(
            IconPath("M18 21a8 8 0 0 0-16 0"),
            IconPath("M5 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0Z"),
            IconPath("M22 20c0-3.37-2-6.5-4-8a5 5 0 0 0-.45-8.3"),
        ))
    }

    val NotebookPen: ImageVector by lazy {
        icon("notebook-pen", paths = listOf(
            IconPath("M13.4 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7.4"),
            IconPath("M2 6h4"),
            IconPath("M2 10h4"),
            IconPath("M2 14h4"),
            IconPath("M2 18h4"),
            IconPath("M21.378 5.626a1 1 0 1 0-3.004-3.004l-5.01 5.012a2 2 0 0 0-.506.854l-.837 2.87a.5.5 0 0 0 .62.62l2.87-.837a2 2 0 0 0 .854-.506z"),
        ))
    }

    val Pencil: ImageVector by lazy {
        icon("pencil", paths = listOf(
            IconPath("M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"),
            IconPath("m15 5 4 4"),
        ))
    }

    val ContactRound: ImageVector by lazy {
        icon("contact-round", paths = listOf(
            IconPath("M16 2v2"),
            IconPath("M17.915 21a6 6 0 10-12 0"),
            IconPath("M8 2v2"),
            IconPath("M8 11a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z"),
            IconPath("M5 3H19a2 2 0 0 1 2 2V19a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2Z"),
        ))
    }

    val Bell: ImageVector by lazy {
        icon("bell", paths = listOf(
            IconPath("M10.268 21a2 2 0 0 0 3.464 0"),
            IconPath("M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326"),
        ))
    }

    val Plus: ImageVector by lazy {
        icon("plus", paths = listOf(
            IconPath("M5 12h14"),
            IconPath("M12 5v14"),
        ))
    }

    val CircleAlert: ImageVector by lazy {
        icon("circle-alert", paths = listOf(
            IconPath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"),
            IconPath("M12 8L12 12"),
            IconPath("M12 16L12.01 16"),
        ))
    }

    val CircleCheck: ImageVector by lazy {
        icon("circle-check", paths = listOf(
            IconPath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"),
            IconPath("m16 9-5.5 5.5L8 12"),
        ))
    }

    val ArrowLeft: ImageVector by lazy {
        icon("arrow-left", autoMirror = true, paths = listOf(
            IconPath("m12 19-7-7 7-7"),
            IconPath("M19 12H5"),
        ))
    }

    val ArrowRight: ImageVector by lazy {
        icon("arrow-right", autoMirror = true, paths = listOf(
            IconPath("M5 12h14"),
            IconPath("m12 5 7 7-7 7"),
        ))
    }

    val ArrowDownLeft: ImageVector by lazy {
        icon("arrow-down-left", autoMirror = true, paths = listOf(
            IconPath("M17 7 7 17"),
            IconPath("M17 17H7V7"),
        ))
    }

    val ArrowUpRight: ImageVector by lazy {
        icon("arrow-up-right", autoMirror = true, paths = listOf(
            IconPath("M7 7h10v10"),
            IconPath("M7 17 17 7"),
        ))
    }

    val ExternalLink: ImageVector by lazy {
        icon("external-link", autoMirror = true, paths = listOf(
            IconPath("M15 3h6v6"),
            IconPath("M10 14 21 3"),
            IconPath("M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"),
        ))
    }

    val Heart: ImageVector by lazy {
        icon("heart", paths = listOf(
            IconPath("M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5"),
        ))
    }

    val Banknote: ImageVector by lazy {
        icon("banknote", paths = listOf(
            IconPath("M4 6H20a2 2 0 0 1 2 2V16a2 2 0 0 1 -2 2H4a2 2 0 0 1 -2 -2V8a2 2 0 0 1 2 -2Z"),
            IconPath("M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            IconPath("M6 12h.01M18 12h.01"),
        ))
    }

    val Close: ImageVector by lazy {
        icon("x", paths = listOf(
            IconPath("M18 6 6 18"),
            IconPath("m6 6 12 12"),
        ))
    }

    private data class IconPath(val data: String, val filled: Boolean = false)

    private fun icon(name: String, autoMirror: Boolean = false, paths: List<IconPath>): ImageVector =
        ImageVector.Builder(
            name = "Lucide.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror,
        ).apply {
            paths.forEach { path ->
                addPath(
                    pathData = PathParser().parsePathString(path.data).toNodes(),
                    fill = if (path.filled) SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
