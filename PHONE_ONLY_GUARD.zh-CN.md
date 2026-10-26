# 手机不打断 Mac：单向传输挂起验证版（Test 9）

> 后续实机证据：USB 诊断时，Test 9 在识别状态正常的一次会话中，已由用户确认“Mac 不被手机视频打断、视频继续无声播放”和“Mac 暂停后手机自动从耳机恢复声音”。这说明传输路径有效，但 Test 9 的初始化和连接稳定性问题仍然存在。Test 11 保留该功能并针对已记录的初始化错误修订，见 `PHONE_GUARD_TEST11.zh-CN.md`；以下为历史版本记录。

> 已撤下：用户实测反复断联和恢复连接，无法正常使用。Test 9 不符合验收要求，请停用。具体设备端根因尚未取得日志证实；代码中的路由变化可能引发挂起/恢复反复切换。Test 10 已退回 Test 8 运行逻辑，并提供一次性遗留状态恢复，见 `RECOVERY_TEST10.zh-CN.md`。单向防抢占需求仍未实现。

版本 `1.0.4-phone-guard-test9`，版本代码 `100040309`。这是新路径的实机验证版，不能将本地构建成功或单元测试通过理解为防抢占已经有效。

## 这次明确的范围

只改安卓：Mac 播放期间，手机的视频、音乐、通知音不能抢夺耳机；手机内容继续播放但不从扬声器出声；Mac 明确停播后，允许正在等待的手机媒体接管。保留手机与耳机的媒体连接，不设置固定两秒等待。无需阻止 Mac 接管手机，不安装 Mac 配套程序。

本轮验证的是 A2DP 媒体传输，电话的 HFP/SCO 通话通道不在这个实验开关的拦截范围内。

## 安装和开启

1. 覆盖安装 `LibrePods-v1.0.4-phone-guard-test9.apk`，无需卸载或清数据。
2. 保留此前已经生效的 KernelSU、LSPosed 和系统模块环境；本轮不用重新刷 Root ZIP。
3. 打开 LibrePods，在应用设置开启 **手机不打断其他设备（实验）**。默认关闭，旧版实验开关不会自动迁移到此功能。
4. 若 KernelSU 弹出 LibrePods 的 Root 请求，允许它。新路径通过 Root 启动独立的小进程调用系统接口，未把代码注入 system_server。
5. 让 Mac 连续播放，先查看下方状态。需要显示另一台正在播放、媒体连接为 2、媒体输出为 AirPods、系统挂起 external=true/applied=true 且没有错误。之后再播放手机视频验证。若前提显示“未保护”或接口报错，不能认为保护已启用成功。
6. Mac 停播后保持手机视频继续播放，检查耳机声音能否恢复。若没有恢复，关闭此实验开关退出。停止服务/辅助进程心跳过期也会尝试恢复保存的挂起状态。

如果仍有抢占、手机视频不走进度、扬声器出声、或 Mac 停播后手机无法恢复，请复制状态卡即可；本轮无需再次运行挂载检查或重刷权限模块。

## 与旧方案的实际差别

- 旧双向状态机仍固定关闭。本次使用独立的单向状态机，远端开始播放始终覆盖此前的手机放行状态；手机播放回调不会被当作远端停播。
- 不调用旧的 connection policy=0 路径，不断开 A2DP/HFP，不把手机媒体音量设为零，也不把扬声器静音当成实现。
- 使用 Android 17 的 `IAudioService.setA2dpSuspended` 外部挂起位。该接口需要 `BLUETOOTH_STACK`，已有的 `BLUETOOTH_PRIVILEGED` 并不足够，所以在独立 Root 进程中调用。
- 挂起前检查实际 A2DP 连接及媒体路由，保留耳机的控制会话、状态回报和模式控制。接到 Mac 让出请求时不再通过主动断开媒体连接响应。
- 读取系统音频服务中的 external/internal/applied 三个位，区分请求与系统应用结果。系统已应用仍不能证明一加 HAL/耳机实际没有抢占，也不能证明硬件内部没有切换到其他通路。
- 首次修改前保存原始外部挂起值；关闭、服务退出及辅助进程心跳停止后恢复。异常退出保留恢复记录，下一次启动重试；没有恢复记录时不擅自清除其他组件原有的挂起请求。仅在等待保护期间保持 CPU 唤醒以维持恢复心跳。

## 仍需目标机证明的条件

1. Mac 播放状态必须被识别。若只收到不明来源的 NONE，不能据此宣称 Mac 已停播。
2. 一加音频 HAL 必须遵守挂起请求且不回落到扬声器，同时手机视频继续走进度。
3. 挂起期间手机通知、音乐和视频都不能导致 Mac 暂停或让耳机切走。
4. Mac 的停播回报需要可用，释放挂起后等待的手机媒体能接管。

这四点未完成实测。任何一点失败都应报告为不兼容或待修复，不能仅以“系统挂起已确认”宣称完整需求通过。原来假连接提示和快捷设置三模式循环的独立修订保留。

## 官方接口依据

- Android 17 AudioManager：`setA2dpSuspended` 标注 `BLUETOOTH_STACK`。
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/media/java/android/media/AudioManager.java
- Android 17 AudioService：权限检查后调用 `mDeviceBroker.setA2dpSuspended(enable, false, eventSource)`。
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/audio/AudioService.java
- Android 17 AudioDeviceBroker：独立维护外部/内部挂起位并向 HAL 应用 `A2dpSuspended`。
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/audio/AudioDeviceBroker.java

上游 README 和许可证保持原样。没有修改 Mac 设置、安装 Mac 驱动或运行 Mac 音频控制程序。
