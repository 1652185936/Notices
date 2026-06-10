# 记事本（Notices）— 华为笔记平替

一个对标华为笔记的本地优先安卓笔记应用，面向 HarmonyOS 4（安卓兼容层）侧载使用。
Kotlin · Jetpack Compose · Room · Hilt。

> 设计文档见 [`docs/`](docs/)（需求、数据库、富文本编辑器、UI、安全、测试共 8 份）。

## 📥 获取 APK（无需本地 Android 环境）

本仓库配置了 GitHub Actions，每次推送自动编译并产出 debug APK：

1. 打开仓库 **Actions** 标签页 → 选择最新一次绿色的 **Android CI** 运行
2. 拉到底部 **Artifacts** → 下载 `notices-debug-apk`
3. 解压得到 `app-debug.apk`，传到手机侧载安装（需允许"未知来源"）

## ✅ 已实现功能

| 模块 | 功能 |
|------|------|
| 笔记 | 富文本编辑（加粗/斜体/下划线/删除线/颜色/高亮/字号）、标题 H1-H3、无序/有序/清单列表、插入图片、撤销重做、自动保存 |
| 列表 | 双列卡片、置顶、收藏、多选批量删除/置顶、空笔记自动丢弃 |
| 文件夹 | 侧滑抽屉切换/新建文件夹、按文件夹过滤、笔记计数 |
| 搜索 | 全文检索（FTS + 中文 LIKE 兜底）、防抖即时搜索 |
| 待办 | 独立清单、按时间分组（逾期/今天/未来/无日期/已完成）、勾选完成、时间提醒（精确闹钟 + 通知） |
| 回收站 | 软删除、恢复、彻底删除 |
| 数据 | 本地备份/恢复（zip：data.json + 附件） |
| 加密 | 笔记内容 AES-256-GCM（Keystore 信封加密，数据层就绪） |
| 外观 | 浅色/深色/跟随系统主题（仿华为蓝） |

## 🚧 待开发（路线图）

- 笔记加密的指纹/PIN 验证 UI 与隐私门禁
- 录音、手写涂鸦、表格插入
- 分享长图卡片、导出 PDF
- 标签系统 UI、信纸主题
- 桌面小组件、地点提醒
- OCR 文字识别、语音转文字（ML Kit / 系统引擎）
- WebDAV 云同步

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
 ├─ data/{local,repository,backup}   数据层（Room / 仓库 / 备份）
 ├─ domain/{model,richtext}          领域模型 + 富文本引擎
 ├─ reminder/                        待办提醒（闹钟 + 通知）
 ├─ ui/{notes,editor,todo,search,trash,settings,theme,navigation}
 └─ di/                              Hilt 模块
```
