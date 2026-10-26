# 毫秒定时点击器（TimeTap）

Android 原生 Java 项目，使用系统无障碍服务在指定屏幕坐标发送一次 **100ms 点击手势**。项目不需要 root，也不包含广告、账号或第三方统计 SDK。

## 功能

- 使用可拖动的悬浮红点选取坐标：开启点选模式后可切换到目标应用，拖动红点并点击工具条上的“保存”；也可以直接输入 X/Y，并实时显示当前坐标与屏幕分辨率。
- 目标日期 + 目标时间；时间支持 `HH:mm`、`HH:mm:ss`、`HH:mm:ss.SSS`，例如 `09:10:00.125`。
- 时间基准可选：设备系统时间（使用设备时区）或中国网络时间（Asia/Shanghai，UTC+8）。
- 通过 NTP 服务器校时，依次尝试 `ntp.aliyun.com`、`time1.cloud.tencent.com`、`time.cloudflare.com`。
- 校准可选不调整、提前或延迟；输入值单位为毫秒。
- 屏幕上实时显示毫秒时钟、网络校时偏差、目标时刻和倒计时。
- 通过无障碍 `dispatchGesture` 发送 100ms 手势，任务可手动停止。
- GitHub Actions 自动构建 debug APK 并保存为 workflow artifact。

## 重要精度说明

毫秒输入和显示并不等于 Android 能保证硬实时。任务线程会在目标时刻附近进行细粒度等待，但系统线程调度、设备负载、无障碍服务处理、屏幕状态以及目标应用本身，都可能造成实际触发延迟。此应用不能承诺误差恒为 1ms，也不能保证某个第三方应用一定接受点击。推荐测试设备的实际偏差，并保持屏幕亮起、网络稳定且关闭会限制后台活动的省电策略。

## 通过 GitHub 云端构建 APK

1. 在 GitHub 新建空仓库。
2. **推荐方式：**用 GitHub Desktop 克隆仓库，把本项目文件夹里的内容复制到克隆目录，再 Commit / Push。不要把最外层 `TimeTap` 文件夹再套一层。
3. 若 GitHub 网页端无法上传文件夹：先把 `TimeTap-GitHub.zip` 作为单个文件上传到仓库根目录；然后在网页点 **Add file → Create new file**，文件名填写 `.github/workflows/build.yml`，把单独提供的 `GITHUB-WORKFLOW-UPLOAD-ONLY.yml` 内容复制进去并提交。这个备用工作流会在云端解压 ZIP 并构建 APK。
4. 打开仓库的 **Actions**，允许工作流运行。推送代码会自动触发构建；备用工作流也支持 **Run workflow**。
5. 构建成功后，打开对应 workflow run 页面，在 **Artifacts** 区域下载 `TimeTap-debug-apk`，解压得到 `app-debug.apk`。
6. 将 APK 传到手机安装；首次使用进入系统设置，开启“毫秒定时点击器无障碍服务”。安装非应用商店 APK 时，系统可能要求允许该来源安装应用。

## 本地构建

使用 Android Studio 打开项目目录。项目要求 JDK 17、Android SDK Platform 35、Build Tools 35.0.0。项目内的 `gradlew` / `gradlew.bat` 会在首次使用时下载 Gradle 8.9；执行 `./gradlew assembleDebug`（Windows 执行 `gradlew.bat assembleDebug`）后，APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 使用方法

1. 安装并开启无障碍服务。
2. 选择“在屏幕上点选位置”，按 Home 或切换到目标应用，将悬浮红点拖到目标处，再点顶部工具条的“保存”；或者直接输入 X/Y。
3. 选择设备系统时间或中国网络时间，并填写日期和时间。
4. 需要校准时选择提前/延迟并输入毫秒数；例如提前 120ms 填 `120` 并选“提前”。
5. 若选择网络时间，先点“同步中国网络时间”确认校时状态；开始任务时还会再尝试同步。
6. 点击“开始定时点击”。建议先设较远的未来时间、小范围测试坐标与偏差。

## 已知边界

- 目前是一次性单击任务，不是循环连点器。
- 任务期间建议保持屏幕亮起、设备处于解锁状态，并让目标应用显示在前台。
- NTP 网络可能被部分网络或设备限制；若校时失败，请切换网络后重试。
- 屏幕旋转、分辨率/显示缩放变化或多窗口模式可能改变目标位置，请重新点选并确认坐标。
- 这是未在真实设备上验证过的源码初版；当前打包环境没有 Android SDK，因此此处提供 GitHub Actions 云构建，而不冒称已生成并测试 APK。
