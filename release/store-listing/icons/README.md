# 礼账商店图标派生素材

文件：[lizhang-store-icon-512.png](lizhang-store-icon-512.png)。规格：512×512、RGB、PNG、不透明方形；背景沿用 `#FFF8F1`。这是商店派生素材，不替换 Android 图标资源，也不是设备截图或实机视觉验收结果。

## 原始资源与比例

- 猫咪红包原图：`app/src/main/res/drawable-nodpi/launcher_cat.png`，1254×1254 RGBA。
- 自适应图标声明：`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`。
- 前景：`app/src/main/res/drawable/ic_launcher_foreground.xml`，对原图画布应用 `18%` 内缩。
- 背景：`app/src/main/res/values/launcher_colors.xml` 中的 `launcher_background`。

导出保留整个原图画布，不重新裁切猫咪或改变 Logo、颜色。为保持自适应图标的前景比例，按 Android `AdaptiveIconDrawable` 的子图层每边扩展 25% 后，再应用资源声明的 18% 内缩，并取方形可见视口；采用 4 倍尺寸绘制后缩小到 512。

没有另加圆角、圆形蒙版、阴影、文字或宣传元素。实际桌面图标会受厂商蒙版、主题和系统渲染影响，因此本素材不声称与任意设备显示逐像素相同。它沿用同一原图、背景和资源比例；各商店是否接受该尺寸/方形形式，仍待渠道核实。

## 导出记录

[export-record.json](export-record.json) 保存来源提交、四个输入文件的 SHA-256、原图尺寸、背景及内缩参数、输出规格和输出 SHA-256。所有路径均为仓库相对路径，没有本机密钥路径或用户数据。

导出脚本为 [export-store-icon.ps1](export-store-icon.ps1)，使用 Windows `System.Drawing`，不需要 Gradle、模拟器、设备或第三方网络服务。在仓库根目录执行：

```powershell
.\release\store-listing\icons\export-store-icon.ps1
```

只写本目录中的派生 PNG 和导出记录，导出前后核对四个原资源摘要。资源链或内缩值变化时先静态复核，不自动套用旧参数。其他尺寸仅在本人确认对应渠道要求后另行导出，本轮只准备 512×512。

## 参考

- [Android 自适应图标：前景、背景及厂商蒙版](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Android AdaptiveIconDrawable API：子图层扩展范围](https://developer.android.com/reference/android/graphics/drawable/AdaptiveIconDrawable#getExtraInsetFraction())
- [AOSP 实现：可见视口与子图层边界](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/graphics/java/android/graphics/drawable/AdaptiveIconDrawable.java)
