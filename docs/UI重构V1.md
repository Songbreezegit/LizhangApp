# 全局 UI 重构 V1

## 范围与版本

- 基于 `feat/contact-bulk-management` 的 `2765a9f8a188100b7ec5a0db34d40b9d7edfb043`。
- 工作分支：`feat/ui-refresh-v1`；版本：`0.3.0`，版本代码：`3`。
- 保留 `ui → ViewModel → domain.repository ← data` 分层。没有修改 Room、计算、去重、读取通讯录、批量删除事务、提醒调度、通知或备份格式。
- 本次附件未包含“图 4”，按文字要求延续奶油、珊瑚、绿色与猫咪元素。

## 组件和参数

`ui/component/GlassComponents.kt` 集中管理 `GlassTokens` 和组件：

| 项目 | 规则 |
| --- | --- |
| 背景 | 所有页面沿用主题背景，浅色奶油白、深色暖灰褐 |
| 基础玻璃 | 浅色透明度 0.76、深色 0.94，渐变高光差 0.04 |
| 浮层 | 透明度 0.97，保证背景文字不干扰操作标签 |
| 描边 | 1dp，主题前景色透明度 0.12 |
| 圆角 | 卡片 24dp，控件 20dp，导航与弹窗 30dp |
| 阴影 | 基础 1dp，FAB 6dp |
| 禁用 | 前景透明度 0.38，保留不可点击语义 |
| 按压 | 使用 Material 的交互源和水波纹；导航保留按压缩放 |
| 安全空间 | FAB 底部 132dp；主列表底部 216dp |

提供 `GlassSurface`、`GlassCard`、`GlassButton`、`GlassTextButton`、`GlassIconButton`、`GlassFab`、`GlassChip`、`GlassSearchBar`、`GlassDialog` 和 `GlassActionMenu`。原 `BottomNavBar` 保留名称，内部使用统一浮层参数。

不进行实时背景采样，不添加 blur、离屏截图、逐帧位图或新图形依赖。透明度与细描边模拟轻玻璃，浮动菜单额外使用浅主题遮罩确保文字可读。

## 页面与交互

- 首页：保留年度统计、猫咪、四个快捷入口、最近往来和记账入口。年度卡随内容增长，金额采用主题颜色；大字体快捷入口为两列。
- 导航：保留四个目的地及现有 `saveState`、`restoreState`、`launchSingleTop` 配置，选中项用浅强调色 pill。
- 联系人：装饰图高度缩至 88dp，移除导入大卡片，搜索框和筛选统一样式。
- 联系人条目：稳定 ID + `LazyColumn.items`，不再把整个数据集放进单个 `Column`。管理模式仍使用原脱敏与选择回调。
- 菜单：向上显示有图标和文字的两个动作，错峰淡入、上移和轻缩放；加号旋转为关闭符号。外部点击和 Back 只收起菜单，选择动作先收起再调用原导航。
- 管理：进入时关闭并隐藏菜单，保留取消、已选人数、全选、清空和原删除确认流程。
- 提醒：单个 `GlassDialog` 包含四个具有单选语义的透明行，保留 `0 / 1 / 3 / 7` 回调。
- 删除、放弃修改、备份及主题弹窗统一使用 `GlassDialog`，危险操作保留红色。

## 猫咪 PNG 资源

页面和启动图的六张猫咪素材均来自项目已有插画，经内置 imagegen 编辑去底。输出保存并替换到 `app/src/main/res/drawable-nodpi/`，不依赖工作区之外的资源。

统一提示约束：仅去除背景，保留奶油色猫咪、道具、构图和水彩质感；输出真实透明 Alpha PNG，白色毛发保持不透明，不增加文字、不绘制棋盘格、避免边缘白圈。首页保留右侧猫咪和左侧留白；启动图额外去除圆角底板。

`CatAssetTransparencyTest` 使用 Android 实际解码器检查六张素材的 Alpha 通道、大面积全透明背景和不透明主体；画布边角允许最多 1/255 的量化残留。页面通过 `illustrationPainter` 将解码位图最长边限制在 1024 像素以内；原始透明 PNG 保留，不在运行时处理抠图。

没有复制第三方开源玻璃或 Speed Dial 实现，没有引入商业 App 代码或新增依赖。Compose / Material 3 沿用现有 Apache 2.0 依赖；猫咪沿用项目资产并通过内置图像工具编辑。

## 验证

2026-09-22 最终构建验收：

| 检查 | 结果 |
| --- | --- |
| test | Debug / Release 合计 152 项，0 失败、0 错误 |
| lint | 0 错误、17 项警告（依赖版本、未使用资源及图标等既有提示） |
| assembleDebug / assembleRelease | 均通过；Release 沿用现有未签名配置 |
| assembleDebugAndroidTest | 通过 |
| updateDebugScreenshotTest / validateDebugScreenshotTest | 37 项通过，0 失败 |
| Android 16 / API 36 设备回归 | 19 项通过（交互 18 项、六张 PNG 透明度检查 1 项） |

系统通讯录授权对话框仍由 Android 提供，应用内权限说明和按钮使用统一组件。

截图矩阵覆盖首页、联系人、菜单展开、管理模式、提醒页面与提醒日期弹窗：360dp 浅色、390dp 深色、412dp 1.5 倍字体、360dp 深色 1.5 倍字体；同时更新原有 13 张页面和弹窗基线。截图全部为虚构数据。

设备交互覆盖菜单开合、外部点击、Back、动作分发、管理隐藏、1000 联系人首尾滚动、四种提醒天数、四个主导航、手动添加实际路由与返回、通讯录假数据导入、搜索状态恢复和原批量管理。

构建环境沿用本机已验证的进程级设置：

```powershell
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:\AndroidProjects\LizhangApp\build'
.\gradlew.bat test lint assembleDebug assembleRelease assembleDebugAndroidTest --console=plain
.\gradlew.bat updateDebugScreenshotTest validateDebugScreenshotTest --console=plain
```

Android 17 预览模拟器与仓库当前 Espresso 输入注入接口不兼容，错误为 `InputManager.getInstance` 不存在。交互验收改用已有 Android 16 / API 36 镜像创建的独立测试设备，没有为此更改产品依赖。

## 性能边界

使用稳定键和可回收的列表项，菜单仅两项，动画只在开合时运行。1000 条虚构联系人能够滚动到最后一项并点击，不代表已完成真机帧率和长期内存基准；本轮不声明量化性能提升。APK、临时模拟器、测试日志和截图渲染中间文件均不提交。

## 文件索引

- 设计系统：`ui/component/GlassComponents.kt`、`GlassActionMenu.kt`、`IllustrationPainter.kt`、`LiZhangComponents.kt`、`ConfirmDialog.kt`、`GiftEntryComponents.kt` 和 `ui/theme/Color.kt`。
- 页面：`ui/screen/` 中首页、联系人、联系人编辑/详情/导入、记一笔、礼金详情、提醒与往来列表、搜索、统计、设置和说明页面。
- 资源与版本：`app/build.gradle.kts`、`res/values/strings.xml` 和 `res/drawable-nodpi/` 的六张猫咪 PNG。
- 回归：`GlassUiInstrumentedTest`、`CatAssetTransparencyTest`、现有导入与批量管理设备测试、`GlassContrastTest`、`GlassScreenshotTest` 和 37 张正式截图基线。
- 文档：`README.md`、本文。完整逐文件差异以分支 Git diff 为准。