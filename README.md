# 礼账 App

礼账是一款面向家庭的本地优先人情礼金记录应用。产品聚焦礼金往来、联系人管理与历史礼簿迁移，第一阶段以 Android 端离线使用为核心，不依赖账号或云服务。

## 当前开发进度

已完成：项目基础设施、MVVM 分层、Room 数据库、联系人与礼金记录 CRUD、StateFlow 状态管理、Navigation Compose 路由、主题和通用格式化工具。

已完成第一版 Jetpack Compose UI 框架。视觉方向为面向广泛用户的“简约中国古代风”：使用宣纸暖白、墨黑、低饱和印章红、青灰和茶褐，强调秩序、留白与可读性，不使用动态取色、红金宫廷风或装饰性仿古元素。首批用户由开发者父母验证易用性，但产品不定位为中老年专用 App。

工程已迁移至 `D:\AndroidProjects\LizhangApp`，并完成 Gradle Wrapper、Android Studio Gradle Sync、Clean、`assembleDebug` 及 Pixel 9 模拟器运行验证。Git 主分支为 `main`，远程仓库为 `https://github.com/Songbreezegit/LizhangApp.git`。

首页、联系人、联系人详情、新增礼金、搜索、统计、OCR 导入入口和设置页面均已替换占位实现，并接入四项底部导航。首页汇总、最近记录、联系人查询、礼金保存和统计聚合使用现有 Repository 数据；OCR、导出、备份恢复等尚未具备底层能力的入口会明确提示仍在完善，不会伪造执行结果。

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
│   ├── screen/           # 8 个 Compose 页面
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
- 新增礼金
- 联系人
- 联系人详情（带 `contactId` 参数）
- 统计
- 搜索
- 设置
- OCR 礼簿识别与待确认导入入口

底部导航固定为首页、联系人、统计、设置四项；新增礼金使用全局主操作入口，搜索和 OCR 从首页进入，不占用底部导航位置。

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

1. 在 Pixel 9 与大字体环境完成第一轮易用性走查，收集首页信息层级和新增礼金流程反馈。
2. 完善联系人快速创建、联系人编辑、日期选择器和记录筛选交互。
3. 为所有页面补齐可交互的加载、空、错误和成功状态测试。
4. 实现 CSV/Excel 导出、本地备份与恢复。
5. 接入 OCR 图片识别服务，并实现严格的“待确认批量导入”流程。

每个功能完成后必须同步更新本 README、执行构建验证，并保持领域层和数据层不依赖具体视觉设计。
