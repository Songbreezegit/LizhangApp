# AI Studio PaddleOCR 调试配置

## 临时验证

在项目根目录、已被 Git 忽略的 `local.properties` 增加：

```properties
AISTUDIO_ACCESS_TOKEN=请填写临时测试令牌
```

不要把真实令牌写入源码、资源、JSON fixture、README、截图或日志，也不要提交 `local.properties`。

Debug 编译会把令牌注入 `BuildConfig`，所以令牌会进入 Debug APK。仅在受控设备测试，验证结束后必须在 AI Studio 废止并重新生成令牌。

Release 编译始终使用空字符串，并将云端开关设为 `false`，即使本机仍有令牌也不会注入 Release。

## 手动验证

1. 先使用普通无敏感信息图片验证协议。
2. 启动 Debug App，进入“拍照识别礼簿”。
3. 选择图片并阅读上传说明。
4. 选择“使用云端识别”。
5. 观察预处理、上传、PP-OCRv6 和等待结果状态。
6. 主结果质量不足时观察 PaddleOCR-VL 二次识别。
7. 检查候选可编辑；不要导入协议测试数据。

异步流程：

```text
POST /api/v2/ocr/jobs
→ data.jobId
→ GET /api/v2/ocr/jobs/{jobId}
→ pending/running/done/failed
→ data.resultUrl.jsonUrl
→ 下载 JSONL
```

脱敏参考代码位于 `docs/paddleocr_api_sample.py`。

## 降级调试

- 删除令牌：直接进入 ML Kit。
- 上传确认中选择“使用离线识别”：不发起云端请求。
- Mock 429 或 API code 12002：显示限流提示并进入 ML Kit。
- Mock 403 且 code 12001：按每日额度耗尽处理。
- 断网或任务超时：提示云端不可用并进入 ML Kit。
- 返回低质量 PP-OCRv6 fixture：触发 PaddleOCR-VL。

AI Studio 的免费额度、模型名和接口规则可能调整，应以官方当前文档和实际脱敏响应为准。
