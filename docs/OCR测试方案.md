# OCR 测试方案

## 自动测试

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

自动测试不使用真实令牌和真实礼簿图片。MockWebServer 覆盖：

- 创建任务及 `pending → running → done`；
- `failed`、401、403、429；
- 有限 5xx 重试；
- 超时和协程取消；
- 结果下载失败和 JSON 字段缺失；
- PP-OCR 与 VL 结果映射。

纯 Kotlin 测试覆盖质量评估、VL/ML Kit 切换、空 Token、姓名金额日期解析、千分位、小数、分列、候选编辑、导入确认和疑似明文令牌扫描。

## 手动测试矩阵

覆盖正向清晰、四方向旋转、透视倾斜、暗光、阴影、浅色笔迹、有线表格、横向记录、纵向分列、模糊及过大图片。

每次检查：预处理没有裁字、阶段提示正确、降级不会崩溃、候选可编辑、未确认前 Room 无变化。

## Release 安全检查

1. `local.properties` 不被 Git 跟踪。
2. `assembleRelease` 成功。
3. Release Token 为空、云端开关关闭。
4. 以本地令牌值扫描 Release APK，结果为零。
5. 日志不出现 Authorization、完整图片或姓名金额明细。

## 已知限制

- 复杂背景可能无法找出页面四边形，此时会安全回退完整画面。
- VL Markdown 没有稳定文字框时使用合成行坐标，仍需人工校对。
- Debug 直连只用于验证，正式发布前必须迁移到后端代理或端侧推理。
