package com.yhx.notices.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 自绘华为笔记风格图标库（对照华为真机/官网截图手工绘制的原创矢量）。
 * 全部为 24×24 视口、1.5 描边、圆角端点的线性图标；通过 Icon(tint=...) 染色。
 */
object HwIcons {

    /** 返回（左尖括号） */
    val Back: ImageVector by lazy {
        hwIcon("Hw.Back") { stroke("M14.5,5.5 L8,12 L14.5,18.5", 1.7f) }
    }

    /** 笔记徽标（圆角方块左半实心，标题栏用） */
    val NoteBadge: ImageVector by lazy {
        hwIcon("Hw.NoteBadge") {
            stroke("M6.9,5.2 H17.1 C18.5,5.2 19.6,6.3 19.6,7.7 V16.3 C19.6,17.7 18.5,18.8 17.1,18.8 H6.9 C5.5,18.8 4.4,17.7 4.4,16.3 V7.7 C4.4,6.3 5.5,5.2 6.9,5.2 Z")
            fill("M6.9,5.2 H11.6 V18.8 H6.9 C5.5,18.8 4.4,17.7 4.4,16.3 V7.7 C4.4,6.3 5.5,5.2 6.9,5.2 Z")
        }
    }

    /** 关闭 × */
    val Close: ImageVector by lazy {
        hwIcon("Hw.Close") { stroke("M6.8,6.8 L17.2,17.2 M17.2,6.8 L6.8,17.2", 1.7f) }
    }

    /** 加号 ＋ */
    val Add: ImageVector by lazy {
        hwIcon("Hw.Add") { stroke("M12,5.2 V18.8 M5.2,12 H18.8", 1.7f) }
    }

    /** 搜索（放大镜） */
    val Search: ImageVector by lazy {
        hwIcon("Hw.Search") { stroke("M10.8,5.2 a5.6,5.6 0 1 1 -0.01,0 Z M15.1,15.1 L19.3,19.3") }
    }

    /** 缩略图侧栏（圆角矩形 + 右侧分栏小条） */
    val Panel: ImageVector by lazy {
        hwIcon("Hw.Panel") {
            stroke("M6.5,6.2 H17.5 C18.9,6.2 20,7.3 20,8.7 V15.3 C20,16.7 18.9,17.8 17.5,17.8 H6.5 C5.1,17.8 4,16.7 4,15.3 V8.7 C4,7.3 5.1,6.2 6.5,6.2 Z")
            stroke("M14.6,6.4 V17.6")
            stroke("M16.4,9 H18 M16.4,12 H18 M16.4,15 H18", 1.3f)
        }
    }

    /** 九宫菜单（2×2 圆点） */
    val GridMenu: ImageVector by lazy {
        hwIcon("Hw.GridMenu") {
            fill("M8.1,6.6 a1.55,1.55 0 1 1 -0.01,0 Z")
            fill("M15.9,6.6 a1.55,1.55 0 1 1 -0.01,0 Z")
            fill("M8.1,14.4 a1.55,1.55 0 1 1 -0.01,0 Z")
            fill("M15.9,14.4 a1.55,1.55 0 1 1 -0.01,0 Z")
        }
    }

    /** 撤销 ↶ */
    val Undo: ImageVector by lazy {
        hwIcon("Hw.Undo") {
            stroke("M9.2,5.8 L5.4,9.2 L9.2,12.6")
            stroke("M5.4,9.2 H14.2 C17.3,9.2 18.8,11.3 18.8,13.6 C18.8,15.9 17.3,18 14.2,18 H10.5")
        }
    }

    /** 重做 ↷ */
    val Redo: ImageVector by lazy {
        hwIcon("Hw.Redo") {
            stroke("M14.8,5.8 L18.6,9.2 L14.8,12.6")
            stroke("M18.6,9.2 H9.8 C6.7,9.2 5.2,11.3 5.2,13.6 C5.2,15.9 6.7,18 9.8,18 H13.5")
        }
    }

    /** 平移画布（四向箭头） */
    val Move: ImageVector by lazy {
        hwIcon("Hw.Move") {
            stroke("M12,3.6 V20.4 M3.6,12 H20.4")
            stroke("M9.9,5.8 L12,3.6 L14.1,5.8 M9.9,18.2 L12,20.4 L14.1,18.2 M5.8,9.9 L3.6,12 L5.8,14.1 M18.2,9.9 L20.4,12 L18.2,14.1")
        }
    }

    /** 秀丽笔（实心笔尖 + 缝线挖空 + 底座横杠） */
    val Pen: ImageVector by lazy {
        hwIcon("Hw.Pen") {
            fill(
                "M12,3.2 C14.6,6.4 16.2,9.4 16.2,12 C16.2,14.9 14.5,16.6 12,16.6 C9.5,16.6 7.8,14.9 7.8,12 C7.8,9.4 9.4,6.4 12,3.2 Z " +
                    "M11.6,16.6 L11.6,11.6 C11.6,11.1 12.4,11.1 12.4,11.6 L12.4,16.6 Z " +
                    "M12,8.1 a1.05,1.05 0 1 1 -0.01,0 Z",
                evenOdd = true,
            )
            stroke("M8.2,19.6 H15.8", 1.7f)
        }
    }

    /** 铅笔（笔身 + 削尖 + 笔芯） */
    val Pencil: ImageVector by lazy {
        hwIcon("Hw.Pencil") {
            stroke("M8.7,13.4 V6.6 C8.7,3.9 15.3,3.9 15.3,6.6 V13.4 L12,19.8 Z")
            stroke("M8.7,13.4 H15.3")
            stroke("M10,13.4 L13.1,16.8 M12.7,13.4 L14.2,15")
            fill("M11.3,18.5 L12,19.8 L12.7,18.5 Z")
        }
    }

    /** 马克笔（笔帽 + 笔身 + 楔形笔头） */
    val Marker: ImageVector by lazy {
        hwIcon("Hw.Marker") {
            stroke("M9.1,7.2 V5.9 C9.1,4.8 10,3.9 11.1,3.9 H12.9 C14,3.9 14.9,4.8 14.9,5.9 V7.2 Z")
            stroke("M9.1,7.2 H14.9 V12.8 H9.1 Z")
            stroke("M9.1,12.8 L7.9,17.1 C7.7,17.7 8.2,18.4 8.9,18.4 H15.1 C15.8,18.4 16.3,17.7 16.1,17.1 L14.9,12.8")
        }
    }

    /** 橡皮（圆顶面包形 + 中部腰线） */
    val Eraser: ImageVector by lazy {
        hwIcon("Hw.Eraser") {
            stroke("M6.4,16.3 V11.7 C6.4,9.3 8.3,7.6 12,7.6 C15.7,7.6 17.6,9.3 17.6,11.7 V16.3 C17.6,16.9 17.1,17.4 16.5,17.4 H7.5 C6.9,17.4 6.4,16.9 6.4,16.3 Z")
            stroke("M6.4,12.6 H17.6")
        }
    }

    /** 套索（8 段虚线圆） */
    val Lasso: ImageVector by lazy {
        hwIcon("Hw.Lasso") { stroke(lassoDash(), 1.6f) }
    }

    /** 文本框（圆角矩形 + T + 左右拖点） */
    val TextBox: ImageVector by lazy {
        hwIcon("Hw.TextBox") {
            stroke("M6.7,6.9 H17.3 C18.7,6.9 19.8,8 19.8,9.4 V14.6 C19.8,16 18.7,17.1 17.3,17.1 H6.7 C5.3,17.1 4.2,16 4.2,14.6 V9.4 C4.2,8 5.3,6.9 6.7,6.9 Z")
            stroke("M9.4,10 H14.6 M12,10 V14.2")
            fill("M4.2,12 a1.25,1.25 0 1 1 -0.01,0 Z")
            fill("M19.8,12 a1.25,1.25 0 1 1 -0.01,0 Z")
        }
    }

    /** 图片（相框 + 山形 + 太阳点） */
    val Image: ImageVector by lazy {
        hwIcon("Hw.Image") {
            stroke("M7,5.8 H17 C18.4,5.8 19.5,6.9 19.5,8.3 V15.7 C19.5,17.1 18.4,18.2 17,18.2 H7 C5.6,18.2 4.5,17.1 4.5,15.7 V8.3 C4.5,6.9 5.6,5.8 7,5.8 Z")
            stroke("M7.1,15.7 L10.8,11.4 L13.6,14.6 L15.4,12.6 L17.3,14.9")
            fill("M8.7,9.1 a1.05,1.05 0 1 1 -0.01,0 Z")
        }
    }

    /** 贴纸（缺角圆 + 翻折内弧） */
    val Sticker: ImageVector by lazy {
        hwIcon("Hw.Sticker") {
            stroke("M14.6,18.9 A7.4,7.4 0 1 1 18.9,14.6")
            stroke("M18.9,14.6 Q15.5,15.5 14.6,18.9")
        }
    }

    /** 一笔成形（实心圆 + 圆角矩形描边） */
    val ShapeRecog: ImageVector by lazy {
        hwIcon("Hw.ShapeRecog") {
            stroke("M13.4,6.4 H17.6 C18.9,6.4 20,7.5 20,8.8 V10.8 C20,12.1 18.9,13.2 17.6,13.2 H13.4 C12.1,13.2 11,12.1 11,10.8 V8.8 C11,7.5 12.1,6.4 13.4,6.4 Z")
            fill("M8.8,10.5 a4.1,4.1 0 1 1 -0.01,0 Z")
        }
    }

    /** 防误触（手掌 + 斜杠） */
    val Palm: ImageVector by lazy {
        hwIcon("Hw.Palm") {
            stroke(
                "M9.3,11.6 V6.6 C9.3,5.9 9.9,5.3 10.6,5.3 C11.3,5.3 11.9,5.9 11.9,6.6 V10.6 " +
                    "M11.9,10.6 V5.4 C11.9,4.7 12.5,4.1 13.2,4.1 C13.9,4.1 14.5,4.7 14.5,5.4 V10.7 " +
                    "M14.5,10.7 V6.7 C14.5,6 15.1,5.4 15.8,5.4 C16.5,5.4 17.1,6 17.1,6.7 V13.9 " +
                    "C17.1,17 15.1,19.1 12.1,19.1 C9.9,19.1 8.5,18.1 7.4,16 L5.8,12.9 " +
                    "C5.5,12.3 5.7,11.6 6.3,11.3 C6.9,11 7.6,11.2 8,11.7 L9.3,13.5",
            )
            stroke("M6,18.6 L18.6,6")
        }
    }

    /** 纸张样式（圆角方块 + 斜纹） */
    val Paper: ImageVector by lazy {
        hwIcon("Hw.Paper") {
            stroke("M7.5,5 H16.5 C17.9,5 19,6.1 19,7.5 V16.5 C19,17.9 17.9,19 16.5,19 H7.5 C6.1,19 5,17.9 5,16.5 V7.5 C5,6.1 6.1,5 7.5,5 Z")
            stroke("M5.2,10.3 L10.3,5.2 M5.2,15.3 L15.3,5.2 M7.4,18.3 L18.3,7.4 M12.4,18.6 L18.6,12.4", 1.3f)
        }
    }

    /** 插入纵向空白（上下横线 + 双向箭头） */
    val InsertSpace: ImageVector by lazy {
        hwIcon("Hw.InsertSpace") {
            stroke("M5.2,6.4 H18.8 M5.2,17.6 H18.8")
            stroke("M12,8.6 V15.4 M10.2,10.3 L12,8.5 L13.8,10.3 M10.2,13.7 L12,15.5 L13.8,13.7")
        }
    }

    /** 收藏星（描边） */
    val Star: ImageVector by lazy {
        hwIcon("Hw.Star") {
            stroke("M12,4.4 L14.25,9.05 L19.4,9.75 L15.65,13.35 L16.55,18.45 L12,16 L7.45,18.45 L8.35,13.35 L4.6,9.75 L9.75,9.05 Z")
        }
    }

    /** 收藏星（实心） */
    val StarFilled: ImageVector by lazy {
        hwIcon("Hw.StarFilled") {
            fill("M12,4.4 L14.25,9.05 L19.4,9.75 L15.65,13.35 L16.55,18.45 L12,16 L7.45,18.45 L8.35,13.35 L4.6,9.75 L9.75,9.05 Z")
        }
    }

    /** 汉堡菜单（三横线） */
    val Menu: ImageVector by lazy {
        hwIcon("Hw.Menu") { stroke("M4.5,7 H19.5 M4.5,12 H19.5 M4.5,17 H19.5", 1.7f) }
    }

    /** 排序（长短递减三横线） */
    val Sort: ImageVector by lazy {
        hwIcon("Hw.Sort") { stroke("M4.5,7 H19.5 M4.5,12 H14.5 M4.5,17 H9.5", 1.7f) }
    }

    /** 网格视图（2×2 圆角方块） */
    val ViewGrid: ImageVector by lazy {
        hwIcon("Hw.ViewGrid") {
            stroke("M6.3,4.5 H8.7 C9.7,4.5 10.5,5.3 10.5,6.3 V8.7 C10.5,9.7 9.7,10.5 8.7,10.5 H6.3 C5.3,10.5 4.5,9.7 4.5,8.7 V6.3 C4.5,5.3 5.3,4.5 6.3,4.5 Z")
            stroke("M15.3,4.5 H17.7 C18.7,4.5 19.5,5.3 19.5,6.3 V8.7 C19.5,9.7 18.7,10.5 17.7,10.5 H15.3 C14.3,10.5 13.5,9.7 13.5,8.7 V6.3 C13.5,5.3 14.3,4.5 15.3,4.5 Z")
            stroke("M6.3,13.5 H8.7 C9.7,13.5 10.5,14.3 10.5,15.3 V17.7 C10.5,18.7 9.7,19.5 8.7,19.5 H6.3 C5.3,19.5 4.5,18.7 4.5,17.7 V15.3 C4.5,14.3 5.3,13.5 6.3,13.5 Z")
            stroke("M15.3,13.5 H17.7 C18.7,13.5 19.5,14.3 19.5,15.3 V17.7 C19.5,18.7 18.7,19.5 17.7,19.5 H15.3 C14.3,19.5 13.5,18.7 13.5,17.7 V15.3 C13.5,14.3 14.3,13.5 15.3,13.5 Z")
        }
    }

    /** 列表视图（圆点 + 横线 ×3） */
    val ViewList: ImageVector by lazy {
        hwIcon("Hw.ViewList") {
            fill("M5.6,7 a1.3,1.3 0 1 1 -0.01,0 Z")
            fill("M5.6,12 a1.3,1.3 0 1 1 -0.01,0 Z")
            fill("M5.6,17 a1.3,1.3 0 1 1 -0.01,0 Z")
            stroke("M9.5,7 H19.5 M9.5,12 H19.5 M9.5,17 H19.5", 1.7f)
        }
    }

    /** 锁（加密笔记标识） */
    val Lock: ImageVector by lazy {
        hwIcon("Hw.Lock") {
            stroke("M8.2,10.6 V8.6 C8.2,6.5 9.9,4.8 12,4.8 C14.1,4.8 15.8,6.5 15.8,8.6 V10.6")
            stroke("M8.5,10.6 H15.5 C16.9,10.6 18,11.7 18,13.1 V16.7 C18,18.1 16.9,19.2 15.5,19.2 H8.5 C7.1,19.2 6,18.1 6,16.7 V13.1 C6,11.7 7.1,10.6 8.5,10.6 Z")
            fill("M12,13.6 a1.2,1.2 0 1 1 -0.01,0 Z")
        }
    }

    /** 待办（圆圈 + 对勾） */
    val TodoCheck: ImageVector by lazy {
        hwIcon("Hw.TodoCheck") {
            stroke("M12,4.5 a7.5,7.5 0 1 1 -0.01,0 Z")
            stroke("M8.6,12.2 L11.1,14.7 L15.6,9.6", 1.7f)
        }
    }

    /** 我的（人像） */
    val Person: ImageVector by lazy {
        hwIcon("Hw.Person") {
            stroke("M12,4.9 a3.3,3.3 0 1 1 -0.01,0 Z")
            stroke("M5.5,19 C5.5,15.2 8.4,13.2 12,13.2 C15.6,13.2 18.5,15.2 18.5,19")
        }
    }

    /** 标题字号（大 T + 小 T） */
    val TitleSize: ImageVector by lazy {
        hwIcon("Hw.TitleSize") {
            stroke("M5,7 H13.4 M9.2,7 V17.6", 1.7f)
            stroke("M14.6,11.6 H19.6 M17.1,11.6 V17.6", 1.5f)
        }
    }

    /** 有序列表（数字 + 横线） */
    val NumberList: ImageVector by lazy {
        hwIcon("Hw.NumberList") {
            stroke("M5,5.6 L6.4,4.9 V8.3", 1.3f)
            stroke("M5,10.9 C5,10 6.9,9.9 6.9,11 C6.9,11.9 5,12.6 5,13.5 H7", 1.3f)
            stroke("M5,16.1 H6.9 L5.9,17.4 C6.9,17.4 7.1,18 7.1,18.4 C7.1,19.3 5.4,19.5 5,18.8", 1.3f)
            stroke("M10,6.5 H19.5 M10,12 H19.5 M10,17.5 H19.5", 1.6f)
        }
    }

    /** 相机 */
    val Camera: ImageVector by lazy {
        hwIcon("Hw.Camera") {
            stroke("M9,8 L9.8,6 C9.95,5.6 10.3,5.3 10.75,5.3 H13.25 C13.7,5.3 14.05,5.6 14.2,6 L15,8")
            stroke("M7,8 H17 C18.4,8 19.5,9.1 19.5,10.5 V16 C19.5,17.4 18.4,18.5 17,18.5 H7 C5.6,18.5 4.5,17.4 4.5,16 V10.5 C4.5,9.1 5.6,8 7,8 Z")
            stroke("M12,10.3 a3,3 0 1 1 -0.01,0 Z")
        }
    }

    /** 麦克风（录音） */
    val Mic: ImageVector by lazy {
        hwIcon("Hw.Mic") {
            stroke("M11.99,4.4 C13.32,4.4 14.4,5.48 14.4,6.81 V10.6 C14.4,11.93 13.32,13 11.99,13 C10.67,13 9.6,11.93 9.6,10.6 V6.81 C9.6,5.48 10.67,4.4 11.99,4.4 Z")
            stroke("M6.9,11.2 C6.9,14.2 9.1,16.2 12,16.2 C14.9,16.2 17.1,14.2 17.1,11.2")
            stroke("M12,16.2 V19.2 M9.4,19.2 H14.6")
        }
    }

    /** 语音转文字（声纹条） */
    val Waveform: ImageVector by lazy {
        hwIcon("Hw.Waveform") {
            stroke("M5,10.2 V13.8 M8.5,7.6 V16.4 M12,5.4 V18.6 M15.5,8.6 V15.4 M19,10.6 V13.4", 1.7f)
        }
    }

    /** 表格 */
    val Table: ImageVector by lazy {
        hwIcon("Hw.Table") {
            stroke("M7,5.5 H17 C18.4,5.5 19.5,6.6 19.5,8 V16 C19.5,17.4 18.4,18.5 17,18.5 H7 C5.6,18.5 4.5,17.4 4.5,16 V8 C4.5,6.6 5.6,5.5 7,5.5 Z")
            stroke("M12,5.5 V18.5 M4.5,12 H19.5")
        }
    }

    /** 分割线 */
    val DividerLine: ImageVector by lazy {
        hwIcon("Hw.DividerLine") {
            stroke("M6.5,7.2 H17.5", 1.3f)
            stroke("M4.5,12 H19.5", 1.8f)
            stroke("M6.5,16.8 H17.5", 1.3f)
        }
    }

    /** 手写画板（斜笔 + 笔迹波浪） */
    val Sketch: ImageVector by lazy {
        hwIcon("Hw.Sketch") {
            stroke("M11.6,13.2 L16.9,7.9 C17.6,7.2 18.7,7.2 19.4,7.9 C20.1,8.6 20.1,9.7 19.4,10.4 L14.1,15.7")
            fill("M11.6,13.2 L14.1,15.7 L10.3,17 Z")
            stroke("M4.4,19.3 C6.2,17.9 8,20 9.8,18.6", 1.6f)
        }
    }
}

/** 构建 24×24 视口的 ImageVector。 */
private fun hwIcon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(build).build()

/** 添加一条描边路径（圆角端点/拐角，黑色，由 Icon tint 染色）。 */
private fun ImageVector.Builder.stroke(d: String, width: Float = 1.5f) {
    addPath(
        pathData = addPathNodes(d),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** 添加一条填充路径（黑色，可选 EvenOdd 挖空）。 */
private fun ImageVector.Builder.fill(d: String, evenOdd: Boolean = false) {
    addPath(
        pathData = addPathNodes(d),
        fill = SolidColor(Color.Black),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
    )
}

/** 生成套索虚线圆的 path 数据（8 段圆弧，半径 7.4，每段 26° 间隔 19°）。 */
private fun lassoDash(): String {
    val cx = 12f
    val cy = 12f
    val r = 7.4f
    val sb = StringBuilder()
    for (i in 0 until 8) {
        val a0 = Math.toRadians((i * 45).toDouble())
        val a1 = Math.toRadians((i * 45 + 26).toDouble())
        val x0 = cx + r * cos(a0).toFloat()
        val y0 = cy + r * sin(a0).toFloat()
        val x1 = cx + r * cos(a1).toFloat()
        val y1 = cy + r * sin(a1).toFloat()
        sb.append("M$x0,$y0 A$r,$r 0 0 1 $x1,$y1 ")
    }
    return sb.toString()
}
