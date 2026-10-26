# 保持双端连接的音频互斥：验收条件与可行性边界

> 范围更正（Test 9）：用户现已明确，只要求安卓手机不打断 Mac；不要求安卓阻止 Mac 接管手机，也不修改 Mac。下文“双向必须成立”及“停止开发”记录的是此前误解的范围，已被这次澄清取代。新的单向传输挂起实验见 `PHONE_ONLY_GUARD.zh-CN.md`。它尚未通过目标手机实测，不能视为已实现可靠防抢占。

## 用户已明确的要求

1. 安卓手机和 Mac 均保持与 AirPods 的蓝牙媒体连接；手机不切换为扬声器输出。
2. 先播放的一端保留耳机，另一端开始音乐/视频/音效不能强制转移音源。这一规则必须双向成立。
3. 未获使用权的手机内容继续走进度，但不产生耳机声音或扬声器声音。
4. 当前播放方确认停播后，等待方仍在播放的内容可以自动恢复声音；不要求暂停等待方再重新播放，没有固定两秒等待。
5. 耳机在盒内或连接未成功时，播放手机视频不能触发虚假的“已连接”浮窗。

这里的保持连接包含实际媒体连接和输出设备状态，不能仅保留 AACP 控制连接、断开 A2DP 后宣称已满足。

## Test 6 的偏差

Test 6 的 PriorityAudioRouteGate 仍将手机 A2DP/HFP connection policy 设为 0，并验证 disconnected；PriorityMediaSilencer 依赖手机蓝牙媒体退出后进行全局媒体静音。这会使手机音频回落到本机输出，不满足第 1 条。

手机先播放、Mac 后发声时，现有实现仅在安卓侧忽略部分让出请求，不证明已拦住 Mac 或耳机自身的切换。因此没有满足第 2 条的实机证据。

82 项单元测试只验证了该实现的内部逻辑，不能证明满足这份需求，更不能替代两端实机验证。本轮不再把这些通过数当作双向防抢占已完成的证据。

## 已核对的接口

### 安卓：挂起传输与断开连接不同

Android 17 AudioDeviceBroker 有独立的 A2DP 挂起状态，并向 HAL 发送 A2dpSuspended=true/false；这是一条值得验证的传输层方向。

但公开的高通参考 HAL 中，部分 offload 挂起分支会临时选择静音的 speaker 硬件通路。该参考代码不能证明一加 15 / Android 17 使用相同行为，也不能证明在目标机上挂起后仍保持所有输出条件。因此不能仅调用 setParameters 并收到成功，就宣称满足需求。

目标机测试必须同时读取：媒体连接状态、媒体输出设备、挂起前后手机播放是否继续、Mac 是否被打断，以及恢复后的状态。任一条件失败就应回退，而不是静默切回“断开 + 扬声器静音”的实现。

### Mac：应用参与仲裁不等于全局否决其他应用

Apple AVAudioRoutingArbiter 提供应用参与/退出音频路由仲裁的接口；公开文档没有给出一个可由安卓应用调用、全局禁止 Mac 其他应用抢占的接口。现有安卓模块的“拒绝让出”不能替代这项能力。

Apple 的“连接到此 Mac → 上次连接到此 Mac 时”可以作为关闭原生自动切换的对照设置，但不是“检测另一端是否停播后再自动切换”的完整仲裁器，不能直接用来替代用户需求。

如果采用 Mac 配套程序，仍必须先验证它能控制需要控制的应用音频请求，且不会通过断开耳机或回落扬声器违反连接条件。运行配套程序本身不构成可行性证明。用户已明确只接受修改安卓；若无法仅靠安卓可靠实现则放弃。Mac 配套程序不在范围内。本轮没有安装系统音频驱动或更改 Mac 音频设置。

## 后续实现门槛

- 先通过一个“连接仍保持、输出仍是 AirPods、非主人传输被抑制”的单向最小实机实验。
- 证明手机先播、Mac 后播也能保持手机音频；不能只验证 Mac 先播这一方向。
- 两项通过后再实现持续播放状态协调、自动释放、异常恢复和用户开关。
- 如果接口受系统或固件限制，应明确报告限制，不继续交付断开连接的替代方案。

## 来源

- Android 17 AudioDeviceBroker：
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/audio/AudioDeviceBroker.java
- 高通参考 HAL 的挂起/恢复处理（不等于目标手机实现）：
  https://android.googlesource.com/platform/hardware/qcom/audio/+/refs/tags/android-14.0.0_r57/hal/audio_hw.c
- Apple 路由仲裁接口：
  https://developer.apple.com/documentation/avfaudio/avaudioroutingarbiter
- Apple 自动切换设置：
  https://support.apple.com/guide/airpods/switch-airpods-between-apple-devices-dev228ba3df8/web

## 当前决定

在只修改安卓的约束下，目前没有经过目标机验证、能够可靠满足全部条件的实现；这不构成所有安卓/固件实现均不可能的证明。按用户的范围限制，停止该实验开发。

connection-fixes-test7 撤下开关并强制停用实验行为，保留旧版本保存状态的恢复路径，以及独立的连接生命周期、音源包校验和假连接浮窗修订。该版本恢复正常音频行为，不提供双向互斥；系统/耳机原有自动切换仍可能发生。
