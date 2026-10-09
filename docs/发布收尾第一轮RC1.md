# 发布收尾第一轮：RC1 技术检查

日期：2026-10-10。分支：`release/v1.0.0-rc1`。本轮保持 `versionName=0.9.12`、`versionCode=22`，分支名不代表已经发布 1.0.0。

## 开发基线

开始前已执行 `git fetch origin`。远端 `codex/figma-guide-nav-startup` 仍为 `1ee3ea9e58a2dfb336ba3fca774dd3cf5ab674bc`；从该远端引用直接创建本轮分支，没有从文档站分支创建或合并网站提交。

- 远端 `main` 为 `6493392ca6740aa7314e4e10d1c97c7931849b03`，是 Android 基线的祖先；`origin/main...origin/codex/figma-guide-nav-startup` 为 **0 / 9**，没有远端 main 独有的遗漏提交。
- 已核对其他远端 Android 分支的祖先关系：通讯录导入、批量管理、性能优化、UI、语言、首次引导及动效分支均包含在该基线。
- 本地 main 另有未推送提交 `609024d`（2026-09-20，保存后立即返回），不在远端基线。后续已包含的 `45248e1`（2026-09-22）重新确定了并行反馈与 450ms 返回，并补充跨重建与重复保存保护；实现和历史说明见 `GiftSaveFeedbackEffects.kt`、[记一笔保存体验](记一笔保存体验0.6.0.md)。本轮保留后续已确定的设计，不将旧局部改动覆盖到当前实现，也不更改本地 main。
- 原有 README 隐私草稿链接、未跟踪 `docs/隐私政策.md` 和附件保留。README 切换前被单文件暂存，切换后恢复并核对；本轮提交只包含新增里程碑文字。

## 修复与修改清单

| 文件 | 修改及目的 |
| --- | --- |
| `app/src/main/java/com/yangsong/lizhang/domain/export/GiftRecordCsvFormatter.kt` | 中和危险文本单元格，数值和日期保留原格式；维持 BOM、七列和原有引号转义。 |
| `app/src/test/java/com/yangsong/lizhang/ui/viewmodel/CsvExportTest.kt` | 新增 7 项回归，覆盖公式前缀、绕过前缀、结构及普通输出保持、数值列和 XLSX 类型。 |
| `app/src/test/java/com/yangsong/lizhang/ui/viewmodel/StartupAnimationViewModelTest.kt` | 修正旧 880ms / 440ms 预期和窗口重建 ready 预期；新增 5 项状态机回归。 |
| `app/src/androidTest/java/com/yangsong/lizhang/flow/StartupWindowGateInstrumentedTest.kt` | 同步 900ms 结束、绘制后宽限降级语义及清理后的迟到回调注入。仅编译，不执行设备用例。 |
| `app/src/androidTest/java/com/yangsong/lizhang/flow/StartupRealFrameInstrumentedTest.kt` | 证据标签同步 900ms，明确原 140.8ms 保守采样阈值；未改视觉或重写采样算法。 |
| `app/src/androidTest/java/com/yangsong/lizhang/flow/GiftSaveFlowInstrumentedTest.kt` | 额外编译检查发现两个旧尾随 lambda 调用缺少 `onBack`；改为具名参数，业务代码不变。 |
| `docs/Android发布前代码审查第一轮.md` | 权限、依赖、发布隔离、日志、恢复、删除及隐私政策接入审查，区分本轮和后续事项。 |
| `docs/发布收尾第一轮RC1.md` | 本轮基线、修改清单、实测结果、限制及发布门槛。 |
| `README.md` | 增加本轮里程碑与两份报告链接，原有未提交草稿链接不纳入提交。 |

没有改变启动动画生产状态机、900ms 轨道、UI 视觉、版本字段、权限、依赖配置、Room 结构、备份格式或网站 / ECS 代码。

### 启动动画

原四项失败来自测试仍按 880ms 结束和 440ms 半程断言；当前设计是 900ms、450ms 半程。旧 `awaitWindow()` 后 `ready=true` 的断言也不符合重建时必须等待新窗口交接的契约。

修正后的 11 项 JVM 测试固定断言设计值为 900ms，并检查 880 / 899ms 尚可见、900ms 完成；重建保留首帧时间与进度但重新等待，旧宿主就绪 / 释放不能改变新宿主。补查窗口等待和播放绝对截止时间不因重建或重复就绪延长、剩余毫秒向上取整、首帧缺失 / 暂停后迟到帧按截止时间结束、释放幂等及回调不复活。

源码复核了 `MainActivity` 停止 / 销毁 / 通知跳转清理、`StartupWindowGate` 监听及 Handler 任务移除、Overlay 帧协程。未发现本轮需修改的确定生产状态机缺陷；JVM 测试不替代真实 Activity 资源、系统 Surface 或设备播放验证。

### CSV 防护与兼容性

参照 [OWASP CSV Injection](https://community.owasp.org/attacks/CSV_Injection)，对危险文本采用**双引号包裹，并在引号内加前导 TAB**。仅加单引号存在表格软件另存后重开时失效的情况，不作为本轮策略。检查 ASCII `= + - @` 和全角 `＝ ＋ － ＠`，跳过前置空白、控制符、不可见格式字符后判定；前置控制 / 格式字符也中和。逗号、分号、引号、CR / LF 都保持在引号单元格内，内部双引号继续加倍。

姓名、自定义事由和备注使用该策略；文本表头及方向 / 事件标签同样保护。金额列（测试含负值）和日期列不添加 TAB。普通中文、金额、日期、BOM、七列、普通 CSV 行结构保持原样；逗号或多行用户文本不能拆出新的公式列。所有输入为合成样例，没有真实联系人、礼金数据或带外部请求的攻击公式。

TAB 是导出字段底层值的一部分，程序读取时可能看到它；仅危险文本会改变，不回写数据库，也不改变备份数据。XLSX 保持显式 `inlineStr` 文本单元格、数值金额，不套用 CSV 的 TAB。不同表格软件、导入分隔符选择、另存再打开可能重新解释文本；本轮未运行 Excel / LibreOffice，不能宣称对所有软件或重写后的文件都已验收。

## 实际验证

采用项目已验证的 Windows Gradle 设置：`TEMP` / `TMP` 指向 `build/rc1-check-tmp`，进程内 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/AndroidProjects/LizhangApp/build/rc1-check-tmp`。未修改全局环境或 Gradle 配置。

执行命令：

```powershell
.\gradlew.bat --continue --no-daemon --console=plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' test lint assembleDebug assembleRelease assembleDebugAndroidTest
```

首次执行的 JVM 测试、lint、两种 App 打包都完成，但整体 **失败**，原因是额外的 `compileDebugAndroidTestKotlin` 在 `GiftSaveFlowInstrumentedTest.kt:68、:152` 报 `No value passed for parameter 'onBack'`。该调用来自原 Android 基线；页面后来新增可选尾部 `onCreateContact` 参数，旧尾随 lambda 不再绑定到必填 `onBack`。本轮仅修正两处测试调用。

修正后完整重跑同一命令，最终 **BUILD SUCCESSFUL，47 秒，145 项任务（6 执行、139 已更新）**。首次已执行并通过的 App / JVM 结果在最终运行复用，仪器测试重新编译及打包。没有以最初整体失败的运行宣称全部通过。

| 检查 | 实际结果 |
| --- | --- |
| `test` Debug | 26 个测试类，**167 / 167 通过**，0 失败、0 错误、0 跳过。 |
| `test` Release | 26 个测试类，**167 / 167 通过**，0 失败、0 错误、0 跳过。两种合计 **334 / 334**。 |
| 启动动画专项 | Debug / Release 各 11 项通过；原四项失败消除。 |
| CSV / 导出 / 恢复确认专项 | `CsvExportTest` 两种各 18 项通过，含本轮新增 7 项。 |
| 备份、安全与删除 JVM 回归 | `BackupArchiveCodecTest` 两种各 10 项，`SecurityConfigurationTest` 各 5 项，`ContactBulkManagementViewModelTest` 各 9 项，通过；不等同于真实 Room 事务测试。 |
| `lint` | 通过；Debug 完整报告 **0 错误、46 警告、1 提示**。默认任务检查 Debug 变体，独立 Release lint 结果见下方补充。 |
| `assembleDebug` | 成功，产物 `app/build/outputs/apk/debug/app-debug.apk`，APK v2 签名核验通过。未安装或运行。 |
| `assembleRelease` | 成功，产物 `app/build/outputs/apk/release/app-release-unsigned.apk`，仍未签名；签名核验按预期失败，不能直接安装或提交商店。 |
| `assembleDebugAndroidTest` | 修正旧调用后编译及打包成功；本轮未执行任何设备用例。 |
| Release 运行时依赖 | `:app:dependencies --configuration releaseRuntimeClasspath` 成功；用途与源集隔离见代码审查报告。 |
| `git diff --check` | 通过。 |

另外执行同样进程设置下的 `:app:lintRelease`，**BUILD SUCCESSFUL，1 分 36 秒**，Release 完整报告为 **0 错误、51 警告、1 提示**。

### Lint 余项清单

本轮 Debug 报告所有警告 / 提示均指向未修改的生产资源、配置或源码路径；没有将“有新版本”提示当成已发现漏洞。按类别记录全部 46 个警告与 1 个提示，原始报告保留具体位置：

| 类别 | 数量 | 处理分类 |
| --- | --- | --- |
| `GradleDependency` | 18 警告 | 依赖更新维护，可延期；单独评估升级而非本轮批量更新。 |
| `NewerVersionAvailable` | 3 警告 | 第三方依赖更新维护，可延期。 |
| `AndroidGradlePluginVersion` | 1 警告 | Gradle 工具版本更新维护，可延期。 |
| `UnusedResources` | 13 警告 | 资源整理，可延期；包括可能由系统调用的图标资源，不能仅凭警告直接删除。 |
| `UseKtx` | 6 警告 | API 写法整理，可延期。 |
| `ModifierParameter` | 1 警告 | Compose 参数约定整理，可延期。 |
| `ObsoleteSdkInt` | 1 警告 | minSDK 26 的资源目录整理，可延期。 |
| `PluralsCandidate` | 1 警告 | 英文数量文案需后续核对复数资源，不在本轮视觉 / 功能变更中扩展。 |
| `UnusedAttribute` | 1 警告 | API 33 才使用 `localeConfig` 的兼容提示，可延期。 |
| `ViewConstructor` | 1 警告 | 代码创建的 SnapshotOverlay 工具构造提示，可延期。 |
| `AutoboxingStateCreation` | 1 提示 | Compose 状态装箱优化，可延期。 |

Release 报告与 Debug 相比多 3 个 `UnusedResources`（共 16）和 2 个 `MonochromeLauncherIcon`。后者提示自适应普通 / 圆形图标未接入主题化 monochrome 层；记录为后续图标兼容整理，本轮遵守不改现有视觉设计的边界。Release 其余类别与上表数量一致；没有 lint error。

### 最终 Release 产物审查

- APK 读取版本为 `0.9.12 / 22`、minSDK 26、targetSDK 36，生成的 Release `BuildConfig.DEBUG=false`；清单没有 debuggable 标志。
- 合并后使用权限为 `READ_CONTACTS`、`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`，以及 AndroidX 的 `com.yangsong.lizhang.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（signature 自定义权限）。没有联网、写通讯录、广泛存储或精确闹钟权限。
- 唯一 Activity 为 MainActivity；不存在 GiftSaveTestActivity。依赖初始化 Provider 和 Room 服务均不公开；ProfileInstaller 的广播接收器虽 exported，但要求系统 `android.permission.DUMP`，不作为可公开调用的账本调试入口。
- 枚举三个 DEX 的 27647 个类定义，没有 `org.junit`、`androidx.test`、项目测试 fixture、测试 Activity / ViewModelTest / InstrumentedTest 类或完整 Compose tooling 实现；Preview 标注和生产教学演示不认定为误入的测试 Runner。APK 没有打包 `local.properties`。该检查范围是具体类定义与配置，不宣称对所有字节码完成安全认证。
- Release 为 22543016 字节，SHA-256 `0413ccaa45b0b5b433adb97cdf3a9eef433fa0dd58e4c0b2f2ff4e338284edff`。Debug SHA-256 `ae3f159aca737e0d35abfdc84ddb3458ade05722b9da663074d9e031a2705c30`。

### 证据位置

可重新构建生成的本机文件不提交到 Git：

- `build/rc1-check-20261010/validation.log`（首次失败）、`validation-final.log`（最终成功）、`lint-release.log`、`release-dependencies.log`。
- `build/rc1-check-20261010/unit-test-summary.json`、`release-apk-audit.json`。
- `app/build/test-results/testDebugUnitTest/`、`app/build/test-results/testReleaseUnitTest/`、对应 `app/build/reports/tests/` 报告。
- `app/build/reports/lint-results-debug.xml` / `.html`、Release 独立报告；后续构建可能覆盖这些输出，当前结果已在本页固化。

## 上架前余项与本轮边界

完整审查见 [Android 发布前代码审查](Android发布前代码审查第一轮.md)。本轮优先修复两个确定问题，其他事项按以下门槛处理：

| 分类 | 事项 |
| --- | --- |
| 真正上架前必须完成 | 审核批准完整隐私政策、真实运营者和联系渠道、生效日期；批准后再接入应用内离线正文 / 正式链接及多语言，不把草稿当正式文件。 |
| 真正上架前必须完成 | 用户确定正式签名与保管流程、发布版本 / 版本代码、商店权限及数据声明。当前 Release 未配置正式签名，不能直接提交商店。 |
| 后续验证 | Excel / LibreOffice 打开和另存 CSV 的兼容性；真实 Room 恢复事务 / 级联删除、系统通知及文件服务、实际启动和配置重建。按用户要求本轮不执行实机验证。 |
| 后续测试基准修订 | 现有 `StartupRealFrameInstrumentedTest` 仍以应用名笔画像素识别品牌帧，与已确定的无文字 Overlay 不完全一致；应在设备验收前修订采样基准，不能将本轮编译结果算作新版画面通过。 |
| 可延期维护 | 间接依赖许可清单、R8 / 预览源集及体积优化，历史基础清单中“通知直达详情”更新为当前独立提醒列表跳转。 |

不自动合并 main、不提交商店、不配置正式政策、不签名发布、不安装或运行 App。本轮未连接生产服务器、修改 DNS 或开放公网服务。
