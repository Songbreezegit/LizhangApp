# OCR 架构说明

## 处理链路

```text
图片 Uri
→ OpenCV 预处理
→ AI Studio PP-OCRv6
→ 多因素质量评估
→ 必要时 PaddleOCR-VL-1.6
→ 云端不可用时 ML Kit
→ 可编辑校对
→ Room 单事务导入
```

Debug 为快速验证识别效果而临时直连 AI Studio。正式版本必须迁移到后端代理或经过评估的端侧模型，不能在可分发 APK 中保存长期凭据。

## 分层

- `data/image`：读取 Uri、EXIF 修正、缩放、页面检测、透视矫正、灰度增强和临时文件清理。
- `data/remote/paddle`：Retrofit API、异步任务轮询、有限重试、JSONL 下载和 DTO 映射。
- `data/ocr`：PP-OCRv6、PaddleOCR-VL、ML Kit 与 Room 导入实现。
- `domain/ocr`：统一文字框、识别引擎、降级原因、礼簿解析器和质量评估。
- `ui/viewmodel`：协调处理阶段、候选合并、重复检测和用户确认。
- `ui/screen`：图片选择、云端上传授权、进度和降级提示、结果编辑。

云端原始 JSON 不进入 UI 或领域层。`PaddleOcrResultMapper` 将 PP-OCRv6 的
`ocrResults[].prunedResult.rec_texts/rec_scores/rec_boxes` 以及 VL 的
`layoutParsingResults[].markdown.text` 转换为 `OcrTextLine`。

## OpenCV 预处理

1. 采样读取大图，最长边限制为 2800 像素。
2. 按 EXIF 修正旋转或镜像。
3. 通过边缘和轮廓面积寻找可靠四边形；可靠时透视矫正。
4. 检测失败时保留完整画面，不裁掉文字。
5. 灰度化、双边降噪、CLAHE 局部对比度增强和温和去阴影。
6. 不做强二值化，避免浅色手写笔画丢失。
7. 结果写入 `cacheDir/ocr_preprocessed`，识别结束后删除。

Debug 可查看预处理返回的尺寸、`perspectiveCorrected`、`usedFallback` 和警告；中间图片不会永久写入相册。

## 模型选择

以下信号会从主模型触发 VL：

- 没有有效文字或没有解析记录；
- 姓名和金额只有一类；
- 配对比例低；
- 低置信度或空白结果比例高；
- 记录数量与文字行数量明显不匹配；
- 金额异常；
- PP-OCRv6 任务失败或结果结构异常。

若两个模型候选不一致，候选都会进入校对列表并标记“重点核对”，不会静默覆盖。

未配置令牌、用户选择离线、断网、401/403、429、任务超时、有限 5xx 重试失败、任务失败或 JSON 异常时使用 ML Kit。降级只改变识别引擎，不跳过校对，不直接写库。

## 安全边界

- Debug Token 由 `local.properties` 注入 `BuildConfig`。
- Release 的 Token 固定为空且云端开关为 `false`。
- 网络层没有 Header/Body 日志拦截器。
- 错误只暴露类别和中文提示，不包含令牌、完整请求或业务数据。
- 用户离开页面时 ViewModel 协程取消，上传和轮询随之取消。
