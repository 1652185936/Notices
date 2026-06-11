# CLAUDE.md — 项目交接说明（给接手的 AI / 开发者）

> 本文件是项目的"一页上手"。先读这份，再看 `docs/`。
> 作者诚实交代：**功能层做得比较全，但 UI 视觉还原华为做得不到位——这是最大短板，接手第一优先级就是它。**

---

## 1. 项目是什么

- 代号 **Notices**，目标：**复刻华为笔记（HUAWEI Notes）**，在 **HarmonyOS 4（华为，安卓兼容层）** 上侧载使用。
- **对标对象 = 华为笔记**（专业手写/图文笔记），其旗舰功能 **「无界笔记」= 无限画布模式**，是本项目核心。
  - ⚠️ 不是"华为备忘录"（那是另一个更轻量的内置 App）。立项初期曾搞错，已纠正。
- 应用内两种笔记形态：
  - **分页笔记**（图文富文本）：列表 FAB →「笔记」
  - **无界笔记**（无限画布）：列表 FAB →「无界笔记」

---

## 2. 关键约束（务必先知道，否则会走弯路）

1. **目标设备是华为（鸿蒙 4），没有 Google Play 服务（GMS）。**
   - 能用：**ML Kit 端侧 bundled 版**（`text-recognition-chinese`，离线 OCR，不依赖 GMS）、系统 `SpeechRecognizer`、`BiometricPrompt`。
   - **不能用**：任何 GMS 依赖 API（如 ML Kit Document Scanner = `play-services-mlkit-document-scanner`）。文档扫描的边缘检测要做就得用 OpenCV 自实现。
2. **本仓库的开发云环境无法本地编译/运行**：没有 Android SDK，且网络是**白名单制**——只放行 `github.com` / `repo1.maven.org`(Maven Central) / `services.gradle.org`，**其它全部 403**（Google Maven、华为官网、Bing/百度/DuckDuckGo 图片搜索…全连不出去）。
   - 后果一：**APK 由 GitHub Actions 构建**（见 §5），不能在本地 `./gradlew` 跑通。
   - 后果二：**抓不到任何外部图片/截图**做参照。要还原华为 UI，**只能靠用户上传的截图**（上传的本地图片可以 Read 查看）。
3. **不要用 Figma 工具去"截华为 App"**——Figma 工具只能截 Figma 设计稿，截不到华为 App。

---

## 3. 技术栈与架构

- Kotlin · Jetpack Compose · Room · Hilt · Coil · kotlinx.serialization · WorkManager(未全接) · BiometricPrompt · ML Kit(bundled 中文 OCR)
- minSdk 26 / targetSdk 34，包名 `com.yhx.notices`，单 module（`app`）
- 架构：MVVM 单向数据流（ViewModel + StateFlow / Compose state）
- 依赖用版本目录 `gradle/libs.versions.toml`

```
app/src/main/java/com/yhx/notices/
 ├─ data/
 │   ├─ local/        Room：Entities/DAO/NoticesDatabase（DB version=3）
 │   ├─ repository/   NoteRepository / FolderRepository / TodoRepository /
 │   │                AttachmentRepository / AudioEngine / OcrEngine / NoteCrypto /
 │   │                TagRepository / UserPrefs
 │   ├─ backup/       BackupManager（zip 导入导出）
 │   └─ export/       ExportManager（长图 / PDF / 分享）
 ├─ domain/
 │   ├─ richtext/     分页富文本引擎：块模型/样式算法/序列化（有 32 个单测）
 │   ├─ canvas/       无界笔记画布模型 CanvasModel（世界坐标元素 + 序列化）
 │   └─ model/        Note / NoteListItem / 枚举
 ├─ reminder/         待办提醒（AlarmManager + 通知 + 启动重建）
 ├─ ui/
 │   ├─ notes/        列表 NotesListScreen + NoteListViewModel（文件夹抽屉/排序/视图/移动/删除撤销）
 │   ├─ editor/       分页编辑器 NoteEditorScreen/ViewModel + EditorToolbars + SketchEditor(手写画板) + SpanStyleMapping
 │   ├─ canvas/       ★无界笔记 CanvasScreen/ViewModel（核心，UI 重点在这）
 │   ├─ todo/ search/ trash/ settings/ security/ theme/ navigation/ AppViewModel
 ├─ di/               Hilt DatabaseModule
 └─ MainActivity（FragmentActivity，含应用锁门禁）/ NoticesApp（通知渠道）
```

Room 迁移：v1→v2 加 `notes.isCanvas`；v2→v3 加 `folders.parentId`。**改 schema 必须加 Migration，禁止破坏式迁移**（DatabaseModule 里注册）。

---

## 4. 已实现功能（功能层，多数已 CI 验证）

- **无界笔记（CanvasScreen）**：无限画布、缩放 10%–1000%、钢笔/铅笔/荧光笔/橡皮、**套索圈选→移动/复制/删除**、**一笔成形(直线/矩形/椭圆)**、文字框(可二次编辑)、图片、**插入纵向空白**、纸张模板(网格/横线/点阵)、**缩略图小地图**、贴纸(Emoji)、导出图片/PDF/分享。工具栏已改成顶部图标 + 点笔弹「笔刷设置」浮层(粗细+36色网格)。
- **分页笔记**：富文本(加粗/斜体/下划线/删除线/颜色/高亮/字号)、H1-H3、无序/有序/清单、图片(全屏缩放查看)、手写画板、录音、表格、信纸样式、撤销重做、自动保存、**OCR 提取文字**(图片查看器内)、**语音转文字**、标签、加密(指纹/系统密码)、导出/分享。
- **整理**：多层嵌套文件夹(改名/改色/删除)、全文搜索(FTS+中文 LIKE 兜底)+关键词高亮、置顶/收藏、回收站、多选批量。
- **待办**：分组(逾期/今天/未来/无日期/已完成)、子任务+进度、重复规则、编辑、滑动删除、重复自动续期、时间提醒(闹钟+通知)。
- **其它**：本地备份/恢复、深浅色主题、应用锁。

详尽对照见 `docs/08-实现进展与功能对照.md`。

---

## 5. 构建 / 运行 / 出 APK

**APK 由 GitHub Actions 构建**（`.github/workflows/android.yml`）：每次 push 到 `claude/**` 分支自动跑单测 + `assembleDebug` + 上传 artifact。
- 下载：仓库 **Actions → 最新绿色 Android CI 运行 → Artifacts → `notices-debug-apk`**（zip，内含 `app-debug.apk`）。
- 装到**安卓模拟器/华为真机**：`adb install app-debug.apk` 或拖进模拟器。鸿蒙 5/NEXT 模拟器装不了（无安卓层）。

**有本地 Android 环境的话**（接手者推荐）：
```
./gradlew assembleDebug      # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # 富文本引擎单测
```
**强烈建议接手者在本地用 Android Studio 跑**，别再走"推 CI 等编译"那套——那样验证一轮要好几分钟、极烧 token，是本项目前期最大的浪费。

分支：开发都在 `claude/huawei-notes-analysis-1dtb9r`。

---

## 6. UI 视觉还原状态（2026-06 已大幅推进）

原三大短板（①无界笔记工具栏 ②首页列表 ③分页编辑器工具栏）**已对照华为官方截图重做并在模拟器实测**：

- **自绘矢量图标库 `ui/icons/HwIcons.kt`**：约 40 枚 24×24 线性 ImageVector（笔具/套索/纸张/防误触/相机/麦克风/表格/导航等），全部代码内手绘路径，无第三方图标依赖。新增图标继续加在这里。
- **无界笔记**：两行式顶栏（标题行圆钮组 + 工具行），三档线宽波浪预设 + 快捷色点（选中色为圆环），笔刷浮层（笔尖选项卡/粗细/不透明度细滑条/36 色网格），纸张四选面板、重做栈、防误触（手指平移、手写笔书写）、缩放胶囊、小地图开关。
- **首页**：大标题+数量+搜索条、自适应卡片网格（日期/收藏/置顶角标）、蓝色 FAB 展开白胶囊选项、底部导航蓝色胶囊；主题已补全 primaryContainer 等容器色（之前回落 M3 默认紫）。
- **分页编辑器**：顶栏华为化（圆形返回/撤销重做/九宫格菜单），格式栏 B/I/U/S 字形按钮 + 自绘图标，插入栏全自绘图标。

**仍待打磨**：套索"调整大小/转文本"、橡皮"擦除整个笔划"开关、各页动效/触觉反馈、深色画布适配细节。参照素材在本地 `refs/huawei/`（已 gitignore，官方产品图/演示视频帧/工具栏高清裁切图）。`docs/10-差距分析与缺陷清单.md` 有完整清单。

**视觉验证工作流（本机已打通，强烈建议沿用）**：本地 `gradlew assembleDebug` → `adb install -r` 到模拟器 → `adb shell input tap/swipe` 操作 → `adb shell screencap` 截图对照华为参照图迭代，单轮十几秒。

---

## 7. 设计文档索引（docs/）

- `00` 总体设计、`01` 需求、`02` 数据库、`03` 富文本引擎、`04` UI、`05` 模块、`06` 安全、`07` 测试
- `08` 实现进展与功能对照（**最新状态以此为准**）
- `09` 无界笔记画布设计（核心模块架构）
- `10` 差距分析与缺陷清单（**对华为的自我批判 + 待办优先级**）

---

## 8. 给接手者的几条提醒

1. 改 Compose 图标用 **import 后的短名**（`Icons.Default.X`），**不要写全限定 `androidx.compose.material.icons.Icons.Default.X`**（图标是扩展属性，全限定不解析，本项目踩过多次）。
2. 改 Room schema 必加 Migration（见 §3）。
3. 华为云同步/碰一碰流转/云端 AI(慧写/小艺/公式识别) 属生态封闭，单机做不了，按"占位/端侧替代"处理。
4. 视觉还原靠用户截图，环境内**拿不到外部图**（§2）。
5. 富文本引擎、画布模型这些**纯 Kotlin 逻辑有单测、较可靠**；UI 层是要重点打磨的。
