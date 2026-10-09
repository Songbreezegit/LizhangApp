# 礼账 v1.0.0 第二轮发布收尾：RC2 验收报告

日期：2026-10-10。交付分支：`release/v1.0.0-rc2`。

本轮交付是待审查的发布候选。代码可编译、用户在候选包内确认告知，均不等于运营方批准法律文案或已经正式发布。未操作 main、应用商店、ECS、DNS、ICP备案及线上网站。

## 基线与保护范围

- 执行 `git fetch origin` 后从最新 `origin/release/v1.0.0-rc1` 创建 RC2，基线为 `b6adf6ba516ed280bb1c7882b78cfa36e4449b1a`。
- 只读参考文档站分支 `origin/codex/lizhang-docs-site-ecs`，参考提交 `9ae64127643baf091fa46faad6da0840fc89c136`，没有合并该分支。
- 保持已验收的 900ms 动画轨道、CSV 防护、Room 表结构、备份格式及既有页面视觉。只增加本任务要求的告知、文档阅读和“我的”文档入口。
- 原有未提交的 README 隐私草稿链接、`docs/隐私政策.md`、`website/` 和 `.codex-remote-attachments/` 保留，不纳入 RC2 提交；README 本轮只提交追加的 RC2 里程碑。

## 离线文档与语言方案

完整隐私政策、用户协议、帮助文档随 APK 放在 `assets/legal/`。正文由本地仓库读取，ViewModel 提供页面状态，沿用玻璃卡片及返回导航。无需网络即可阅读。官方链接预留为：

- `https://lizhang.songisle.xyz/privacy/`
- `https://lizhang.songisle.xyz/terms/`
- `https://lizhang.songisle.xyz/help/`

在线操作通过系统浏览器 Intent 打开；未新增 Android INTERNET 权限，未访问或部署上述网站。

七种 UI 语言有完整首次告知、按钮、错误提示及回退说明。完整长文目前统一回退到中文审核稿，明确提示正文语言与待批准状态；不以摘要替代政策正文。简体中文新增资源随构建生成 `values-zh` 副本，繁体中文独立资源。正式批准前须由开发者确认此回退方案与目标发行地区是否适合，或提供并审核完整译文。

文案核对详情见 [RC2 文案代码核对](RC2文案代码核对.md)。姓名、完整号码及导入预览、Android 通知系统、账本与偏好、本地窗口画面、备份范围、明文导出、第三方文件服务及删除/恢复后果均按代码说明。

## 首次隐私告知与升级

隐私确认独立存储在 SharedPreferences，不新增数据库表。持久化的是用户明确确认/拒绝的内容版本；旧引导完成状态不作为隐私同意。当前候选内容标识为 `1.0.0-rc2-policy-v1`，不是已生效政策版本。

- 首次打开及尚未确认当前内容版本的升级用户先看到告知。没有默认勾选，没有用“下一步”代替同意。
- 用户可在确认前阅读完整政策和协议。拒绝页提供返回阅读、退出；不请求通讯录或通知权限。
- 在明确确认且成功写盘前，不挂载业务导航、不初始化 Room 账本或读取提醒，不启动提醒协调；广播接收器也检查确认状态。
- 确认后沿用原有记账引导及按页提示；升级保留账本和原有引导偏好，不清空旧状态。候选版本的再次告知不会强制重放旧引导。
- 内容版本变更后再次告知；确认写盘失败时保留在告知流程。
- 通讯录、通知仍仅由对应主动操作触发；拒绝可选权限不作为普通记账门槛。

## 构建与正式发布批准

常规 Debug / Release 均为 `versionName=1.0.0-rc2`、`versionCode=23`。正式 `1.0.0` 的构建需要显式选择并通过内容批准检查；默认配置保持未批准，禁止把候选审核稿按正式版本打包。

批准检查绑定完整离线正文、文档展示资源和七种语言告知资源的内容摘要，并核对真实运营资料、版本及批准记录。文案改变后必须重新批准。正式签名材料由本机私有配置提供，仓库没有加入密钥或密码。

当前配置没有可确认的正式签名身份。本轮不生成替代密钥、不修改签名身份。候选 Release 为未签名检查产物，不能作为已签名正式 APK 交付。最终签名操作与检查命令见 [RC2 签名与发布检查](RC2签名与发布检查.md)。

## 实际验证

日志、JUnit XML、lint 和 APK 检查证据保留在项目 `build/rc2-check/` 及 `app/build/` 中，不提交二进制或合成测试数据文件。测试只使用合成样例，不读取或提交真实联系人、礼金及备份。

要求的执行任务为 `test`、`lint`、`assembleDebug`、`assembleRelease`、`assembleDebugAndroidTest`，另检查 Release lint、Release 依赖及正式构建的默认拒绝行为。

### 首轮实际失败与修复

首轮全任务命令使用项目内 `TEMP` / `TMP` 和 `JAVA_TOOL_OPTIONS`，单工作线程、Kotlin 进程内编译。实际 **BUILD FAILED，5 分 41 秒**：Debug / Release 各 31 类、190 项单元测试已通过；两种 App APK 与测试 APK 已打包，但 lint 报 **3 错误、58 警告、1 提示**，因此不能按整轮成功交付。

三项错误均为 `PrivacyNoticeUiInstrumentedTest` 在 `setContent` 内直接构造 `PrivacyConsentViewModel`（`ViewModelConstructorInComposable`）。已把实例移至 Compose 内容之外，确保重组时不重复创建。没有启用 baseline、禁用 lint、删除断言或放宽错误等级。原失败证据保留：

- `build/rc2-check/gradle-required-first.log`
- `build/rc2-check/lint-first-failed.xml`
- `build/rc2-check/lint-first-failed.txt`

独立审查另修正正式门控的政策常量文件定位，实际常量位于 `domain/legal/LegalDocument.kt`；补齐七种语言草案标记识别，避免只检查中文。旧已同意业务仪器测试增加启动前的明确确认 fixture，真正首次/未确认升级测试保持未同意前置。历史三页及四步引导断言同步当前真实入口、五步记账与按页引导，不改变生产视觉或 900ms 帧与时间断言。

17 组已同意业务用例的前置规则、两阶段升级建样方式，以及 FeatureGuideUi 保留 10 项用例并迁移现有契约的逐项映射见 [RC2 仪器测试迁移说明](RC2_TEST_FIXTURES.md)。多语言、主题、方向组合与圆泡截图断言尚未在设备执行；不能把 224 个参数组合视作 224 项已运行测试。

### 内容批准拒绝验证

实际执行 `-PofficialRelease=true assembleRelease :app:printLegalContentDigest`：`verifyOfficialReleaseContent` 按预期因 `approval_status` 未获本人确认失败，Gradle 退出码 **1**，**35 秒**；现有候选 Release APK 的 SHA-256 前后相同，没有产生正式包。日志：`build/rc2-check/official-release-expected-rejection.log`。这是门控拒绝的预期结果，区别于必需候选构建任务应全部成功。

Gradle 与 PowerShell 对 **18 个文件**的摘要一致：`7f5996b0ee102d860834d3aec2a0f1faaec71f1d9fdf88db39ff53978130775f`。其中包含三份全文、元数据及七种语言的两类 XML。共享七语扫描规则对 **35/35 条实际候选提示**全部命中，18/18 文件仍含待批准内容；扫描没有授予批准。证据：`build/rc2-check/legal-digest.json`、`seven-language-draft-scan.json`。

### 新增测试覆盖与边界

新增 **23 项 JVM 用例**：隐私版本策略 6、隐私 ViewModel 4、旧引导与隐私相互独立 3、告知七语资源 1、完整离线正文/解析/资源 6、文档加载/失败/重试 3。加上 RC1 原有 167 项，共 190 项独立测试，在 Debug / Release 两种变体执行。

新增 **15 项设备用例**：偏好持久化 4、拒绝/选择/大字号深浅色 UI 4、未确认不处理及真实旧账本保留 2、离线正文/入口/返回/语言 4、拒绝可选权限后真实保存页面与隔离 Room CRUD 1。它们本轮仅编译，不计为执行通过。权限拒绝用例涵盖真实保存 UI 及数据库查/改/删，不代表 MainActivity 全链路或编辑/删除页面已完成设备验收。

### 冻结源码后的最终完整结果

进程内 `TEMP` / `TMP` 为 `D:\AndroidProjects\LizhangApp\build\rc2-check\tmp`，`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/AndroidProjects/LizhangApp/build/rc2-check/tmp`，未修改全局环境。最终完整执行：

```powershell
.\gradlew.bat --continue --no-daemon --console=plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' test lint assembleDebug assembleRelease assembleDebugAndroidTest :app:lintRelease :app:dependencies --configuration releaseRuntimeClasspath :app:printLegalContentDigest
```

结果：**BUILD SUCCESSFUL，4 分 30 秒，153 项任务（45 执行、108 已更新）**。此前三项 lint 错误全部消除。此后只更新验收文档，没有再改 App、资源、构建、脚本或测试源码。

| 检查 | 实际结果 |
| --- | --- |
| `test` Debug | 31 类，190/190 通过，0 失败、0 错误、0 跳过。 |
| `test` Release | 31 类，190/190 通过，0 失败、0 错误、0 跳过。两种合计 380/380；独立用例为 190 项。 |
| `lint` | Debug 0 错误、58 警告、1 提示。 |
| `:app:lintRelease` | Release 0 错误、63 警告、1 提示。 |
| `assembleDebug` | 成功，`1.0.0-rc2 / 23`，实际 APK v2 签名核验通过。 |
| `assembleRelease` | 成功，未签名 `1.0.0-rc2 / 23` 候选；不能直接安装或提交商店。 |
| `assembleDebugAndroidTest` | 全部仪器源码编译及打包成功。设备用例实际执行 0 项。 |
| Release 运行时依赖 | 解析成功；本轮无依赖新增，未见 JUnit、AndroidX Test、Espresso、调试宿主依赖进入 Release 运行时。`ui-tooling-preview` 为既有预览注解依赖。 |
| 实际 Release APK 检查 | 检查脚本成功；应用 ID、版本、SDK、不可调试、权限、Manifest/DEX 测试隔离及四份离线资产与当前源码一致。 |
| 内容摘要 | 最终 Gradle 输出仍与 PowerShell 的 18 文件摘要一致。 |
| Git 空白及范围检查 | 通过；生产动画核心、CSV 防护、Room 实体与 schema 无差异，原有隐私草稿未改动。 |

证据：`build/rc2-check/gradle-final.log`、`junit-summary.json`、`lint-debug-summary.json`、`lint-release-summary.json`、`release-apk-check.json`；完整 JUnit XML/HTML 与 lint XML/HTML 在 `app/build/test-results/` 和 `app/build/reports/`。首轮失败报告仍保留，不覆盖为成功报告。

全部警告/提示分类如下；没有把“存在新版本”提示当作已确认安全漏洞，也没有为了清零而升级依赖或修改既有设计。

| 分类 | Debug | Release | 处理说明 |
| --- | ---: | ---: | --- |
| `GradleDependency` | 18 | 18 | 既有依赖维护建议，另行评估升级。 |
| `NewerVersionAvailable` | 3 | 3 | 既有第三方版本提示。 |
| `AndroidGradlePluginVersion` | 1 | 1 | 既有构建工具版本提示。 |
| `UnusedResources` | 24 | 27 | 相比 RC1 多 11 项：旧简短隐私说明被完整正文取代，原七语旧资源仍保留，后续可独立清理。 |
| `UseKtx` | 7 | 7 | 既有 6 项及本轮浏览器 URI 写法建议 1 项；可延期，不影响离线/权限行为。 |
| `ModifierParameter` | 1 | 1 | 既有 Compose API 约定提示。 |
| `ObsoleteSdkInt` | 1 | 1 | 既有资源目录兼容提示。 |
| `PluralsCandidate` | 1 | 1 | 既有英文数量文案维护项。 |
| `UnusedAttribute` | 1 | 1 | 既有 localeConfig 兼容提示。 |
| `ViewConstructor` | 1 | 1 | 既有原生窗口工具构造提示。 |
| `MonochromeLauncherIcon` | 0 | 2 | 既有 Release 图标维护项，本轮不改图标视觉。 |
| `AutoboxingStateCreation`（提示） | 1 | 1 | 既有状态装箱优化提示。 |

### 最终候选产物

| 产物 | 字节数 | SHA-256 | 签名/使用边界 |
| --- | ---: | --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 30246011 | `a3f3f2879d68dad81a879dca848c2b0415ed5867f63556553f381f8ff4b8fa04` | Debug v2 签名有效，非正式签名身份；未安装、未运行。 |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 22615148 | `9a11bab5e53acad3850ee78e0bfc0c5d9373e0b5781b4298427e0248c4857ad8` | 未签名候选，签名核验按预期不通过。 |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 1826859 | `6aaa7ae7358eb5bec15fc8e612b4c83183d9df7bba5b3a437e283dd3514c292f` | 测试组件独立包，编译成功不代表设备测试已通过。 |

Release APK 的实际权限为已有 `READ_CONTACTS`、`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`，以及 AndroidX 合并生成的签名级 `com.yangsong.lizhang.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。不存在 INTERNET、WRITE_CONTACTS、可调试标志、测试宿主或仪器入口；`BuildConfig.DEBUG=false`。包内完整政策/协议/帮助/元数据逐文件 SHA-256 与源码一致；没有密钥或本机配置文件进入 APK。

设备测试边界：本轮不执行实机或模拟器仪器测试，`assembleDebugAndroidTest` 仅验证编译/打包。启动、实际窗口画面、深浅色切换、大字号布局、系统语言重建、浏览器与通知实际行为、持久化/升级真实设备流程仍需后续设备验收。不得把 JVM 测试或 APK 静态检查表述成设备行为已验收。

## 开发者本人待确认

1. 运营者真实姓名/名称、联系地址、可用隐私联系邮箱/渠道及权利请求处理安排。
2. 政策与协议生效日期、正式内容版本、最终条款及未成年人服务安排、软件许可与分发安排。
3. 七语言首次告知及完整中文正文回退方案；目标地区需要的完整译文与审核。
4. 网站备案主体、真实ICP备案号及相关展示资料。占位文本不能视作备案完成。
5. 明确的内容批准记录：批准人、批准日期、版本、最终全文摘要；不能只因为构建成功就批准。
6. 正式签名密钥是否已存在、准确路径/别名、既有证书指纹、与历史分发包是否一致及私有密码来源。签名身份必须由开发者确认；若密钥不存在，另行决定，不能自动生成替代。
7. 本报告列明的未运行设备验收与发布资料最终审查。

技术依据：[Android 官方浏览器 Intent](https://developer.android.com/guide/components/intents-common#Browser)；法律文案复核参考：[个人信息保护法官方原文](https://www.cac.gov.cn/2021-08/20/c_1631050028355286.htm)。本轮不作法律审核通过的结论。

## 修改文件清单

本轮共 **84 个文件**；README 仅追加 RC2 里程碑，原有草稿改动不在提交中。完整 commit SHA 由交付回复提供。

```text
README.md
app/build.gradle.kts
app/src/androidTest/java/com/yangsong/lizhang/fixtures/AcceptedPrivacyRule.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/AppearanceFailureInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/AppearanceTransitionInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/BasicLedgerFlowInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/ContactImportFlowInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/ExpandedLanguageInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/LocalePersistenceInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/LocalizationInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/NativeTransitionRecordingTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/NavigationPageMotionInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/StartupAnimationInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/StartupRealFrameInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/StartupWindowGateInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/flow/TransitionVisualInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/legal/LegalDocumentInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/onboarding/FeatureGuideEntryInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/onboarding/FeatureGuideUiInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/onboarding/OnboardingEntryInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/onboarding/OnboardingLegacyUpgradeInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/onboarding/OnboardingLocaleInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/onboarding/OnboardingPermissionInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/onboarding/OnboardingUpgradeInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/privacy/PermissionDeniedLedgerInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/privacy/PrivacyConsentPreferencesInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/privacy/PrivacyNoticeUiInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/privacy/PrivacyProcessingGateInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/reminder/IndependentNotificationInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/reminder/IndependentReminderInstrumentedTest.kt
app/src/androidTest/java/com/yangsong/lizhang/reminder/ReminderNavigationInstrumentedTest.kt
app/src/main/assets/legal/help.md
app/src/main/assets/legal/metadata.properties
app/src/main/assets/legal/privacy.md
app/src/main/assets/legal/terms.md
app/src/main/java/com/yangsong/lizhang/LiZhangApplication.kt
app/src/main/java/com/yangsong/lizhang/MainActivity.kt
app/src/main/java/com/yangsong/lizhang/data/di/AppContainer.kt
app/src/main/java/com/yangsong/lizhang/data/legal/AssetLegalDocumentRepository.kt
app/src/main/java/com/yangsong/lizhang/data/preferences/SharedPreferencesPrivacyConsentRepository.kt
app/src/main/java/com/yangsong/lizhang/data/reminder/AndroidReminderRepository.kt
app/src/main/java/com/yangsong/lizhang/domain/legal/LegalDocument.kt
app/src/main/java/com/yangsong/lizhang/domain/privacy/PrivacyConsentState.kt
app/src/main/java/com/yangsong/lizhang/domain/repository/LegalDocumentRepository.kt
app/src/main/java/com/yangsong/lizhang/domain/repository/PrivacyConsentRepository.kt
app/src/main/java/com/yangsong/lizhang/ui/navigation/AppDestination.kt
app/src/main/java/com/yangsong/lizhang/ui/navigation/LiZhangNavGraph.kt
app/src/main/java/com/yangsong/lizhang/ui/privacy/PrivacyNoticeScreen.kt
app/src/main/java/com/yangsong/lizhang/ui/screen/InformationScreens.kt
app/src/main/java/com/yangsong/lizhang/ui/screen/LegalDocumentScreen.kt
app/src/main/java/com/yangsong/lizhang/ui/screen/SettingsScreen.kt
app/src/main/java/com/yangsong/lizhang/ui/viewmodel/LegalDocumentViewModel.kt
app/src/main/java/com/yangsong/lizhang/ui/viewmodel/OnboardingViewModel.kt
app/src/main/java/com/yangsong/lizhang/ui/viewmodel/PrivacyConsentViewModel.kt
app/src/main/res/values-b+zh+Hant/legal_strings.xml
app/src/main/res/values-b+zh+Hant/privacy_notice.xml
app/src/main/res/values-en/legal_strings.xml
app/src/main/res/values-en/privacy_notice.xml
app/src/main/res/values-es/legal_strings.xml
app/src/main/res/values-es/privacy_notice.xml
app/src/main/res/values-fr/legal_strings.xml
app/src/main/res/values-fr/privacy_notice.xml
app/src/main/res/values-ja/legal_strings.xml
app/src/main/res/values-ja/privacy_notice.xml
app/src/main/res/values-ko/legal_strings.xml
app/src/main/res/values-ko/privacy_notice.xml
app/src/main/res/values/legal_strings.xml
app/src/main/res/values/privacy_notice.xml
app/src/test/java/com/yangsong/lizhang/PrivacyNoticeResourcesTest.kt
app/src/test/java/com/yangsong/lizhang/domain/legal/LegalDocumentTest.kt
app/src/test/java/com/yangsong/lizhang/domain/privacy/PrivacyConsentPolicyTest.kt
app/src/test/java/com/yangsong/lizhang/ui/viewmodel/FeatureGuideViewModelTest.kt
app/src/test/java/com/yangsong/lizhang/ui/viewmodel/LegalDocumentViewModelTest.kt
app/src/test/java/com/yangsong/lizhang/ui/viewmodel/OnboardingViewModelTest.kt
app/src/test/java/com/yangsong/lizhang/ui/viewmodel/PrivacyConsentViewModelTest.kt
docs/RC2_TEST_FIXTURES.md
docs/RC2文案代码核对.md
docs/RC2签名与发布检查.md
docs/发布收尾第二轮RC2.md
release/legal-approval.properties
release/unapproved-content-pattern.txt
scripts/legal-content-digest.ps1
scripts/scan-unapproved-legal-content.ps1
scripts/verify-rc2-release.ps1
```
