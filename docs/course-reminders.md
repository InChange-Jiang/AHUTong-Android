# 课前提醒

## 行为

- 保存提醒开关成功后才排期或取消。排期、取消和通知发送串行执行，过时的排期命令不会覆盖较新的开关操作。
- 按当前账号、学期起始日、课程周次和星期直接计算上课日期，不限制为未来 21 天。
- 正常提前 10 分钟提醒；进入课前 10 分钟但尚未开始的课程补发一次。上课时间始终来自课程，补发文案显示实际剩余分钟。
- 发送记录按账号、学期、课程和本次上课时间生成摘要，持久化到应用私有 SharedPreferences。重复广播、重排和课程地点更新不会再次通知同一节课；关闭提醒撤销排期及当前通知。
- 排期只读本地缓存，不登录或拉取远端课表；开学前和已开始的课程不会补发，今日非教学日按本地课表状态跳过。
- Android 8–11 使用精确闹钟。Android 12 及以上检查特殊权限，设置页提供“准时提醒权限”入口；未授权时保留可能延迟的普通排期。权限变化后重排。
- Android 16 岛卡使用系统 Chronometer 倒计时和通知超时，正文显示绝对上课时间，不再预约每分钟唤醒。旧版本遗留的分钟闹钟会被取消；用户移除已发送岛卡后，重排不会重新弹出。

## 自动验证

```powershell
adb devices
.\gradlew.bat :background:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:schedule:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:testDebugUnitTest --tests com.ahu.ahutong.architecture.ModuleBoundaryTest
```

单元测试覆盖延迟落盘、快速开关、等待中的取消与重排、提醒窗口边界、开学前日期、远期和单双周课程、无效时间、账号与学期隔离、地点变化及已发送记录。

连接设备时，安装并启动独立 Debug 包后运行：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am start -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity
adb shell am instrument -w -r -e class com.ahu.ahutong.notification.CourseReminderIntegrationTest com.ahu.ahutong.debug.test/androidx.test.runner.AndroidJUnitRunner
```

设备测试需要 Debug 包的通知权限。补发测试只在未登录的 Debug 包上运行，并在结束时恢复原有开关和课表选择；已有登录账号时跳过，避免替换该账号的课表。岛卡设备测试仅在 Android 16 及以上运行。

## 本次验证（2026-10-01）

- `background` 17 个、设置 22 个、课表 16 个单元测试，以及 2 个架构门禁测试通过，共 57 个。
- Debug APK 和设备测试 APK 构建通过，Android 14（API 34）设备安装、启动成功。
- 设备初次运行通过补发、发送记录写入、重复重排去重和关闭通知撤销；另一次运行通过当前包的精确闹钟权限页面跳转。
- Android 16 系统倒计时设备测试因设备版本不足跳过，尚需 Android 16 真机验证锁屏、进程退出及 Doze 下的展示。
- 额外 Lint 检查被现有 `core:storage:debugUnitTestRuntimeClasspath` 锁文件缺少 `kotlinx-coroutines-android:1.10.2` 挡住；本次没有修改无关依赖锁。
