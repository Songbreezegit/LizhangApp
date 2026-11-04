# 礼账官网

[礼账官网](https://lizhang.songisle.xyz/)提供产品介绍、Android 下载、隐私说明、用户协议和使用帮助。网站由 Node.js 脚本生成，沿用现有 ECS / Nginx 静态部署。

## 页面与内容来源

| 页面 | 内容来源 |
| --- | --- |
| `/`、`/download/` | `scripts/build.mjs` 与 `site.config.json` |
| `/privacy/`、`/terms/`、`/help/` | Android 的 `app/src/main/assets/legal/` |
| 隐私页官网访问说明 | `content/privacy-website.md` 与对应配置 |
| `/contact/` | 共享法律元数据中的联系方式 |

发行信息集中维护在 `site.config.json` 的 `release` 中，包括版本、文件名、APK 和发行记录链接、大小、SHA-256 及最低 Android 版本。`scripts/release.mjs` 校验这些字段的一致性。

首页和下载页的资源链接带网站内容摘要参数，用于更新浏览器缓存。下载页使用一个同源脚本复制校验值，其余页面保持原有脚本策略。

## 本地预览

从仓库根目录运行：

```powershell
node website/scripts/build.mjs --review
node website/scripts/preview.mjs 4177
```

打开 `http://127.0.0.1:4177/`。预览文件位于 `website/dist/`。

## 生产构建与检查

```powershell
node website/scripts/build.mjs --production
node --test website/scripts/legal-finalization.test.mjs website/scripts/public-download.test.mjs
```

法律一致性测试同时读取 Android 已生成的离线正文。首次准备环境时，可通过 `:app:generateOfflineLegalAssets` 任务生成这些文件。

构建会核对法律批准摘要、网站输入摘要、版本、备案及网站访问说明。生产文件生成到 `website/dist-production/`，共 12 个公开静态文件；构建记录另存于 `build/website-production-*/`。

## 发布与回滚

发布使用 `website/dist-production/`。通过既有连接将校验后的文件放入新的版本目录，保留旧版，再原子切换 `/var/www/lizhang-privacy/current`。发布后核对公网文件、响应头及实际 APK 下载。

网站 ICP 备案号为 **渝ICP备2026025309号-1**；APP 备案号来自独立的 `release/app-filing.properties`。

- [v1.0.0 公开发行及下载入口交付](../docs/v1.0.0-public-release-handover.md)
- [下载页生产批准](docs/下载页生产批准-v1.0.0-20261104.json)
- [资源缓存修订批准](docs/下载页缓存更新生产批准-v1.0.0-20261104.json)
- [当前部署回执](docs/下载页部署回执-v1.0.0-20261104.json)
- [既有部署结构](deploy/README.md)
- [历史说明](docs/archive/README-2026-11-04.md)
