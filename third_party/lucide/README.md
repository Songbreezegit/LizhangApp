# Lucide 图标来源与许可

礼账的功能图标采用 [Lucide 官方项目](https://github.com/lucide-icons/lucide) 的 SVG 轮廓，固定源版本为 `70562c1ee1c4fdcf736fe97bc893fb8511927934`。

- 图标路径保存在 `app/src/main/java/com/yangsong/lizhang/ui/component/LiZhangIcons.kt`，使用本地 Compose `ImageVector` 绘制。
- 保留官方圆角端点、圆角连接和 24 × 24 坐标，统一显示线宽为 1.8；需要跟随布局方向的操作箭头继续自动镜像。
- 未增加图标网络请求或运行时图标依赖。
- 完整 ISC 与 Feather 衍生图标的 MIT 许可保留在本目录 `LICENSE`，同一许可通过 `app/src/main/assets/licenses/lucide-LICENSE.txt` 随 APK 分发。
