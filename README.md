# 礼账 Android App

## 项目介绍

礼账是一款面向家庭与个人的人情礼金记录应用，用于管理联系人、收礼与送礼记录、日期提醒、统计以及本地数据备份。应用坚持本地优先，联系人和礼金数据保存在设备 Room 数据库中。

当前产品不包含 OCR、图片上传或云端识别功能。原有 OCR 实验因真实手写礼簿准确率不足已完整下线，记账入口统一使用可校验的手动录入流程。

## 技术栈

- Kotlin、Kotlin Coroutines、StateFlow
- Jetpack Compose、Material 3
- Navigation Compose
- Room、KSP
- MVVM、Repository Pattern
- Compose Preview Screenshot Testing
- Android Gradle Plugin 8.13.2、Kotlin 2.2.21
- `compileSdk 36`、`minSdk 26`、`targetSdk 36`

## 架构设计

项目采用单 `app` 模块、按职责分包的 Clean MVVM 结构：

```text
Compose UI
    ↓
ViewModel
    ↓
domain.repository
    ↑
data.repository / data.preferences
    ↓
Room / SharedPreferences / Android 系统能力
```

- `ui`：Compose 页面、通用组件、导航、主题和页面状态。
- `domain`：业务模型、Repository 接口、备份、导出和提醒规则。
- `data`：Room、Repository 实现、本地偏好和系统提醒实现。
- `core`：日期、金额、常量和扩展函数。
- `data/di/AppContainer`：应用级依赖组合根，为未来接入 Hilt 保留清晰边界。

业务逻辑不放在 Composable 中；UI 只观察 `StateFlow` 并发送用户意图。

## 项目目录

```text
app/src/main/java/com/yangsong/lizhang/
├── core/
│   ├── common/               # 常量
│   └── util/                 # 日期与金额工具
├── data/
│   ├── di/                   # 应用级依赖组合
│   ├── local/                # Room 数据库、DAO、实体和查询投影
│   ├── mapper/               # 数据层与领域层映射
│   ├── preferences/          # 主题等轻量本地偏好
│   ├── reminder/             # 系统通知与提醒调度
│   └── repository/           # Repository 实现
├── domain/
│   ├── backup/               # 版本化备份与完整性校验
│   ├── export/               # CSV、XLSX 生成
│   ├── model/                # 领域模型
│   ├── reminder/             # 年度提醒计算规则
│   └── repository/           # Repository 接口
├── ui/
│   ├── component/            # 可复用 Compose 组件
│   ├── mapper/               # 界面文案映射
│   ├── navigation/           # 路由与导航图
│   ├── screen/               # 页面
│   ├── theme/                # Material 3 色彩、排版与令牌
│   └── viewmodel/            # 页面状态和业务协调
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
| `contactId` | 联系人外键，联系人删除时级联删除 |
| `amountInCents` | 金额，单位为分 |
| `eventType` | 婚礼、满月、生日、乔迁、节日、其他 |
| `eventDate` | 事件日期时间戳 |
| `direction` | `RECEIVED` 或 `GIVEN` |
| `notes` | 可选备注 |
| `createdTime` | 创建时间戳 |

当前数据库版本为 1。备份恢复使用独立领域格式，不直接复制 SQLite 文件；普通备份格式已升级为版本 2，记录来源数据库版本，并兼容读取版本 1。普通备份使用 SHA-256 完整性校验，可选密码备份使用 PBKDF2-HMAC-SHA256 与 AES-256-GCM 加密认证，并在单个 Room 事务中恢复数据。

## 已完成功能

- 首页支持下拉选择统计年份，并按所选年份重新计算收礼、送礼与净往来汇总；候选年份根据设备当前年份和最早账本记录动态生成，不写死年份且不会超过现实年份。
- 手动新增、查看、编辑和删除礼金记录；录入页采用独立任务流，表单可滚动，保存按钮固定在系统安全区上方并随软键盘避让；存在未保存修改时，返回操作会先居中确认是否放弃。
- 联系人搜索、中文姓名排序、新增、编辑、详情与删除；新增入口采用与首页一致的圆形主色悬浮按钮。
- 收礼记录、送礼记录、日历视图、全局搜索和年度统计。
- 未来 30 天年度日期提醒，支持通知权限、当天、提前 1 天、提前 3 天、提前 7 天及提醒时间设置；点击系统通知可直接进入对应礼金记录，记录已删除时安全回到首页并居中提示。
- CSV 与标准 XLSX 导出。
- `.lizhangbackup` 本地备份、校验、确认和事务恢复；支持普通备份格式 v1/v2 兼容、来源数据库版本提示、普通或密码加密备份，密码不会持久化保存。
- Material 3 浅色/深色配色。
- 主题设置支持“跟随系统、浅色模式、深色模式”，选择结果持久保存并立即生效。
- 大字体与深色模式下的固定浅色插画卡、快捷图标、表单边界和选中标签采用独立高对比前景色，并由对比度单元测试与截图基准保护。
- 字体说明支持跳转 Android 系统显示设置；关于页说明产品范围和版本。
- 隐私说明明确本地存储、无联网权限、通知权限、导出备份和数据删除规则。
- 浮动半透明底部导航、奶白色背景、统一姓氏头像和中文居中反馈。

## 页面与导航

底部主导航固定为：

1. 首页
2. 联系人
3. 记一笔
4. 我的

“记一笔”和首页悬浮按钮现在均直接进入手动新增页面。进入录入任务后暂时隐藏主导航，用户通过保存或返回结束任务，避免底部导航遮挡保存按钮或造成误切换。业务子页面包括联系人详情、礼金详情与编辑、收礼记录、送礼记录、日历、提醒、统计、搜索、字体与显示、关于礼账和隐私说明。

## 测试与质量

单元测试覆盖：

- 礼金新增、编辑、删除和状态恢复。
- 首页汇总、最近记录、联系人查询与中文排序。
- CSV 转义、XLSX 文件结构与金额格式。
- 备份完整字段往返、v1/v2 格式迁移、普通与加密兼容、错误密码、密文篡改、无效外键和恢复确认。
- 年度提醒、多档提前日期、跨月跨年和闰日边界，以及提醒通知 Intent 唯一性、冷启动直达详情和失效记录回退。

四个主页面已建立 Compose Preview Screenshot Testing 基础，并补充首页与联系人空状态大字体、记一笔标准状态、放弃填写确认、深色大字体空表单与已填写状态、“我的”深色模式、加密备份密码状态和隐私说明大字体基准。首页年度汇总卡会根据系统字体比例自适应增高，避免金额摘要被截断；记一笔保存操作在标准字体、软键盘展开和大字体状态下均保持可见。固定浅色容器的关键前景色通过 WCAG 4.5:1 对比度单元测试。主要修改至少执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

涉及 Room 或恢复事务时，还需在已启动的模拟器或物理设备执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## 运行方式

1. 在 Android Studio 选择 **Open**。
2. 打开 `D:\AndroidProjects\LizhangApp`。
3. 使用 Android Studio 自带 JDK 17。
4. 等待 Gradle Sync 完成。
5. 选择 Pixel 9 模拟器或物理设备运行 `app`。

命令行构建：

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 当前开发进度

工程基础、核心数据模型、CRUD、导航、统计、搜索、多档年度提醒及通知直达详情、导出、v1/v2 兼容的普通与密码加密备份恢复、主题切换和设置说明页面均已完成。Pixel 9 的 1.3 倍字体与深色模式主流程走查已完成，并修复表单可发现性和固定浅色容器对比度。OCR 已从代码、导航、资源、依赖、权限、测试和文档中移除，应用不再申请联网权限，也不再读取 AI Studio 令牌。

## 后续开发计划

1. 在物理设备上复核常用流程、通知投递、文件导出与系统文件选择器。
2. 根据真实用户反馈评估农历日期和自定义提醒周期。
3. 在数据库首次升级时补充 Room Schema 1 到新版本的正式迁移与回归测试。

每个主要里程碑都需要更新本 README，并重新执行测试、Lint 和 Debug 构建。
