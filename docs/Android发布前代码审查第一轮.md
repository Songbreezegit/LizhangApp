# Android 发布前代码审查：第一轮

审查日期：2026-10-10。Android 开发基线为 `codex/figma-guide-nav-startup` 的 `1ee3ea9e58a2dfb336ba3fca774dd3cf5ab674bc`，收尾分支为 `release/v1.0.0-rc1`。当前 `versionName=0.9.12`、`versionCode=22`，本轮不升级到 1.0.0。

本报告记录权限、依赖配置、发布隔离、隐私说明及关键数据路径的源码审查。完整 `test`、`lint`、`assembleDebug`、`assembleRelease` 的实际执行数量、最终产物权限与签名检查见本轮测试报告。本报告不把已有测试文件、源码检查或历史设备结果视为本轮实机验证，也不表示已经通过法律或应用商店审核。

## 1. 必须处理的事项

| 事项 | 分类与本轮处理边界 | 依据与完成条件 |
| --- | --- | --- |
| 启动动画测试仍沿用 880 ms 预期 | 本轮已修复，保留已确定的 900 ms 动画 | `StartupAnimationViewModel` 与 `StartupAnimationViewModelTest`；半程、末帧、重建及旧窗口回调回归在 Debug、Release 各 11 项通过。 |
| CSV 公式注入 | 本轮输出防护与回归已完成，桌面兼容验收待后续 | `domain/export/GiftRecordCsvFormatter.kt`；危险文本在引号内添加 TAB，CSV / 导出测试两种各 18 项通过；数据库和备份仍保存原值。软件直接打开和另存后重开未实测，不能宣称所有导入器均已验收。 |
| 仪器测试保存页面调用漂移 | 本轮编译发现并修复 | `GiftSaveFlowInstrumentedTest.kt:68、:152` 旧尾随 lambda 缺失必填 `onBack`；只改测试为具名参数，重新编译测试 APK 成功，不修改生产页面。 |
| 正式政策尚未批准或接入 | 真正提交商店前必须完成，等待运营者审核；本轮只列后续工作 | `ui/screen/InformationScreens.kt:78` 显示的是简短隐私说明，`res/values/strings.xml:194` 至 `205` 未包含实际运营者、联系方式、生效日期、完整提醒与第三方文件服务说明。不能将未批准的草稿直接替换为正式政策。 |
| 正式发布签名与版本批准 | 真正提交商店前必须完成，独立于本轮技术构建 | `app/build.gradle.kts:10` 至 `34` 没有配置正式 `signingConfig`，当前版本仍为 0.9.12。本轮 `assembleRelease` 不能等同于已生成可上架的正式签名安装包；后续由用户确定证书保管、签名流程、1.0.0 版本代码及商店材料。不得提交密钥或密码。 |

本次审查没有发现需要在上述范围外立即修改数据库结构、备份格式或 UI 视觉的确定缺陷。最终产物检查或测试若发现新阻断项，应补充到本轮测试报告。

## 2. 已核对的权限与组件

| 权限或行为 | 源码依据 | 结论及限制 |
| --- | --- | --- |
| 读取通讯录 | `app/src/main/AndroidManifest.xml:3`；`data/contact/DeviceContactDataSource.kt:12`；`ui/screen/ContactImportScreen.kt:75` | 用户主动进入导入流程后，按授权状态只读查询姓名、号码、系统联系人标识；不是先上传联系人再筛选。读取有电话号码的整个候选列表，在内存预览；确认后保存选择结果。 |
| 通讯录权限撤回 | `ui/viewmodel/ContactImportViewModel.kt:54`、`:81` | 未授权时取消加载和筛选任务，重置预览；`SecurityException` 转换为拒绝状态。既有已导入联系人没有自动删除。这是源码结论，未验证各厂商授权界面。 |
| 通知权限 | `app/src/main/AndroidManifest.xml:4`；`ui/screen/LedgerBrowseScreens.kt:202` 至 `227`；`data/reminder/AndroidReminderRepository.kt:154` | Android 13 及以上，在用户开启提醒时解释并申请，接收广播时再次检查通知权限；拒绝不影响账本 CRUD。 |
| 重启等系统事件重排 | `app/src/main/AndroidManifest.xml:5`、`:39`；`data/reminder/AndroidReminderRepository.kt:176` | 接收重启、应用更新、时间、时区及日期变化，白名单校验事件。两个提醒广播接收器均 `exported=false`；不是可被外部任意调用的公开数据接口。 |
| 文件读写 | `ui/screen/SettingsScreen.kt:133` 至 `177`、`:666` | 使用系统 `CreateDocument` / `OpenDocument` 读写用户选定 URI，输入流读取有大小上限；源码未扫描整个存储。第三方文件提供方仍可能在用户选定云盘位置时执行自己的上传行为。 |
| 系统自动备份与换机迁移 | `app/src/main/AndroidManifest.xml:9` 至 `11`；`app/src/main/res/xml/data_extraction_rules.xml:2` | 主清单 `allowBackup=false`、`fullBackupContent=false`，提取规则排除云备份与设备迁移中的数据库、偏好、文件等域。本轮未执行 OEM 换机测试，不能保证任何第三方工具都无法复制数据。 |
| 主 Activity 与本地跳转 | `app/src/main/AndroidManifest.xml:24`；`MainActivity.kt:174` | 主 Activity 是启动器而公开；提醒跳转处理不包含导出、恢复或删除外部调用入口。源码未声明对外文件 Provider 或云服务。 |

主源码清单显式声明 `READ_CONTACTS`、`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`，没有互联网、写通讯录、广泛存储、相机、麦克风、定位或精确闹钟权限。最终 Release 的合并清单必须单独核对依赖带入的权限，不能只以主源码清单作最终结论。

提醒使用 `AlarmManager.setAndAllowWhileIdle`（`data/reminder/AndroidReminderRepository.kt:115`），没有申请精确闹钟权限。当前承诺应是本地日期提醒，实际送达受系统调度、电量及通知设置影响，不能宣称精确到点送达。

## 3. 第三方依赖与 Release 隔离

`app/build.gradle.kts:53` 至 `71` 的显式运行时依赖为 AndroidX / Jetpack Compose、Room、Kotlin 协程以及 Haze 1.6.4，用于本地 UI、数据库和异步工作。源码与依赖未发现广告、统计、崩溃上报、账号登录、联网请求、OCR、图片上传或云同步 SDK。主执行者已成功生成 `releaseRuntimeClasspath` 依赖树（本地证据 `build/rc1-check-20261010/release-dependencies.log`），只读复核未发现 JUnit、`androidx.test` 或完整 `ui-tooling` 实现作为 Release 运行时依赖；`ui-tooling-preview` / `ui-tooling-preview-android` 1.8.3 保留用于预览标注。该检查不等于对所有间接依赖做漏洞或许可证认证；最终产物复核以本轮测试报告为准。

| 检查项 | 依据 | 结论 |
| --- | --- | --- |
| JUnit 与协程测试 | `app/build.gradle.kts:73`、`:74` | 使用 `testImplementation`，未挪至 `implementation`。 |
| Android 仪器测试 / Room 测试 / UIAutomator | `app/build.gradle.kts:75` 至 `81` | 使用 `androidTestImplementation`；源码位于 `app/src/androidTest`，本轮完整 JVM 单元测试不等同于执行了这些 Room 或设备测试。 |
| 测试 Activity | `app/src/debug/AndroidManifest.xml:5`；`app/src/debug/java/com/yangsong/lizhang/GiftSaveTestActivity.kt` | 调试源集中的 `GiftSaveTestActivity` 不应进入 Release。最终 Release 清单和 DEX 仍须核查。 |
| Compose UI 工具 / UI 测试清单 | `app/build.gradle.kts:82`、`:83` | 使用 `debugImplementation`；截图验证依赖使用 `screenshotTestImplementation`。未见将这些源集合并到 main 的配置。 |
| Preview 标注及语义 testTag | `app/build.gradle.kts:66`；`ui/component/UiPreviews.kt`；各 UI 组件 | 主源码内存在预览函数、`testTag` 和诊断访问辅助，不等于测试 Runner 或可操作私有账本的调试页面进入 Release。未发现由用户入口启用 JUnit / UIAutomator 的代码。可在后续体积优化中考虑源集收敛。 |
| 引导练习仓储 | `data/onboarding/GuidePracticeStore.kt:32` 至 `38`、`:73`、`:85`、`:228` | 为正式产品的教学演示，不是误入 Release 的测试数据库；独立内存仓储只接受本会话负数标识，不持有 Room、文件或真实仓储，拒绝通讯录导入，结束清空。 |
| Release 缩减配置 | `app/build.gradle.kts:10` 至 `34` | 未显式开启 R8 / 资源缩减。它不是当前公式注入修复的替代手段，也未发现因此出现可直接操作私有数据的调试入口；后续如开启须单独测试反射、序列化及资源，不宜在本轮顺带变更。 |

## 4. 日志与临时画面

在 `app/src/main/java` 与 `app/src/debug/java` 搜索 `Log.*`、`println`、`printStackTrace`，当前主源码日志调用集中于 `core/common/ThemeOperationDiagnostics.kt:30`。启用方法将记录开关设为 `BuildConfig.DEBUG`（`:19`），写日志前检查开关（`:22`）；Release 默认不会写该诊断日志。记录内容为主题状态、请求与窗口标识、操作阶段、触摸位置和尺寸，未发现联系人姓名、号码、礼金金额、备份明文或密码作为日志参数。

`MainActivity.kt:55` 的触摸诊断以 `BuildConfig.DEBUG` 为门槛；启动动画诊断默认也以 `BuildConfig.DEBUG` 控制（`ui/viewmodel/StartupAnimationViewModel.kt:13`）。相关诊断类仍在 main 源码中，不应报告为完全不存在于 Release 的字节码；本轮产物检查应确认最终 `BuildConfig.DEBUG=false`，且测试宿主没有进入 Release。

主题 / 语言过渡通过窗口 PixelCopy 在内存临时捕获画面（`ui/component/AppearanceTransition.kt:322`），未找到写入截图文件或网络发送代码；旧宿主回调和捕获失败路径回收 Bitmap（`:338` 至 `350`），动画结束调用状态清理（`:407`），宿主销毁取消监听和动画（`:435`）。这种临时画面处理应在正式政策中如实说明，不能简单写成“从不处理画面”。本轮未复测设备窗口切换和低内存行为。

## 5. 备份、恢复及删除关键路径

### 备份格式与安全边界

- Room 数据库版本仍为 2（`data/local/LiZhangDatabase.kt:11`），`1 → 2` 迁移仅增加可空自定义事由字段（`:25`），创建数据库显式注册该迁移，没有使用破坏性重建兜底（`data/di/AppContainer.kt:35` 至 `39`）。
- 普通备份格式仍为 3，保持 v1 / v2 读取兼容；包含联系人和礼金字段（`domain/backup/BackupArchiveCodec.kt:30`、`:48` 至 `66`、`:113` 至 `134`）。SHA-256 检查是损坏检测，不是加密或可信来源证明。
- 解码验证魔数、长度、摘要、版本、尾部数据、记录数量、字符串大小、重复主键、外键及正金额（`domain/backup/BackupArchiveCodec.kt:85` 至 `162`）。
- 密码备份仍为封装版本 1，使用随机 16 字节盐、12 字节 IV、PBKDF2-HMAC-SHA256 210000 次、AES-256-GCM / 128 位认证标签，对头部使用 AAD（`domain/backup/BackupEncryptionCodec.kt:31` 至 `63`）。密码不写入偏好或文件；临时密码及恢复待确认状态在内存存在，不承诺不可变字符串的取证级擦除。
- 普通备份、CSV、XLSX 以及本机 Room 数据库没有自动变成密码加密文件；用户选择加密备份仅保护该份备份。无需为本轮修复更改数据库或旧备份格式。

### 恢复与联系人删除

| 关键行为 | 源码依据 | 审查结论 |
| --- | --- | --- |
| 一致的导出备份快照 | `data/repository/RoomBackupRepository.kt:20` 至 `34` | 在同一 Room 事务读取联系人和礼金，编码 / 可选加密后生成备份。 |
| 恢复前确认 | `ui/viewmodel/SettingsViewModel.kt:133` 至 `181`、`:201` 至 `228`；`ui/screen/SettingsScreen.kt:315` | 解密 / 校验摘要后保存待恢复状态；用户确认后调用替换恢复，取消清除待确认引用。UI 明示会替换账本，已有 JVM 测试检查需确认后才能调用恢复。 |
| 恢复不会先删除后才检查文件 | `data/repository/RoomBackupRepository.kt:42` 至 `60` | 在事务开始前完成解密与解码验证；删除旧礼金、删除旧联系人、插入备份联系人、插入礼金处于单一事务。数据库写入失败应回滚。 |
| 删除联系人会删除关联礼金 | `data/local/entity/GiftRecordEntity.kt:12` 至 `17`；`data/local/dao/ContactDao.kt:94` 至 `100` | 既有外键 CASCADE；批量删除在事务中重新比对影响摘要，有变化返回 Changed 要求重新确认，按 900 个标识分片，仍在同一事务。 |
| 不删除系统通讯录或独立提醒 | `data/repository/RoomContactRepository.kt:33` 至 `38`；`data/reminder/AndroidReminderRepository.kt:34` 至 `44` | 联系人删除只操作本地 Room。独立提醒在应用私有 SharedPreferences，与礼金 / 联系人没有自动关联。 |
| 备份范围 | `data/repository/RoomBackupRepository.kt:21` 至 `27`；`res/values/strings.xml:324` | 当前只备份联系人和礼金，不包含独立提醒、主题、语言、调度偏好及引导进度；现有提醒页已有不在备份中的提示。 |

已有 `BackupArchiveCodecTest` 覆盖字段往返、旧格式、错误密码、篡改和非法关联；`CsvExportTest` 覆盖恢复确认。Room 的真实事务恢复、级联删除及失败回滚案例位于 `app/src/androidTest/java/com/yangsong/lizhang/data/repository/RoomBackupRepositoryInstrumentedTest.kt`、`ContactBulkDeleteRepositoryInstrumentedTest.kt` 等。它们本轮没有执行；即使全部 JVM 测试通过，也不能报告为实机数据库验收通过。

## 6. 隐私说明一致性与后续接入

当前应用简短说明在 `res/values/strings.xml:195` 至 `205`，与目前核心行为一致：无账号 / 广告 / 统计 / 云同步 / 图片识别，不申请联网权限；本机存储、主动通讯录导入和文件选择、可选密码备份、卸载不清理已导出文件。本轮未替换这些资源或新增草稿入口。

现有说明较短，正式政策需要补齐以下事实与事项，不能直接把“无联网”扩写成“所有外部提供行为均不存在”：

1. 确认真实开发者 / 运营者、可用联系渠道、生效日期、政策更新及权利请求处理方式、未成年人服务安排。未知内容保持待确认，获得审核批准后再接入。
2. 明确独立提醒标题及日期会提交给 Android 通知系统，可能出现在通知栏、锁屏或获通知读取权限的应用中；说明设置、提醒在 SharedPreferences 保存，提醒不包含于账本备份。
3. 明确导入预览会读候选姓名、完整号码和联系人标识；号码遮罩不等于从未处理完整号码。只读、不保存系统标识、不写通讯录、撤回权限后停止读取但不自动删除已导入数据。
4. 明确删除礼账联系人会级联删除关联礼金，不会删除手机通讯录或独立提醒；恢复为替换，不是增量合并；本机数据库与未加密导出需要设备和文件访问保护。
5. 说明用户选择第三方文件服务可能由该服务同步文件，礼账通过系统 URI 读写；同时说明临时窗口画面处理及 Debug 诊断边界。
6. 在用户批准政策后，按商店要求确定应用内可离线查看的正式版本、政策链接、告知 / 确认方式及多语言一致性，再运行相应构建与资源测试。文档站备案 / DNS / 公网批准与本轮分开，本轮不连接服务器或上线网站。
7. 上架前复核最终签名 APK 的合并清单、Release 依赖、业务数据流，以及商店填写的数据和权限声明；不得沿用较旧版本的说明而不核对安装包。

为比较事实，本轮只读检查了 `origin/codex/lizhang-docs-site-ecs:website/content/privacy.md`。该文件仍带“审核稿 · 尚未生效”，且列明运营者、联系渠道等待确认；它不能作为已批准的正式政策。本轮未修改网站或 ECS 文件，也未更改未跟踪的 `docs/隐私政策.md`。

## 7. 可延期及验证边界

| 事项 | 优先级与处理建议 |
| --- | --- |
| 全部间接依赖的漏洞和许可证清单 | 后续维护项；当前审查限于显式用途与发布隔离。应独立维护解析后的依赖与授权说明，不据当前版本号推断有已知漏洞。 |
| R8 / 资源缩减及预览源集收敛 | 可延期的体积与构建整理项；未发现与本轮两个确定缺陷有关的可执行调试页面，无需为此扩大本轮改动。 |
| 历史验收文档提醒描述 | `docs/基础功能验收清单.md:23` 仍写“通知直达详情”，与当前独立提醒列表跳转不同，后续更新验收文档；发布声明以最终功能审查及正式批准文案为准。 |
| 实机 / OEM 行为 | 按本轮要求未验证真实动画播放、窗口与主题切换、通知实际送达、文件服务、换机迁移、Excel / LibreOffice 直接打开或另存 CSV。列作后续设备 / 桌面兼容验收，不阻止本轮按源码和自动化测试交付技术修复。 |
| 正式发布操作 | 不自动合并 main、不提交商店、不切换正式版本、不签发生产服务、不变更 DNS。等待用户审核技术修复和后续发布方案。 |
