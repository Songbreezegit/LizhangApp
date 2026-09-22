# UI 重构 V1 实机返修

> 本文为历史阶段记录；当前应用已升级至 **0.6.0 / versionCode 6**，见 [保存体验说明](记一笔保存体验0.6.0.md)。

## 参考与范围

已实际查看 `D:\code\Lizhang\参考图.png`，仅参考低饱和蓝橘渐变、通透层级与留白，没有复制参考图的文字或业务内容。继续在 `feat/ui-refresh-v1` 上工作，返修起点为 `38e9dfdfaff4a15230e71d8f69794aedb7bd4f8e`，开始前已拉取并核对远端一致。

不修改业务层、数据库、提醒调度、备份格式、通讯录规则和导航结构。不改变字体或透明猫咪 PNG。版本文件没有纳入返修；开始时本地已有用户未提交的 `0.4.0 / versionCode 4` 修改，予以保留，当时分支已提交版本为 `0.3.0 / 3`。

## 根因与处理

- `AppTextField`、`AmountTextField`、`AppMultilineTextField` 的 `visibleTextFieldColors` 将输入区域填成不透明的 `colorScheme.background`；记一笔 `SelectionRow` 也使用该实色，嵌在外部 GlassCard 中形成第二层实体表单。现改用独立半透明字段，去掉整张外层表单卡。
- `GlassCard` 原本在 `.glassFrame()` 后再套 Material `Card`，`GlassSurface` 也再套 `Surface`。现在普通内容容器只绘制一层玻璃背景，内容使用透明 Column / Box。
- 渲染检查发现普通卡片的 `Modifier.shadow` 内部阴影透过半透明填充呈灰色。普通内容卡取消该阴影，使用高光细边框与留白；已验收的浮动导航保留原阴影。
- 所有页面原先的 `Scaffold` 默认铺实色背景，`AppTopBar` 也铺背景色，无法呈现连续渐变。统一换为 `AppScaffold`，设置透明容器与明确的主题正文色。
- 首页年份按钮接近不透明白色；日历未选中日期、详情筛选项和联系人选择项使用实体 `surface`；导入底栏使用默认 Surface。分别改为玻璃或透明布局。
- 提醒的 `ReminderSettingRow`、联系人 `ContactListItem`、设置 `SettingsRow` 本身是透明行，不对它们虚构额外白底来源；这些区域的外层重复绘制和灰底随共用容器修复。设置行增加轻分隔线。

## 统一背景与玻璃参数

`ui/component/AppGradientBackground.kt` 提供 `AppGradientBackground` 与 `AppScaffold`，所有 12 个页面文件复用同一背景。渐变按屏幕高度绘制，不随列表项或滚动位置重新开始。

| 层级 | 浅色 | 深色 |
| --- | --- | --- |
| 页面渐变 | `#DDF3FC → #FFF7EC → #FFDCC3` | `#1B2936 → #26282B → #372A25` |
| 普通玻璃透明度 | 0.42 → 0.36 | 0.62 → 0.56 |
| 普通卡片边框 | 1dp 白色 0.65 | 1dp 白色 0.12 |
| 浮动导航 / 联系人菜单 | 保留 0.97 浮层参数与原布局 | 同左 |
| 弹窗 / 选择底部弹层 | 0.94，保证背景内容不干扰文字 | 同左 |

不引入实时 blur、背景截图或新依赖。正文保持实色，收到使用橘色，送出使用蓝色，净往来摘要为中性色。旧颜色常量名保留引用兼容，实际值收敛为蓝橘色系；深色强调色同样采用低饱和蓝橘。

## 页面结果

- 首页：删除 FAB 和对应额外底部留白，保留导航所需 124dp 空间；Header 直接展示在渐变上。年度卡单层绘制，年份按钮半透明；四个快捷入口统一玻璃容器与蓝橘图标；最近往来透明行加轻分隔线，头像仅蓝橘淡色。
- 提醒：系统提醒与提醒设置各为单层卡片，内部透明行；日期单选弹窗保留原四种天数及回调。
- 联系人：共用渐变，通透搜索与筛选、单层联系人卡。添加按钮、纵向菜单方向、动作数量、外部点击 / Back / 再次点击关闭和管理模式行为均保留。
- 记一笔：Section + 玻璃字段，联系人、金额、日期、备注不再嵌入整块白表单。方向选择为橘 / 蓝，事件选择中性玻璃，保存按钮为浅橘玻璃。
- 我的：单层设置组，透明设置行与细分隔线，数据类蓝色图标、设置类橘色图标，Switch 随主题。
- 其他页面：联系人详情、编辑、导入、搜索、统计、礼金详情、往来与日历、说明页面全部复用背景；相关筛选/选择项清理实体内层。

## 验证

2026-09-22 最终验收结果：

| 检查 | 结果 |
| --- | --- |
| test | Debug / Release 合计 152 项，0 失败、0 错误 |
| lint | 0 错误、17 项既有警告 |
| assembleDebug / assembleRelease | 均通过；Release 沿用未签名配置 |
| assembleDebugAndroidTest | 通过 |
| updateDebugScreenshotTest / validateDebugScreenshotTest | 45 项通过，0 失败 |
| API 36 设备测试 | 21 项全部通过 |
| 设备目视检查 | 16 张深浅主题截图，包含表单和设置滚动区域 |

设备为 Android 16 / API 36 模拟器，1080×2400、420dpi（约 412dp 宽），并非用户的实体手机。已核对首页无 FAB、连续蓝橘渐变、蓝橘快捷入口、提醒与联系人卡片无内层白块、原菜单展开和关闭、表单及设置透明内容；不将模拟器结果表述为用户实机验收通过。

构建沿用当前工作区中用户已有的版本配置；本次提交没有包含版本改动。验证日志位于忽略目录 `build/ui-repair-validation.txt` 与 `build/ui-repair-device.txt`，设备截图位于 `build/ui-repair-review/`。

执行命令：

```powershell
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:\AndroidProjects\LizhangApp\build'
.\gradlew.bat test lint assembleDebug assembleRelease assembleDebugAndroidTest updateDebugScreenshotTest validateDebugScreenshotTest --console=plain
```

设备测试包含 `GradientUiDeviceTest`、`GlassUiInstrumentedTest`、`ContactImportFlowInstrumentedTest`、`ContactBulkManagementFlowInstrumentedTest`、`CatAssetTransparencyTest`。

截图矩阵增加记一笔和我的页面，覆盖首页、联系人、展开菜单、管理模式、提醒、提醒日期弹窗、记一笔和我的；配置为浅色 360dp、深色 390dp、浅色 412dp / 1.5 倍字体、深色 360dp / 1.5 倍字体。保留并更新原页面基线。

`GradientUiDeviceTest` 使用 API 36 实际渲染：在不同背景下采样卡片内容区，确认能够透色；深浅主题分别渲染首页、联系人、记一笔、提醒、我的以及实际点击展开的联系人菜单，检查首页 FAB 不存在，并保存仅含虚构数据的设备截图供目视核对。已有菜单、导入、批量管理和 PNG 透明度测试继续运行。

对比度测试扩展到渐变三个位置以及各自玻璃合成色，正文、金额和危险操作均按 4.5:1 检查。设备截图、APK、构建日志及本地配置均不提交；只提交正式截图基线。
