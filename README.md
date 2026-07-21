# 礼账 App

礼账是一款面向家庭的本地优先人情礼金记录应用。产品聚焦礼金往来、联系人管理与历史礼簿迁移，第一阶段以 Android 端离线使用为核心，不依赖账号或云服务。

## 当前开发进度

已完成：项目基础设施、MVVM 分层、Room 数据库、联系人与礼金记录 CRUD、StateFlow 状态管理、Navigation Compose 路由、主题和通用格式化工具。

已根据正式视觉参考图完成第二版 Jetpack Compose UI。视觉语言调整为温暖、治愈的家庭礼簿风格：全局奶白背景、珊瑚粉主色、薄荷绿收支对比、低饱和糖果色功能入口、白色柔和圆角卡片，以及统一的猫咪与家庭人物水彩插画。底部主导航由导航根容器统一绘制为悬浮磨砂玻璃层，使用渐变、高光、描边和柔和投影营造液态玻璃观感，同时避免下层卡片透出形成色块；图标与文字整体垂直居中并正确避让系统手势区。保存、删除、导出和功能状态等瞬时反馈统一在页面中央显示，避免被悬浮导航遮挡；确认对话框继续使用居中模态布局。联系人头像由 Compose 统一生成浅灰圆形底与居中深灰姓氏，不再按记录随机选择人物图片。首批用户由开发者父母验证易用性，但产品不定位为中老年专用 App。

工程已迁移至 `D:\AndroidProjects\LizhangApp`，并完成 Gradle Wrapper、Android Studio Gradle Sync、Clean、`assembleDebug` 及 Pixel 9 模拟器运行验证。Git 主分支为 `main`，远程仓库为 `https://github.com/Songbreezegit/LizhangApp.git`。

首页、联系人、联系人详情、记一笔、搜索、收礼记录、送礼记录、日历、提醒、统计、OCR 导入入口和“我的”页面均已应用统一视觉系统。底部四项主入口为“首页 / 联系人 / 记一笔 / 我的”。首页汇总、最近记录、联系人查询、礼金保存、方向筛选、日历和统计聚合均使用 Repository 的真实数据流。

“记一笔”主入口先提供“手动添加 / 拍照识别礼簿”两种方式；首页悬浮添加按钮复用相同分流。手动表单具备可搜索联系人选择器、快速新建联系人、全中文年月日选择器、字段校验、保存中防重复提交和成功反馈。首页、搜索、收礼、送礼、日历、提醒和联系人详情中的礼金记录均可进入独立详情页，并继续编辑全部业务字段；删除必须二次确认。编辑页按 `recordId` 从 Room 恢复状态，进程重建时不依赖上一个页面传递完整对象。联系人模块支持搜索、中文姓名排序、新增、详情查看、完整资料编辑和删除；删除联系人会明确提示相关礼金记录将级联删除。OCR 已接入 ML Kit 端侧中文模型，可直接拍照或通过系统文件选择器读取图片，无需账号、API 密钥或首次联网下载。识别文字按版面行组合后提取姓名、金额和日期，并进入 `StateFlow` 驱动的待确认列表；低置信度、缺少日期和疑似重复记录会明确标记，用户可逐条编辑或删除。确认前不会写入数据库；确认后在单个 Room 事务中复用同名联系人、创建缺失联系人并批量导入礼金记录。设置页的 CSV 与 Excel 导出均已接通 Room 真实数据和 Android 系统文件保存器，输出包含中文表头、金额、方向、事件、日期、备注和创建时间。CSV 使用 UTF-8 BOM 兼容常用表格软件；Excel 输出标准 `.xlsx` 工作簿，具备冻结表头、自动筛选、列宽和金额数值格式。两种格式对空数据、取消和保存失败均有明确反馈。

本地备份与恢复已完成。应用使用专用 `.lizhangbackup` 文件保存联系人和礼金记录的领域数据快照，不直接复制 SQLite 文件；备份包含格式版本、生成时间和 SHA-256 完整性校验。恢复前会校验文件魔数、大小、数据数量、文本长度、枚举、重复主键及联系人外键，并显示备份时间与记录数量供用户二次确认。只有完整校验通过后才会在单个 Room 事务中替换当前数据，任何插入异常都会整体回滚。备份创建、文件读取、不可撤销警告、恢复成功和失败反馈均使用中文居中界面。

系统日期提醒已完成。提醒页根据历史礼金记录的月日计算下一次年度日期，展示未来 30 天的联系人和事件；用户主动开启开关后，Android 13 及以上版本才请求通知权限。用户可选择“当天 / 提前 1 天”并通过居中时间选择器设置任意 24 小时制提醒时间，设置持久保存在本地，修改后会立即重新安排全部提醒。系统调度使用 `AlarmManager.setAndAllowWhileIdle` 发送通知，不申请精确闹钟权限；并在礼金记录变化、备份恢复、开关或提醒设置变化、应用升级、设备重启、日期或时区变化后重新同步。未来记录只按其真实年份调度，不会提前按年度重复；2 月 29 日在非闰年调整至当月最后一天，提前提醒也能正确跨月和跨年。

已建立 ViewModel、导出、备份、提醒与 OCR 解析单元测试，覆盖礼金编辑状态恢复、金额更新、创建时间保留、删除完成状态、首页年度收送汇总、最近记录数量限制、联系人查询、中文姓名排序、CSV 特殊字符转义、XLSX 压缩包结构与 XML 转义、导出时间戳文件名、空数据保护、备份完整字段往返、文件篡改检测、无效外键拒绝、恢复前确认和恢复完成状态、年度提醒边界，以及 OCR 单行提取、版面单元格合并、缺少日期和无效行过滤。联系人“按姓名”使用中文 Collator，而非 Unicode 编码顺序。测试使用 Kotlin Coroutines Test 的可控主线程调度器和内存 Fake Repository，不依赖 Android 设备或真实数据库。

已接入官方 Compose Preview Screenshot Testing（当前为实验性版本），并建立首页、联系人、记一笔入口和“我的”四个主页面标准状态基准图。基准覆盖奶白背景、年度汇总、快捷入口、最近记录、姓氏头像、联系人列表、记账方式、设置分组和悬浮磨砂导航；截图测试位于独立 `screenshotTest` 源集，不进入正式 APK。视觉变更应先运行验证任务，确认差异符合预期后才能更新基准图。

## 视觉资源

项目插画使用 AI 生成后按页面用途切分，并保存于 `app/src/main/res/drawable-nodpi/`：

- `home_hero_cat.png`：首页年度汇总横幅。
- `avatar_*.png`：早期人物头像资源，当前联系人列表改用 Compose 姓氏头像，保留供后续资料头像功能评估。
- `page_contacts_cat.png`：联系人及搜索页面。
- `page_add_cat.png`：记一笔、空状态及 OCR 页面。
- `page_statistics_cat.png`：统计页面。
- `page_settings_cat.png`：“我的”页面。

所有姓名、金额、日期、标签和按钮均由 Compose 原生渲染，插画不包含业务文字或虚构数据。

## 技术栈

- Kotlin / Coroutines / StateFlow
- Jetpack Compose / Material 3
- Compose Preview Screenshot Testing
- Navigation Compose
- Room（SQLite）与 KSP
- ML Kit Text Recognition v2 中文端侧模型（随应用打包）
- MVVM + Repository Pattern
- Android Gradle Plugin 8.13.2、Kotlin 2.2.21
- `compileSdk 36`，最低支持 Android 8.0（API 26）

## 架构设计

项目采用单模块、按职责分包的 Clean MVVM 结构。当前规模下保持单模块能减少构建复杂度；边界已经清晰，未来可平滑拆分为 `core`、`data`、`feature-*` 模块。

```text
ui → ViewModel → domain.repository ← data.repository → Room DAO → SQLite
图片 URI → ML Kit → 纯 Kotlin 礼簿解析 → 待确认状态 → Room 事务导入
```

- `ui`：Compose 页面、导航、主题、UI 映射与 ViewModel；不直接操作数据库。
- `domain`：纯 Kotlin 的业务模型和仓库接口；不依赖 Android 或 Compose。
- `data`：Room 实体、DAO、数据库、实体映射与仓库实现。
- `core`：常量、金额与日期等通用工具。
- `data/di/AppContainer`：应用依赖组合根。当前手写注入，后续接入 Hilt 时只需替换此层。

OCR 选择内置模型以保证离线可用，因此通用 Debug APK 会包含多套 CPU 架构的原生模型，体积明显增大；正式发布使用 Android App Bundle 后由应用商店按设备架构拆分交付。

## 项目目录

```text
app/src/main/java/com/yangsong/lizhang/
├── core/                 # 常量、格式化与扩展函数
├── data/
│   ├── di/               # 应用级依赖组合根
│   ├── local/            # Room 数据库、DAO、实体、投影
│   ├── mapper/           # 数据实体与领域模型映射
│   ├── ocr/              # ML Kit 中文识别与 Room 事务导入
│   ├── reminder/         # AlarmManager、通知渠道与重调度接收器
│   └── repository/       # 仓库接口实现
├── domain/
│   ├── backup/           # 版本化备份格式、完整性校验与安全解析
│   ├── export/           # CSV 与标准 XLSX 文档生成
│   ├── model/            # 纯业务模型
│   ├── ocr/              # 礼簿版面行组合与字段解析
│   ├── reminder/         # 年度日期提醒的纯 Kotlin 计算规则
│   └── repository/       # 仓库接口
├── ui/
│   ├── component/        # 可复用 Compose 组件
│   ├── mapper/           # 枚举到字符串资源映射
│   ├── navigation/       # 页面路由与导航图
│   ├── screen/           # Compose 页面与按业务聚合的页面文件
│   ├── theme/            # 固定色板、排版与设计令牌
│   └── viewmodel/        # 页面状态和业务协调
├── LiZhangApplication.kt
└── MainActivity.kt
```

## 数据库设计

### Contact（联系人）

| 字段 | 说明 |
| --- | --- |
| `id` | 自增主键 |
| `name` | 姓名，已建立索引 |
| `phone` | 可选手机号，已建立索引 |
| `relationship` | 可选关系 |
| `notes` | 可选备注 |
| `createdTime` | 创建时间戳 |

### GiftRecord（礼金记录）

| 字段 | 说明 |
| --- | --- |
| `id` | 自增主键 |
| `contactId` | 外键，关联联系人；联系人删除时级联删除记录 |
| `amountInCents` | 金额，单位为分，避免浮点精度错误 |
| `eventType` | 婚礼、满月、生日、乔迁、节日、其他 |
| `eventDate` | 事件日期时间戳，已建立索引 |
| `direction` | `RECEIVED`（收到）或 `GIVEN`（送出） |
| `notes` | 可选备注 |
| `createdTime` | 创建时间戳 |

Room 同时提供联系人汇总投影和“礼金记录 + 联系人名称”投影，为联系人详情、统计、搜索与首页仪表盘预留查询能力。

备份恢复通过专用 Repository 访问 Room。导出时在事务内读取一致快照；恢复时先在数据库外完整解析和校验，再按“礼金记录 → 联系人”的顺序清空旧数据、按“联系人 → 礼金记录”的顺序写入备份，并由 Room 单事务保证原子性。此功能没有改变数据库表结构，当前数据库版本仍为 1。

## 已建立的路由

- 首页
- 记一笔方式选择
- 手动新增礼金
- 礼金记录详情（带 `recordId` 参数）
- 礼金记录编辑（带 `recordId` 参数）
- 联系人
- 联系人详情（带 `contactId` 参数）
- 联系人新增/编辑（复用表单，带 `contactId` 参数）
- 收礼记录
- 送礼记录
- 日历视图
- 未来 30 天提醒
- 统计
- 搜索
- 设置
- OCR 礼簿识别与待确认导入入口

底部导航固定为首页、联系人、记一笔、我的四项；搜索、收礼、送礼、日历、提醒、统计和 OCR 作为业务子页面，不占用底部导航位置。

## 运行方式

1. 在 Android Studio 选择 **Open**，打开本目录。
2. 确认 Gradle JDK 使用 Android Studio 自带的 JDK。
3. 等待 Gradle 同步完成并下载依赖。
4. 选择 Pixel 9 模拟器，点击运行。

当前 Windows 开发工作目录为 `D:\AndroidProjects\LizhangApp`。如果首次同步仅因 Gradle 源码分发包网络超时而长时间等待，可临时启用 Android Studio 的 Gradle Offline Mode；该设置属于本机 `.idea` 配置，不提交到仓库。新增依赖前应恢复联网模式并先完成依赖下载。

如使用命令行，请先由 Android Studio 生成/下载 Gradle Wrapper，再执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

首页截图回归验证：

```powershell
.\gradlew.bat :app:validateDebugScreenshotTest
```

仅在已经人工确认视觉变更正确时更新截图基准：

```powershell
.\gradlew.bat :app:updateDebugScreenshotTest
```

## 后续开发计划

1. 在 Pixel 9 与大字体环境完成第一轮真实用户易用性走查。
2. 为四个主页面补充大字体、空状态和深色模式截图变体，并继续补齐 ViewModel 单元测试。
3. 为备份文件增加可选密码加密，并建立数据库升级后的跨版本恢复测试。
4. 使用不同纸张、手写体和光照条件建立 OCR 样本集，持续改进版面解析规则。
5. 根据首轮使用反馈补充更多提醒周期，例如提前三天或按农历日期提醒。

每个功能完成后必须同步更新本 README、执行构建验证，并保持领域层和数据层不依赖具体视觉设计。
