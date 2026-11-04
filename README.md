# 礼账

礼账是一款记录人情礼金往来的 Android 应用。记下收礼与送礼，整理联系人和重要日期，数据保存在本机，无需注册账号。

[下载 Android 版](https://lizhang.songisle.xyz/download/) · [GitHub 发行记录](https://github.com/Songbreezegit/LizhangApp/releases/latest) · [官方网站](https://lizhang.songisle.xyz/)

## 下载与安装

当前版本为 **v1.0.0**，支持 **Android 8.0 及以上**。

在官网下载页选择「下载 Android 版」，下载后按照系统提示安装。安装包的版本、大小和 SHA-256 校验值可在下载页或 GitHub 发行记录中查看。

## 主要功能

- **往来记账**：记录收礼、送礼，关联联系人、事由、日期和备注。
- **联系人管理**：手动添加或从通讯录选择导入，查看历史往来。
- **日历与统计**：按日期查找记录，查看年度、月份、事由和联系人汇总。
- **日期提醒**：创建一次性或每年重复的本地提醒。
- **导出与备份**：导出 Excel、CSV，创建普通或密码加密备份。
- **语言与外观**：支持简体中文、繁体中文、英语、日语、韩语、西班牙语和法语，以及浅色、深色模式和系统字号。

## 数据与迁移

在「我的 → 数据备份与恢复」中创建 `.lizhangbackup` 文件，可用于迁移账本。备份包含联系人和礼金记录，提醒及应用设置需另行设置。恢复会替换现有账本；加密备份需要原密码。

Excel、CSV 用于查看和整理记录。迁移时请使用礼账备份文件。

<details>
<summary>从开发测试版迁移</summary>

开发阶段的 Debug 版与正式版签名不同，不能直接覆盖安装。先在旧版中备份账本并保存文件，再卸载旧版、安装正式版，最后恢复账本。

</details>

## 隐私与反馈

通讯录在主动导入时读取，通知权限在开启提醒时按需申请。完整说明可在 App 内离线阅读，也可通过官网查看。

[隐私说明](https://lizhang.songisle.xyz/privacy/) · [用户协议](https://lizhang.songisle.xyz/terms/) · [使用帮助](https://lizhang.songisle.xyz/help/)

联系邮箱：[3173887065@qq.com](mailto:3173887065@qq.com)

APP 备案号：渝ICP备2026025309号-2A

## 本地开发

使用 Android Studio 打开仓库，配置 JDK 17 和 Android SDK 36。项目采用 Kotlin、Jetpack Compose、Room 与 StateFlow，按 `ui → ViewModel → domain.repository ← data` 分层。

在 Windows PowerShell 中构建 Debug 包：

```powershell
.\gradlew.bat :app:assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。相关检查命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

协作要求见 [AGENTS.md](AGENTS.md)，官网构建方式见 [官网说明](website/README.md)。

发行与验证记录见 [v1.0.0 公开发行交付](docs/v1.0.0-public-release-handover.md)，早期开发记录归档于 [历史说明](docs/archive/README-2026-11-04.md)。
