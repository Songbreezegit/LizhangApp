# RC2 文案与代码核对

本轮为候选审核稿接入，未完成运营者内容批准，不代表法务审核或应用商店审核通过。日期：2026 年 10 月 10 日。

## 来源与边界

- 代码基线：RC1 `b6adf6ba516ed280bb1c7882b78cfa36e4449b1a`，0.9.12／22。
- 文档来源：`origin/codex/lizhang-docs-site-ecs` 的 `website/content/privacy.md`、`terms.md`、`help.md`，源提交 `9ae64127643baf091fa46faad6da0840fc89c136`。使用 `git show` 读取，未合并文档站分支，未使用工作区既有未跟踪 `website/`。
- RC2 完整正文：`app/src/main/assets/legal/{privacy,terms,help}.md`。候选内容标识 `1.0.0-rc2-policy-v1`，由 `LegalPolicy.CURRENT_VERSION` 与 `metadata.properties` 对应。标识用于再次告知识别，正式政策版本仍待本人确认。
- 离线正文通过 `LegalDocumentRepository ← AssetLegalDocumentRepository` 读取，由 `LegalDocumentViewModel` 提供状态，Compose 不读取业务数据库或联网。
- 本轮未操作 ECS、DNS、备案、线上文档站、应用商店或 main。

## RC1 与 RC2 对照

| 说明 | 实际代码依据 | 核对结果与文案处理 |
| --- | --- | --- |
| 处理联系人与礼金字段 | `ContactEntity`、`GiftRecordEntity` | 姓名、可选电话、关系、备注、创建时间，以及礼金方向、事由、日期、金额和关联；正文列出用途和保存位置。 |
| 本地保存，未接入业务上传 | 主 Manifest、`AppContainer`、显式运行时依赖 | 无 INTERNET 权限；Room 未配置应用数据库加密。正文明确区分本地数据库、普通备份与加密备份。 |
| 通讯录候选不只读取最终勾选项 | `DeviceContactDataSource`、`ContactImportViewModel`、`ContactImportRules` | 查询有电话的候选姓名、完整号码、系统标识，用于内存预览与去重；确认后保存选中姓名与规范化号码。未写系统通讯录。拒绝／撤回会清空未授权预览，不删除已导入联系人。 |
| 可选权限在功能入口申请 | `ContactImportScreen`、`NotificationsScreen`、`PermissionPolicy` | 保留入口用途说明；拒绝通讯录可手动创建联系人；通知拒绝不会影响记账、备份与导出。首次隐私告知不自行申请权限。 |
| 独立提醒与锁屏 | `AndroidReminderRepository`、通知接收器／调度器 | 独立提醒存偏好文件，保存礼金不会生成提醒；系统通知标题与日期可能在锁屏出现。没有精确闹钟权限，不承诺准点送达。 |
| 备份密码与恢复范围 | `BackupEncryptionCodec`、`RoomBackupRepository`、`BackupArchiveCodec` | PBKDF2-HMAC-SHA256／AES-256-GCM，至少 8 字符；不保存密码，无找回。只备份联系人与礼金，事务内替换恢复，不恢复独立提醒、主题、语言、设置或引导。 |
| CSV／Excel 与普通备份 | `GiftRecordCsvFormatter`、`GiftRecordXlsxFormatter`、`SettingsScreen` | 文件未用备份密码加密；系统文件选择器由用户选定位置，可选第三方云盘。保留已验收 CSV 防护。 |
| 删除与系统备份 | `GiftRecordEntity` 的 `ForeignKey.CASCADE`、`ContactDao`、主 Manifest、`data_extraction_rules.xml` | 删除联系人级联删除关联礼金；不删除独立提醒、系统通讯录或外部文件。关闭系统自动备份并排除迁移域，但最终系统行为仍需设备验收。 |
| 主题过渡与诊断 | `AppearanceTransitionHost`、`ThemeOperationDiagnostics` | 内存窗口图像用于外观过渡，无图片上传；诊断由 BuildConfig.DEBUG 控制，内容不含联系人、金额或备份正文。本轮不改 900ms 动画。 |
| 首次告知与重大政策更新 | RC2 `PrivacyConsentRepository`／首次 gate | 旧 Onboarding 状态不等于同意；主动确认的内容版本单独持久化。未确认前可读文档，可拒绝并返回或退出；不删除账本或原引导状态。详见 RC2 验收报告。 |
| 文档站与浏览器 | 固定官方网站链接与 `Intent.ACTION_VIEW` | App 未增加 INTERNET；仅主动点击时打开系统浏览器。未核验线上状态，正文去掉对当前部署状态的断言，仅描述参考分支模板与待批准事项。 |

## 文案修正与统一语言方案

去掉来源稿中“尚未替换应用内隐私说明”“尚未实现协议同意流程”等已过时说法，保留 RC1 数据处理依据并补充 RC2 告知、内容版本、离线页面与回退说明。帮助文档不暴露当前未实现的“联系开发者”页面或离线不存在的“常见问题”入口。

简体中文、繁体中文、英语、日语、韩语、西班牙语、法语界面统一读取三份完整简体中文候选正文。每种语言均有完整本地化页面标题、草案状态、回退提示、读取失败提示和浏览器说明；不将正文回退为摘要。完整正文的其他语言译文尚未审核，本轮没有宣称交付七语言法律全文。正式发布时，开发者必须确认此回退方案的适用安排，或补齐经批准的译文并重新核对批准摘要。

页面使用现有 `AppScaffold`、`AppTopBar` 和玻璃卡片。段落可选择复制；Markdown 表格展开为可换行文本，以适配系统字号。页面不嵌入 WebView、不要求联网，不新增首页入口。

## 待本人确认与正式发布阻断

运营者姓名或主体、有效联系邮箱与地址、生效日期、正式政策与协议版本、网站备案号、未成年人及儿童信息安排、个人信息权利受理程序及答复安排、服务支持、收费策略、软件许可／第三方署名、法律与争议条款、网站日志信息范围和保留删除安排均待确认。资料不得编造，候选 ID 不代替正式生效资料。

`metadata.properties` 明确保持“待本人确认／待批准”。正式发布构建需通过内容批准检查，批准范围应包括完整三正文、全部首次告知和回退翻译资源；正文或翻译改变后摘要必须失效并重新批准。编译成功和版本号升级不构成内容批准。签名身份另按发布操作说明由本人确认。

## 自动化验证范围

新增 JVM 测试检查关键说明未丢失、表格与链接转文本不漏信息、空正文失败、七语言资源键与占位符一致、候选元数据、固定官网链接和无 INTERNET 权限；ViewModel 测试检查加载、错误／重试及重复请求。新增 Android instrumentation 测试覆盖打包正文读取、隐私末尾说明与返回、“我的”三入口、大字号深浅色切换以及七语言完整正文一致性。

测试执行结果与实际数量统一由根任务在最终验收报告填写。本轮不要求实机验收；仅编译 instrumentation 安装包不视为设备测试通过，未运行的设备项目必须保留为未验收。
