# 礼账 App

礼账是一款面向家庭的本地优先人情礼金记录应用。产品聚焦礼金往来、联系人管理与历史礼簿迁移，第一阶段以 Android 端离线使用为核心，不依赖账号或云服务。

## 当前开发进度

已完成：项目基础设施、MVVM 分层、Room 数据库、联系人与礼金记录 CRUD、StateFlow 状态管理、Navigation Compose 路由、主题和通用格式化工具。

当前页面为功能占位页面，未进入最终视觉设计阶段。后续会先完成设计系统和原型，再替换 Compose 页面视觉实现；数据与业务层无需重构。

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
│   ├── screen/           # 轻量页面占位实现
│   ├── theme/            # Material 3 颜色与排版体系
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

除首页、联系人及联系人详情的基础数据绑定外，其他页面只保留占位实现，避免在设计系统确定前投入 UI 打磨成本。

## 运行方式

1. 在 Android Studio 选择 **Open**，打开本目录。
2. 确认 Gradle JDK 使用 Android Studio 自带的 JDK。
3. 等待 Gradle 同步完成并下载依赖。
4. 选择 Pixel 9 模拟器，点击运行。

如使用命令行，请先由 Android Studio 生成/下载 Gradle Wrapper，再执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

## 后续开发计划

1. 为新增礼金、联系人编辑和详情记录补上设计定稿后的 Compose 表单。
2. 完成搜索、事件/年份统计 dashboard 和筛选。
3. 实现 CSV/Excel 导入导出、本地备份与恢复。
4. 增加 OCR 图片识别及“待确认批量导入”流程。
5. 以本地账本边界为基础，扩展云同步、家庭共享和账号体系。

每个功能完成后必须同步更新本 README、执行构建验证，并保持领域层和数据层不依赖具体视觉设计。
