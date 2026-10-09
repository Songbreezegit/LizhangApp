# RC2 仪器测试前置与现有引导契约迁移

本轮仪器测试只编译，不在设备或模拟器执行。以下说明不能作为设备播放、首次告知、语言重建或几何效果已通过的证据。

## 隐私前置

`AcceptedPrivacyRule` 仅存在于 `androidTest`，用于明确已经同意后的业务、外观和启动动画测试。外层规则 `order=0` 在 Compose 规则 `order=1` 启动 Activity 之前同步保存当前版本确认；不请求通讯录或通知权限，不消费功能引导，不调整 900ms 动画或采样断言。

使用该前置的 17 组测试：BasicLedgerFlow、ContactImportFlow、ReminderNavigation、IndependentReminder、IndependentNotification、FeatureGuideUi、NavigationPageMotion、AppearanceFailure、AppearanceTransition、ExpandedLanguage、LocalePersistence、NativeTransitionRecording、StartupAnimation、StartupRealFrame、StartupWindowGate、Localization、TransitionVisual。

OnboardingEntry、FeatureGuideEntry、OnboardingLocale、OnboardingUpgrade、OnboardingLegacyUpgrade 的验收阶段以及 privacy 目录的测试不得使用该规则。首次安装类必须按类在独立全新测试安装中执行，不能与已经写入同意记录的业务测试共用安装状态。升级类先断言没有当前政策确认记录、旧功能引导状态保留且数据库及提醒未初始化，再通过真实告知界面明确确认后检验旧数据。

两阶段升级建样应使用对应旧版本源码构建的应用与测试包，随后覆盖安装 RC2 应用及测试包执行 `legacyUpgradePhase=check`。不能把依赖新增隐私接口的 RC2 测试包直接装在尚无该接口的 RC1 应用上执行建样。当前版本的 `setup` 分支也需明确确认，只用于隔离的合成测试数据。

## FeatureGuideUi 的 10 项契约迁移

RC1 已验收行为是五步真实记账介绍，以及每页独立的首次提示。旧四步串行介绍、可见进度数字、三角箭头和设置重播入口均不是当前产品契约。迁移只修改测试，不修改生产引导或视觉。

| 旧测试目的 | 当前等价或加强覆盖 |
| --- | --- |
| 完整推进与结束 | 五步依次在首页及真实记账页推进；完成不会自动保存，也不会消费未访问页面提示。 |
| 真实入口可穿透 | 点击真实记一笔进入联系人步骤；返回首页保留进度，再进入继续原步骤。 |
| 高亮与连接图形实际绘制 | 保留视觉锚点、点击区域、中心对齐及底栏安全断言；通过实际帧检查两个圆泡中心、外侧及中间空隙，替代已经移除的三角箭头像素坐标。 |
| 联系人导航与暂停 | 联系人页的首次提示只消费联系人页；真实菜单进入手动新建并返回，保存记账进度和权限历史。 |
| 提醒与我的导航 | 两页独立提示，原提醒设置和权限请求历史不变，没有自动权限弹窗，首页记账介绍继续存在。 |
| 跳过与持久化 | 跳过记账后新仓库保持完成；联系人首次提示仍可独立跳过并持久化，返回首页不重播。 |
| 其他控件正常操作 | 搜索入口按正常导航工作；搜索首次提示独立消费，不推进记账步骤。 |
| 目标离开视口与恢复 | 当前真实记账金额控件滚出视口时暂停气泡，滚回恢复，进度不变并保留保存栏安全范围。 |
| 法语窄屏及大字号 | 320×640dp、1.5 倍字号、深色提醒页首次测量检查真实目标、云体、按钮及屏幕边界。 |
| 多语言、主题和方向边界 | 七种语言、深浅色、LTR/RTL、1.5 倍字号检查五个记账步骤与联系人、提醒、我的三页提示；保存栏、底栏、目标和按钮安全断言按当前所在页面适用。 |

当前进度属于气泡的无障碍 `SemanticsProperties.StateDescription`。测试据此检查 `1 / 5`、`3 / 5` 或 `1 / 1`，没有恢复产品已移除的可见数字节点。FeatureGuideEntry 同样检查当前第三步状态描述、七语言重建、文档返回和重新启动后的进度保留。

几何绘制测试使用实际测量的锚点、猫咪和气泡范围，结合现有圆云几何计算确定采样点，再检查实际截图像素；只有坐标计算正确不足以通过该用例。设备运行后才会写入 `guide-rc2/` 的截图，本轮未生成这些设备证据。
