# 礼账 v1.0.0 RC3 自动化验证报告

日期：2026-10-10。分支：`release/v1.0.0-rc3`。基线：`d0a71a893ce662a44ab7b8a9e9f3f174e51bad42`。最终完整交付 SHA 见交付回复及 `git rev-parse HEAD`；本报告不将代码通过等同于法律、设备、签名或商店验收。

## 整合与保护范围

远端 RC2 最新 SHA 与上述已验收提交相同。独立 RC3 工作树整合文档站 `9a0fde4789ecb2226b0a32e889eeab65dd5414fa`、`9ae64127643baf091fa46faad6da0840fc89c136`；唯一冲突是 README 的追加里程碑，双方内容均保留。原目录保持 RC2 分支及原有 README 草稿、旧隐私草稿、网站产物和附件，没有重置或暂存这些文件。

对 RC2 差异核验：Room 实体、DAO、schema、账本仓库、联系人/礼金/提醒功能、备份编解码、导出实现、900ms 动画 ViewModel 与覆盖层、Manifest 均无生产差异。MainActivity 仅改隐私 ViewModel 注入和 Gate 参数。隐私偏好仍独立保存版本，不包含数据库清空或引导重置路径。

实际 JVM 回归包含每种变体的 CSV 18 项、备份编解码 10 项、启动动画 ViewModel 11 项、启动会话 3 项和帧诊断 4 项，均通过。它们支持 RC1 防护与逻辑未回归的结论；实体设备升级数据保留、900ms 实际播放及文件/通知行为本轮未执行。

## Android 全任务验证

构建使用 JDK 17、SDK 36、项目内 `build/rc3-check/tmp` 作为 TEMP/TMP 和 UnixDomainSocket 临时目录，进程内 Kotlin、单工作线程；未修改全局环境。

```powershell
.\gradlew.bat --continue --no-daemon --console=plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' test lint lintRelease assembleDebug assembleRelease assembleDebugAndroidTest :app:printLegalContentDigest
```

| 检查 | 实际结果 |
| --- | --- |
| `test` Debug | 32 类、198/198，通过；0 失败、0 错误、0 跳过。 |
| `test` Release | 32 类、198/198，通过；0 失败、0 错误、0 跳过。两种合计 396，独立用例数 198。 |
| `lint` | Debug 0 错误、46 警告、1 提示。 |
| `lintRelease` | Release 0 错误、51 警告、1 提示。 |
| `assembleDebug` | 成功，`1.0.0-rc3 / 23`，调试 v2 签名有效。 |
| `assembleRelease` | 成功，`1.0.0-rc3 / 23` 未签名候选。 |
| `assembleDebugAndroidTest` | 仪器源码编译及独立测试 APK 打包成功；设备执行 0 项。 |
| Release 依赖 | 解析成功；未新增依赖。没有 JUnit、AndroidX Test、Espresso 或调试宿主进入 Release 运行时；既有 `ui-tooling-preview` 为预览注解依赖。 |
| `git diff --check` | 通过；没有降级 lint、启用 baseline 或删断言以获得通过。 |

首轮实际 **BUILD FAILED，7 分 28 秒，146 个执行任务**：两种 JVM 测试通过、lint 无错误、两个候选 App APK 已产生，仪器 Kotlin 编译因 `LegalDocumentInstrumentedTest` 的 `assertDoesNotExist` 错误导入失败。已去掉多余导入，保留该成员断言。不能把这轮写成全任务成功，原日志留存 `gradle-required-first.log`。

加入 APK 内容绑定、修正导入后的完整轮次 **BUILD SUCCESSFUL，3 分 14 秒，154 个任务（29 执行、125 已更新）**；之后补齐门禁域名和法语 RC3 标记校验，再在全部源码冻结后复跑同一完整任务链，**BUILD SUCCESSFUL，52 秒，154 个任务（5 执行、149 已更新）**，日志为 `gradle-final-complete.log`。单元源码没有再变化，最终链复用 Gradle 已更新的单测结果；没有声称重新执行了设备测试。

现有警告主要为依赖版本维护建议、剩余无引用资源、Compose/KTX API 建议和既有图标/兼容性建议。没有为了清零而升级依赖或改变视觉；完整项目 lint XML/HTML 保留在 `app/build/reports/`，分类汇总在 `build/rc3-check/lint-summary.json`。

## 网站与一致性校验

- 完整仓库 `npm test` **93/93 通过，0 跳过**；候选 `npm run build` 成功生成 12 个文件。
- 单独复制最终 `website/`，没有 Android 工程或 SDK，运行同一测试：**92 通过、1 跳过、0 失败**；唯一跳过项是明确需要真实 Android 仓库的对照测试。独立候选构建也生成 12 个文件。
- Android 构建不执行 Node 或 npm；Gradle 自行校验随仓库保存的完整正文、元数据、批准镜像与内容摘要。网站自带正文和摘要，可独立构建。
- 三份 App 全文与网站完全镜像；任一 App 资源、全文、元数据、批准记录或镜像漂移时失败。FAQ/contact 或网站配置变化后，旧网站批准摘要也失效。
- 网站合成正式通过/拒绝覆盖草稿、主体、政策版本、无效日期、备案状态、版本边界、分发历史、APP 适用材料、两类摘要及七语言未审词。合成 fixture 只在临时目录，不替代真实批准。
- 真实 `--public` **退出 1，按预期拒绝**，真实配置和正文仍保持未批准，没有产生或部署正式站点。

最终摘要经 Gradle、PowerShell、Node 交叉核对：

```text
Android 25 个法律输入：
b803fc0f34e996f26dc802e794c51abba5297860e1807794d9e1716a9ea4d337
网站 6 个正文/配置输入：
c0c375acee05407a5723ef02468d590131970bbb09f15f9ccbd998255fabfc0c
```

Git 法律输入强制 LF；实际文件也已规范，避免 Windows 与 Linux 检出产生不同批准摘要。候选专用两条提示各七语言共 **14/14** 被共享规则识别；专用候选资源仍纳入摘要，只在正式正文扫描时排除，不能放过 assets 与正式告知中的草稿。25 输入中的 4 份完整正文/元数据仍有真实待批准标记，正式内容扫描明确拒绝。

## 正式发布门禁

使用同一生产 `release/legal-release.gradle` 在独立、无 Android 插件的 `build/` 合成工程执行 **23/23 成功与拒绝用例**。包含合成完整批准通过、主体/未成年人未确认、错误邮箱日期、版本未递增/与命令不一致、签名指纹不完整、候选编号、中文/英文/法语草稿、全文/语言缺失、批准后内容变化、网站镜像/摘要/域名偏移等。合成批准文件不覆盖生产资料，不生成正式 APK。

真实工程执行：

```powershell
.\gradlew.bat --no-daemon --console=plain '-PofficialRelease=true' '-PofficialVersionCode=24' assembleRelease
```

结果 **退出 1，25 秒，因真实 `approval_status` 未获本人确认而拒绝**。正式构建前后候选 Release SHA-256 相同；没有生成正式 `1.0.0` APK。随后重新执行默认候选完整任务链，恢复候选构建状态。

待批准资料包括本人运营信息、法律全文/日期/版本、中文回退方案、网站备案、独立 APP 备案/单机适用材料、分发历史与正式代码、正式签名证书。仓库文件名范围未找到正式密钥，没有扫描私人目录、读取私钥或密码、创建正式密钥或真正签名。

## 最终 APK 静态检查

具体 Release APK 经 `scripts/verify-rc3-release.ps1` 通过：包名、版本、SDK 范围、不可调试、Manifest/DEX 无测试和调试宿主、固定权限、4 份完整离线资产与源码字节一致、6 字段编译内容绑定、16KiB 页对齐及签名状态符合候选。实际资源表另由本机 aapt2 导出，包含默认/显式简体中文、繁体中文及英/日/韩/西/法资源。首次资源导出用了本机不支持的 `--values` 选项，按工具帮助改为默认输出后成功。

实际权限仅：`READ_CONTACTS`、`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED` 以及 AndroidX 签名级 `com.yangsong.lizhang.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。没有 INTERNET、WRITE_CONTACTS、testOnly、仪器入口或密钥/本机配置资产。生成的 Release `BuildConfig.DEBUG=false`、`IS_OFFICIAL_RELEASE=false`。

签名脚本的签前路径还要求正式版本/内容、干净已提交源码、本人批准证书、仓库外既有密钥与输出目录；拒绝调试别名、已有签名材料和旧 APK。对齐后签名，再做证书/v2/对齐/资源绑定验证，保存两份 JSON。其真正证书交互与正式签名成功路径本轮没有执行；当前只是操作方案，不能称正式签名验收通过。

| 产物 | 字节数 | SHA-256 | 边界 |
| --- | ---: | --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 29791997 | `4f75840cd1a4fb5d7e2b2d46acfc509bffc3c42ecb8bd30b679fedbb069c3674` | 调试 v2 签名，非正式身份；未安装运行。 |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 22602127 | `fcf28a2d041a3823e6fd8aecaa2fb841a62a18bd82d8e6611340aa174a8985ec` | 未签名候选，签名核验按预期不通过，不能直接提交商店。 |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 1765547 | `1919c5263044cce0b8e4574e01b7bacbacc8b5ed9c341f7322638989ae62c0e0` | 仪器包编译打包通过，设备执行 0 项。 |

密钥/密码/本机配置忽略规则与 Git 跟踪文件名核验通过；实际跟踪文件没有 `.jks/.keystore/.p12/.pfx/.pem/.key`、私有签名属性或 `local.properties`。没有输出或提交用户联系人、礼金及备份数据。

## 证据与交付边界

完整本机证据在独立工作树 `build/rc3-check/`，JUnit/lint 在 `app/build/`；均被 Git 忽略，不上传二进制或合成数据。关键文件：

- `gradle-required-first.log`、`gradle-final.log`、`gradle-final-complete.log`。
- `junit-summary.json`、`lint-summary.json`、`release-apk-check.json`、`apk-artifacts.json`。
- `official-release-expected-rejection.log`、`official-rejection-summary.json`、`gate-regression-final23.log` 与最新 gate fixture 的 `gate-results.json`。
- `website-*-final.log`、`website-standalone-location-final.txt`。
- `release-manifest.xml`、`release-resources.txt`、`debug-signature.txt`。

本轮没有实机/模拟器/仪器执行、商店截图采集、正式证书验收、覆盖安装或线上链接验收。没有操作服务器、DNS、ICP备案、Cloudflare 主站或现有线上站点；没有合并 main、发布正式 Release 或提交商店。代码与自动化候选检查完成，开发者待批准事项见 [正式发布清单](v1.0.0正式发布清单.md)，完成后等待审查。
