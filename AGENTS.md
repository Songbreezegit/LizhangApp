# 礼账项目协作约定

- 项目内界面、文档、注释和用户提示统一使用中文。
- 保持 `ui → ViewModel → domain.repository ← data` 分层，Compose 与 ViewModel 不直接发起网络请求。
- OCR 结果必须进入可编辑校对页；未经用户确认不得写入 Room。
- `AISTUDIO_ACCESS_TOKEN` 只能位于未提交的根目录 `local.properties`。
- Debug 允许临时直连 AI Studio；Release 必须禁用云端 OCR 且令牌固定为空。
- 禁止记录令牌、Authorization、完整请求头、完整礼簿图片、完整姓名及金额。
- OCR 临时图片放入 `cacheDir`，任务完成或取消后及时清理。
- 每次主要修改至少运行 `test`、`lint`、`assembleDebug`；发布相关改动还要运行 `assembleRelease`。
- 不修改 Room 表结构、现有页面逻辑或视觉设计，除非当前任务明确要求。
- 完成主要里程碑后同步更新 `README.md` 和 `docs/`。
