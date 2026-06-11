# CLAUDE.md — 项目交接说明（给接手的 AI / 开发者）

> 本文件是项目的"一页上手"。先读这份，再看 `docs/`。
> 当前阶段共识：**壳（图标/布局）只是及格线，书写手感与交互质感才是这个项目的灵魂**。用户对"换皮不换芯"零容忍，每一轮改动都要在模拟器/真机实测出"能感受到的差异"再交付。

---

## 1. 项目是什么

- 代号 **Notices**，目标：**复刻华为笔记（HUAWEI Notes）**，在 **HarmonyOS 4（华为，安卓兼容层）** 上侧载使用。
- **对标对象 = 华为笔记**（专业手写/图文笔记），其旗舰功能 **「无界笔记」= 无限画布模式**，是本项目核心。
  - ⚠️ 不是"华为备忘录"（那是另一个更轻量的内置 App）。立项初期曾搞错，已纠正。
- 交互参照不限于华为：用户认可"一笔记/享做笔记"类 App 的拟物笔托盘与笔刷质感，**好的就学**。
- 应用内两种笔记形态：
  - **分页笔记**（图文富文本）：列表 FAB →「笔记」
  - **无界笔记**（无限画布）：列表 FAB →「无界笔记」

---

## 2. 开发环境（两套，先分清自己在哪套）

1. **用户本机 Windows（当前主力，强烈推荐）**：
   - Android SDK 在 `%LOCALAPPDATA%\Android\Sdk`（platform-34 + build-tools 34.0.0），`local.properties` 已指向（gitignore）。Java 17、ffmpeg、Chrome 都有，网络无限制。
   - 雷电模拟器 `emulator-5554` 常驻（2560×1440 平板横屏，安卓版本较老：不支持 `cmd input motionevent`，多点触控注入不可用）。
   - **视觉验证闭环（单轮十几秒）**：`gradlew assembleDebug` → `adb install -r` → `adb shell input tap/swipe` → `adb shell screencap` + pull → 看图对照 `refs/huawei/` 参照素材迭代。
2. **云开发环境（早期用过）**：无 SDK、网络白名单（仅 github/Maven Central/gradle.org），只能推 CI 等编译、拿不到外部图——**能用本机就别用这套**。

目标设备约束：**华为鸿蒙 4 无 GMS**。能用 ML Kit bundled 中文 OCR、系统 `SpeechRecognizer`、`BiometricPrompt`；**不能用任何 GMS 依赖 API**（如 ML Kit Document Scanner）。

---

## 3. 技术栈与架构

- Kotlin · Jetpack Compose（BOM 2024.10，UI 1.7，支持 `PointerInputChange.pressure`）· Room · Hilt · Coil · kotlinx.serialization · BiometricPrompt · ML Kit(bundled 中文 OCR)
- minSdk 26 / targetSdk 34，包名 `com.yhx.notices`，单 module（`app`），版本 `0.2.0-ink`
- 架构：MVVM 单向数据流（ViewModel + StateFlow / Compose state）

```
app/src/main/java/com/yhx/notices/
 ├─ data/
 │   ├─ local/        Room：Entities/DAO/NoticesDatabase（DB version=3，schemas/ 已入库）
 │   ├─ repository/   NoteRepository / FolderRepository / TodoRepository / Attachment /
 │   │                AudioEngine / OcrEngine / NoteCrypto / TagRepository / UserPrefs
 │   ├─ backup/       BackupManager（zip 导入导出）
 │   └─ export/       ExportManager（长图/PDF/分享；画布导出与屏显共用 InkGeometry）
 ├─ domain/
 │   ├─ richtext/     分页富文本引擎：块模型/样式算法/序列化（32 个单测）
 │   ├─ canvas/       CanvasModel（世界坐标元素+序列化，StrokeElement 含 widths 逐点宽）
 │   │                ★InkGeometry：墨迹几何引擎（Catmull-Rom 重采样 + 变宽轮廓多边形 +
 │   │                  圆头/平头帽 + 收笔笔锋），纯 Kotlin 有单测，屏显/导出两端共用
 │   └─ model/        Note / NoteListItem / 枚举
 ├─ reminder/         待办提醒（AlarmManager + 通知 + 启动重建）
 ├─ ui/
 │   ├─ icons/        ★HwIcons：约 40 枚自绘单色线性 ImageVector（24×24，tint 染色）
 │   │                ★HwPens：6 支多色拟物笔插画（28×64，须 tint=Unspecified）
 │   ├─ notes/        首页 NotesListScreen（华为版式：大标题/搜索条/自适应卡片网格/蓝 FAB）
 │   ├─ editor/       分页编辑器（华为化顶栏 + B/I/U/S 字形格式栏 + 自绘图标插入栏）
 │   ├─ canvas/       ★CanvasScreen/ViewModel（核心，约 1700 行，见 §4 详述）
 │   ├─ todo/ search/ trash/ settings/ security/ theme/ navigation/ AppViewModel
 ├─ di/               Hilt DatabaseModule
 └─ MainActivity（FragmentActivity，应用锁门禁）/ NoticesApp（通知渠道）
```

Room 迁移：v1→v2 加 `notes.isCanvas`；v2→v3 加 `folders.parentId`。**改 schema 必须加 Migration，禁止破坏式迁移**（DatabaseModule 注册）。

---

## 4. 无界笔记现状（本项目核心，重点读）

### 书写引擎（2026-06 重写，已实测）
- **渲染**：折线描边已废弃。落墨 = `InkGeometry.strokeOutline()` 生成变宽轮廓多边形后**填充**；
  实时预览/落墨/导出走同一引擎，所见即所得。落墨 Path 有缓存（key 含首末点，套索平移自动失效）。
- **动态笔宽**：手写笔压感优先（`change.pressure`），否则笔速映射（慢粗快细）+ 指数平滑；起笔渐入、收笔 `taperTail()` 三段递减出笔锋。
- **分笔刷质感**（`CanvasTool`）：钢笔 FOUNTAIN（0.8×宽，近恒宽微提按）、秀丽笔 PEN（1.45×~0.5× 大幅提按）、铅笔 PENCIL（双层渲染：毛边宽层 45% + 紧实芯层 75%，半径逐点确定性抖动）、马克笔 MARKER（2.2×宽 92% 实色恒宽）、荧光笔 HIGHLIGHTER（3×宽 35% 透明平头 + **Multiply 混合**真叠色）。
- **分层渲染**：已落墨内容烘焙为视口位图（`BakeStamp` 失效判定 + 60ms 防抖重烘，平移/缩放期矢量兜底），书写帧只画位图+当前一笔。`CanvasViewModel.revision` 驱动缓存失效——**任何元素变更都必须走 `markDirty()`**。
- **手势**：书写事件环中第二指落下 → 丢弃半笔转双指缩放平移；防误触开 = 手指仅平移、手写笔书写。
- **橡皮**：拖动实时命中灰显（25% 透明预览），抬手统一删（可撤销）；命中是**点到线段**距离（别改回点对点，快笔稀疏采样会漏擦）。

### 界面
- 顶部两行华为式：标题行（圆形返回+徽标+标题+插入/缩略图/九宫格菜单圆钮）+ 工具行（撤销重做｜移动/套索/文字｜一笔成形/防误触/纸张四选）。
- **底部拟物笔托盘**（参照一笔记类 App）：六支真笔插画、选中上浮动画、2×5 快捷色板、三档笔号、设置入口；把手可收起成底部小条。点已选中的笔 = 开关笔刷面板（粗细/不透明度细滑条 + 36 色网格，托盘上方弹出带缩放动画）。
- 其它：缩放胶囊（点按回 100%）、小地图（可开关）、**「回到内容」浮钮**（内容完全出视野时出现，点击 380ms 动画飞回适配）、贴纸、插入纵向空白、导出图片/PDF。

### 队列中（按优先级，用户已确认方向：不要问，直接做好）
1. 套索选区缩放/旋转控制手柄、选中笔迹改色改粗细
2. 一笔成形接墨迹引擎 + 更多形状（箭头/三角）+ 成形回弹动画
3. 每支笔独立参数面板（华为：铅笔有压感灵敏度、秀丽笔有稳定度）
4. 低延迟优化（运动预测）、触觉反馈、暗色画布、自定义取色器

---

## 5. 构建 / 签名 / 出 APK

- **调试签名已固定**：`app/debug.keystore` 在仓库内（密码 android/android，alias androiddebugkey），CI 与本地构建签名一致，**覆盖安装永远有效**。⚠️ 此前 CI 每次用 runner 临时签名导致"覆盖安装静默失败、用户以为装了新包"，引发过严重误判——别删这个 keystore。
- 本地：`gradlew assembleDebug` / `gradlew testDebugUnitTest`（富文本 32 测 + InkGeometry 4 测）。
- CI：push 到 `claude/**` 自动跑（`.github/workflows/android.yml`，Actions 已升 Node 24）。下载：Actions → 绿色运行 → Artifacts → `notices-debug-apk`。
- 模拟器装 CI 包与本地包可互相覆盖（同签名）。鸿蒙 5/NEXT 模拟器装不了（无安卓层）。
- 分支：开发都在 `claude/huawei-notes-analysis-1dtb9r`。

---

## 6. UI 还原状态速览

- **首页**：华为平板版式完成（大标题/数量/搜索条/自适应白卡网格/蓝 FAB 展开胶囊/底部导航蓝胶囊）。主题已补全 primaryContainer 等容器色（曾回落 M3 默认紫）。
- **分页编辑器**：顶栏华为化 + 字形格式栏 + 自绘图标插入栏，完成度中——后续可对照华为继续抠间距/动效。
- **无界笔记**：见 §4，目前是全项目完成度最高、也最被用户盯着的界面。
- 参照素材在本地 `refs/huawei/`（gitignore）：官网产品图（`product-2x.png` 是无界笔记基准图、`professional-brushes-1` 是笔刷面板特写）、官方演示视频、`crops/` 工具栏 3 倍裁切。图标先在 `refs/icons/*.html` 用 SVG 设计、无头 Chrome 截图审稿，再移植 ImageVector（`addPathNodes()` 直接吃 SVG path 字符串）。

---

## 7. 设计文档索引（docs/）

- `00` 总体设计、`01` 需求、`02` 数据库、`03` 富文本引擎、`04` UI、`05` 模块、`06` 安全、`07` 测试
- `08` 实现进展与功能对照、`09` 无界笔记画布设计、`10` 差距分析与缺陷清单
- ⚠️ docs 落后于代码（笔托盘/墨迹引擎/分层渲染未入档），以本文件 §4 和代码为准。

---

## 8. 给接手者的提醒（都是踩过的坑）

1. **改含中文的源文件只用 Edit/Write 工具**，绝不用 PowerShell `Get-Content | Set-Content` 管道——PS 5.1 按 GBK 误读 UTF-8 无 BOM 文件，中文全变乱码且 Edit 救不回（只能整文件重写）。
2. 改 Compose 图标用 **import 短名**（`Icons.Default.X`），全限定名不解析。
3. 改 Room schema 必加 Migration（见 §3）。
4. `HwPens` 多色插画必须 `tint = Color.Unspecified`，否则被染成单色。
5. Kotlin 属性 `var background` 与 `fun setBackground()` JVM 签名冲突——同名 setter 函数要换名（如 `changeBackground`）。
6. 模拟器偶发白屏（`mCurrentFocus=null`）不是崩溃，`am start` 重拉即可；冷启动后等足 4~5 秒再注入点击。
7. 华为云同步/碰一碰流转/云端 AI 属生态封闭，按"占位/端侧替代"处理。
8. 纯 Kotlin 逻辑（富文本引擎、InkGeometry、画布模型）有单测较可靠；改完 UI 必须模拟器截图实测，**口头"应该可以"不算交付**。
