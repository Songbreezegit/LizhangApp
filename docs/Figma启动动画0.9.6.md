# Figma 启动动画（0.9.6）

> 本文记录第一版带名称启动稿与历史打包。用户随后从 Figma 稿去掉“礼账”文字，最新 App 同步为无文字稿，且已完善窗口超时与清理；最新引导、导航动效和试装包见 [Figma 引导与导航及启动交接](Figma引导与导航及启动交接0.9.6.md)。以下名称轨道、旧窗口交接与 APK 哈希不代表最新交付。

日期：2026-10-08。版本：`0.9.6 / 16`。工作分支：`codex/figma-startup-animation`。

用户在完成纯图标导航后，要求使用 Figma 制作启动动画，并明确选择“动画稿完成后接入 App 并打包”。本次保留纯图标导航和既有页面行为，以新稿替换原先停用的品牌开屏。

## 设计稿与素材

[Figma 可编辑文件](https://www.figma.com/design/4BlDvZZVFb0moftVwiux3f)，页面为“启动动画”。包含浅色、深色两张 390 × 844 时间线画板和中文动效说明。

| 主题 | 时间线画板 | 品牌覆盖层 | 小猫 | 名称 | 柔光环 |
| --- | --- | --- | --- | --- | --- |
| 浅色 | `3:4` | `3:16` | `3:19` | `3:20` | `6:2` |
| 深色 | `3:55` | `3:67` | `3:70` | `3:71` | `6:12` |

唯一图片为项目现有 `app/src/main/res/drawable-nodpi/launcher_cat.png`，以完整透明 PNG、FIT 模式导入 Figma；文件大小 1629358 字节，Figma 图像哈希为 `73c9a578f0166fa172e662fb41a87583c622b01b`。App 直接复用同一资源，不新增网络图片、视频播放或动画依赖。背景、文字、圆角柔光环与关键帧均可编辑；未把整屏截图作为设计内容导入。

Figma 底层的空首页只用于演示覆盖层向真实内容交接，使用固定零值，不包含用户数据。App 仍复用现有首页或首次使用引导，没有新增示意页面。浅色底、文字和强调色为 `#F5F5F2 / #292E35 / #A44625`；深色为 `#1E2227 / #F3F1ED / #FFB38C`，对应项目现有主题色。

## 权威关键帧与 Compose 映射

静态结构取自两个覆盖层的 `get_design_context`，动画取自 `get_motion_context` 的 900ms 时间线，按节点 ID 对齐。没有使用旧 880ms 参数代替新稿。

| 图层 | 时间与数值 | 曲线 |
| --- | --- | --- |
| 小猫缩放 | 0ms 为 `.76`；300ms 为 `1.04`；480ms 为 `1`，随后保持 | 前段 `(0,0,.2,1)`；收敛段 `(.4,0,.2,1)` |
| 小猫竖向位移 | 0ms 为 `0dp`；300ms 为 `-8dp`；480ms 为 `-6dp` | 与缩放同步 |
| 名称透明度与位移 | 120ms 前透明、下移 8dp；120–300ms 淡入并回到 0dp | `(.4,0,.2,1)` |
| 柔光环 | 0–620ms 从 112dp 放大至 208dp，透明度从 1 降至 0 | `(0,0,.2,1)` |
| 品牌覆盖层 | 620ms 前完整可见；620–900ms 淡出 | `(.4,0,.2,1)` |

两条曲线分别对应现有 Compose `LinearOutSlowInEasing` 和 `FastOutSlowInEasing`。所有图层共享 `StartupAnimationViewModel` 的单一进度，唯一总时长改为 `900ms`。Figma 编辑器时间线用于循环预览；App 复用现有启动资格与生命周期控制，只在普通进程冷启动播放一次。

猫与柔光环独立锚定屏幕中心，图标名义尺寸为 112dp。名称顶部在屏幕中心下方 76dp，字号/行高为 26sp/34sp、Medium，沿用 Android 系统字体与本地化应用名；字体放大时名称向下扩展，不移动猫中心。Figma 使用 Noto Sans SC Medium，设备具体字形由系统字体提供。

柔光环基准填充透明度为 `.05`，内描边为 `1.6dp / .18`；整个图层按 Figma 的 SCALE 轨道放大，因此描边也随缩放变化。品牌组合不剪裁轻弹后的猫图；App 采用原生图层变换和圆形背景/边框，不添加 SVG 或远程资源。

## 启动接入边界

`MainActivity` 恢复现有 `StartupSession.claim`、`StartupWindowGate` 和品牌覆盖层接入。等待窗口实际提交与系统启动层退出后开始；通知直达、系统状态恢复、同进程热返回不补播，配置重建沿用当前进度。退后台、返回键或收到通知入口时结束覆盖；系统关闭动画时跳过。下层页面同步准备，品牌结束后恢复触摸、无障碍和功能引导。

没有修改 Room、备份格式、提醒规则、联系人数据、权限时机、导航路由或主题/语言切换实现。此前纯图标导航的图标居中、无障碍名称和功能引导锚点保留。

## 设计预览与打包

Figma 浅色画板导出 MP4，并以 10fps 抽取开始、入场、最高点、收敛、淡出和结束画面，已查看这些关键帧，猫未裁切，名称与光环顺序符合时间线，结束后显示承接页面。此检查仅针对 Figma 稿，不能替代 App 真机流畅度验收。

用户此前要求不做测试、自行试装，本轮继续只执行 APK 构建，不运行单元测试、模拟器测试、截图比较或独立 lint；Release 构建的必要检查由 Gradle 自动执行。

Debug 与 Release 构建均成功：`BUILD SUCCESSFUL in 1m 31s`。Debug APK 已核对 `0.9.6 / 16`、最低 API 26、目标 API 36 及 v2 签名，可直接安装；Release 产物未签名，不能直接安装。

交付目录：`build/deliveries/2026-10-08-figma-startup/`。

| 文件 | 用途 | SHA-256 |
| --- | --- | --- |
| `lizhang-0.9.6-figma-startup-debug.apk` | 最新动画试装包 | `6e074571147173fc4e228aa98de6c61478fb6cba283c9da787df1523eaf503de` |
| `lizhang-0.9.6-figma-startup-release-unsigned.apk` | 未签名 Release | `e322a370dd3cdcf0abf7f0b3dd5c9d2cfcd44c0df31c69eac4c2dfbf2c473bfd` |
| `figma-startup-light.mp4` | Figma 设计预览 | — |
| `frame-*.png` | 设计关键帧取样 | — |
| `figma-source-context.json` | 静态设计与权威 motion context 的本地记录 | — |
| `build.log`、`delivery.json` | 构建、版本、分支、基准提交与产物归属 | — |

构建复用项目已有 JBR、项目内临时目录及本次关闭 Kotlin 增量编译的设置；未改全局环境或 Gradle 项目配置。命令为：

```powershell
.\gradlew.bat --no-daemon --max-workers=1 '-Pkotlin.incremental=false' :app:assembleDebug :app:assembleRelease
```

修改保存在 `codex/figma-startup-animation` 工作区，基准提交为 `e07b883`，未提交或推送。原有 README 隐私草稿及 `docs/隐私政策.md` 保留；最终设备验收由用户自行完成。
