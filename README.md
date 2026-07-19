# 礼账 App

礼账是一款面向家庭的本地优先人情礼金记录应用。产品聚焦礼金往来、联系人管理与历史礼簿迁移，第一阶段以 Android 端离线使用为核心，不依赖账号或云服务。

## 当前开发进度

已完成：项目基础设施、MVVM 分层、Room 数据库、联系人与礼金记录 CRUD、StateFlow 状态管理、Navigation Compose 路由、主题和通用格式化工具。

已根据正式视觉参考图完成第二版 Jetpack Compose UI。视觉语言调整为温暖、治愈的家庭礼簿风格：全局奶白背景、珊瑚粉主色、薄荷绿收支对比、低饱和糖果色功能入口、白色柔和圆角卡片，以及统一的猫咪与家庭人物水彩插画。底部主导航由导航根容器统一绘制为悬浮磨砂玻璃层，使用渐变、高光、描边和柔和投影营造液态玻璃观感，同时避免下层卡片透出形成色块；图标与文字整体垂直居中并正确避让系统手势区。联系人头像由 Compose 统一生成浅灰圆形底与居中深灰姓氏，不再按记录随机选择人物图片。首批用户由开发者父母验证易用性，但产品不定位为中老年专用 App。

工程已迁移至 `D:\AndroidProjects\LizhangApp`，并完成 Gradle Wrapper、Android Studio Gradle Sync、Clean、`assembleDebug` 及 Pixel 9 模拟器运行验证。Git 主分支为 `main`，远程仓库为 `https://github.com/Songbreezegit/LizhangApp.git`。

首页、联系人、联系人详情、记一笔、搜索、收礼记录、送礼记录、日历、提醒、统计、OCR 导入入口和“我的”页面均已应用统一视觉系统。底部四项主入口为“首页 / 联系人 / 记一笔 / 我的”。首页汇总、最近记录、联系人查询、礼金保存、方向筛选、日历和统计聚合均使用 Repository 的真实数据流。

“记一笔”主入口先提供“手动添加 / 拍照识别礼簿”两种方式；首页悬浮添加按钮复用相同分流。手动表单具备可搜索联系人选择器、快速新建联系人、全中文年月日选择器、字段校验、保存中防重复提交和成功反馈。首页、搜索、收礼、送礼、日历、提醒和联系人详情中的礼金记录均可进入独立详情页，并继续编辑全部业务字段；删除必须二次确认。编辑页按 `recordId` 从 Room 恢复状态，进程重建时不依赖上一个页面传递完整对象。联系人模块支持搜索、排序、新增、详情查看、完整资料编辑和删除；删除联系人会明确提示相关礼金记录将级联删除。OCR 已建立 `StateFlow` 驱动的介绍、不可用、待确认和成功状态，以及低置信度、疑似重复、逐条编辑、删除和确认弹窗组件；真实识别服务接入前不会读取图片或写入数据。设置、导出和备份入口均提供明确的“功能完善中”反馈，不执行虚假操作。

已建立首批 ViewModel 单元测试，覆盖礼金编辑状态恢复、金额更新、创建时间保留、删除完成状态、首页年度收送汇总、最近记录数量限制、联系人查询和中文姓名排序。联系人“按姓名”使用中文 Collator，而非 Unicode 编码顺序。测试使用 Kotlin Coroutines Test 的可控主线程调度器和内存 Fake Repository，不依赖 Android 设备或真实数据库。

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
- Navigation Compose
- Room（SQLite）与 KSP
- MVVM + Repository Pattern
- Android Gradle Plugin 8.13.2、Kotlin 2.2.21
- `compileSdk 36`，最低支持 Android 8.0（API 26）

## 架构设计

项目采用单模块、按职责分包的 Clean MVVM 结构。当前规模下保持单模块能减少构建复杂度；边界已经清晰，未来可平滑拆分为 `core`、`data`、`feature-*` 模块。

```text
ui → ViewModel → domain.repository ← data.repository → Room DAO → SQLite
```

- `ui`：Compose 页面、导航、主题、UI 映射与 ViewModel；不直接操作数据库。
- `domain`：纯 Kotlin 的业务模型和仓库接口；不依赖 Android 或 Compose。
- `data`：Room 实体、DAO、数据库、实体映射与仓库实现。
- `core`：常量、金额与日期等通用工具。
- `data/di/AppContainer`：应用依赖组合根。当前手写注入，后续接入 Hilt 时只需替换此层。

## 项目目录

```text
app/src/main/java/com/yangsong/lizhang/
├── core/                 # 常量、格式化与扩展函数
├── data/
│   ├── di/               # 应用级依赖组合根
│   ├── local/            # Room 数据库、DAO、实体、投影
│   ├── mapper/           # 数据实体与领域模型映射
│   └── repository/       # 仓库接口实现
├── domain/
│   ├── model/            # 纯业务模型
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

## 后续开发计划

1. 在 Pixel 9 与大字体环境完成第一轮真实用户易用性走查。
2. 扩展 ViewModel 单元测试，并建立 Compose 截图回归测试。
3. 实现 CSV/Excel 导出、本地备份与恢复。
4. 接入系统级提醒调度与通知权限管理。
5. 接入 OCR 图片识别服务，将真实结果映射到现有“待确认批量导入”状态。

每个功能完成后必须同步更新本 README、执行构建验证，并保持领域层和数据层不依赖具体视觉设计。
