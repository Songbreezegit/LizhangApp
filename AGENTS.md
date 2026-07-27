# 礼账项目协作约定

- 项目内界面、文档、注释和用户提示统一使用中文。
- 保持 `ui → ViewModel → domain.repository ← data` 分层，Compose 与 ViewModel 不直接发起网络请求。
- 当前产品不包含 OCR、图片上传或云端识别功能；未经新的产品决策不得恢复相关入口与依赖。
- 禁止记录或提交用户联系人、礼金金额及备份文件中的隐私数据。
- 每次主要修改至少运行 `test`、`lint`、`assembleDebug`；发布相关改动还要运行 `assembleRelease`。
- 不修改 Room 表结构、现有页面逻辑或视觉设计，除非当前任务明确要求。
- 完成主要里程碑后同步更新 `README.md` 和 `docs/`。
