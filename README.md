# 记事本（Notices）— 华为笔记 / 无界笔记 平替

一个对标 **华为笔记**（含 **无界笔记** 无限画布模式）的本地优先安卓笔记应用，
面向 HarmonyOS 4（安卓兼容层）侧载使用。Kotlin · Jetpack Compose · Room · Hilt。

> 对标对象说明：**无界笔记是华为笔记内的无限画布功能**，不是独立 App；
> 华为备忘录是另一个更轻量的内置 App。详见 [docs/08](docs/08-实现进展与功能对照.md)。

> 设计文档见 [`docs/`](docs/)。实现进展与功能逐项对照见
> [docs/08](docs/08-实现进展与功能对照.md)；无界笔记画布设计见
> [docs/09](docs/09-无界笔记画布设计.md)。

## 📥 获取 APK（无需本地 Android 环境）

本仓库配置了 GitHub Actions，每次推送自动编译并产出 debug APK：

1. 打开仓库 **Actions** 标签页 → 选择最新一次绿色的 **Android CI** 运行
2. 拉到底部 **Artifacts** → 下载 `notices-debug-apk`
3. 解压得到 `app-debug.apk`，传到手机侧载安装（需允许"未知来源"）

> 云开发环境无 Android SDK 且 Google 仓库受限，故 APK 由 CI 构建产出。

## ✅ 已实现功能（对标华为笔记）

### 无界笔记（无限画布 · 核心）
无限延伸画布、缩放 10%–1000%、钢笔/铅笔/荧光笔/橡皮（真擦除）、文字框、
图片、元素选择/移动/删除、纸张模板（网格/横线/点阵）、导出长图/PDF/分享。

### 分页笔记
富文本（加粗/斜体/下划线/删除线/颜色/高亮/字号）、标题、无序/有序/清单列表、
图片（全屏缩放查看）、手写、录音、表格、信纸样式、撤销重做、自动保存、
**语音转文字**（系统）、**OCR 文字识别**（ML Kit 端侧离线）、导出长图/PDF/分享。

### 整理 / 待办 / 数据
多层嵌套文件夹、全文搜索、置顶/收藏、回收站、批量操作；
待办分组 + 勾选 + 时间提醒（闹钟+通知）；
笔记加密（指纹/系统密码）、本地备份/恢复、深浅色主题。

## 🚧 待开发 / 占位（按"单机优先、联网占位"）
无界笔记缩略图导航、待办重复规则与子任务、桌面小组件、地点提醒、
WebDAV 云同步（替代华为云空间）；多设备流转、华为云端 AI 等生态封闭功能不做。

## 🛠 本地构建（可选）

需要 Android SDK 与可访问 Google Maven 的网络：

```bash
./gradlew assembleDebug      # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # 跑富文本引擎单元测试
```

- minSdk 26 / targetSdk 34，包名 `com.yhx.notices`
- 单元测试覆盖富文本核心（样式算法、块操作、序列化 round-trip）

## 📁 结构

```
app/src/main/java/com/yhx/notices/
 ├─ data/{local,repository,backup,export}   数据层 / 备份 / 导出
 ├─ domain/{richtext,canvas,model}          分页富文本引擎 + 无界画布模型
 ├─ reminder/                               待办提醒（闹钟 + 通知）
 ├─ ui/{notes,editor,canvas,todo,search,trash,settings,security,theme,navigation}
 └─ di/                                      Hilt 模块
```
