package com.yhx.notices.domain.canvas

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 墨迹几何引擎：把「中心线 + 每点半径」转换为可直接填充的闭合轮廓多边形。
 *
 * 流程：去重 → Catmull-Rom 样条重采样（消除折线锯齿）→ 沿法线左右偏移生成
 * 双侧轮廓 → 两端加圆头帽（或平头帽）→ 闭合。输出为扁平 [x0,y0,x1,y1,...]，
 * 采样足够密（默认步长 2.5 世界单位），填充后视觉上即为光滑曲线。
 * 纯 Kotlin 无平台依赖，Compose 与 android.graphics 两端共用。
 */
object InkGeometry {

    /** 圆头帽细分段数。 */
    private const val CAP_SEGMENTS = 8

    /**
     * 生成笔迹轮廓多边形。
     *
     * @param points 中心线扁平坐标 [x0,y0,x1,y1,...]
     * @param radii 每个中心点的半径（数量 = points.size/2；不足时用最后一个补齐）
     * @param step 重采样步长（世界单位），越小越光滑
     * @param roundCaps 两端是否圆头（荧光笔用平头）
     * @return 闭合轮廓扁平坐标；点数不足时返回空数组
     */
    fun strokeOutline(
        points: List<Float>,
        radii: List<Float>,
        step: Float = 2.5f,
        roundCaps: Boolean = true,
    ): FloatArray {
        val n = points.size / 2
        if (n == 0) return FloatArray(0)

        // 去除过近的重复点，半径取较大者保笔锋
        val xs = ArrayList<Float>(n)
        val ys = ArrayList<Float>(n)
        val rs = ArrayList<Float>(n)
        for (i in 0 until n) {
            val x = points[i * 2]
            val y = points[i * 2 + 1]
            val r = radii.getOrElse(i) { radii.lastOrNull() ?: 1f }.coerceAtLeast(0.1f)
            if (xs.isNotEmpty() && hypot(x - xs.last(), y - ys.last()) < 0.05f) {
                rs[rs.size - 1] = maxOf(rs.last(), r)
            } else {
                xs.add(x); ys.add(y); rs.add(r)
            }
        }

        // 单点：直接输出圆
        if (xs.size == 1) return circle(xs[0], ys[0], rs[0])

        // Catmull-Rom 重采样中心线与半径
        val sx = ArrayList<Float>(xs.size * 4)
        val sy = ArrayList<Float>(xs.size * 4)
        val sr = ArrayList<Float>(xs.size * 4)
        for (i in 0 until xs.size - 1) {
            val p0 = (i - 1).coerceAtLeast(0)
            val p3 = (i + 2).coerceAtMost(xs.size - 1)
            val segLen = hypot(xs[i + 1] - xs[i], ys[i + 1] - ys[i])
            val divisions = ceil(segLen / step).toInt().coerceIn(1, 64)
            val tStart = if (i == 0) 0 else 1 // 非首段跳过 t=0 避免重复点
            for (k in tStart..divisions) {
                val t = k / divisions.toFloat()
                sx.add(catmullRom(xs[p0], xs[i], xs[i + 1], xs[p3], t))
                sy.add(catmullRom(ys[p0], ys[i], ys[i + 1], ys[p3], t))
                sr.add(rs[i] + (rs[i + 1] - rs[i]) * t)
            }
        }

        val m = sx.size
        if (m < 2) return circle(xs[0], ys[0], rs[0])

        // 中央差分求切线 → 法线（切线逆时针旋转 90°）
        val nxArr = FloatArray(m)
        val nyArr = FloatArray(m)
        for (i in 0 until m) {
            val ia = (i - 1).coerceAtLeast(0)
            val ib = (i + 1).coerceAtMost(m - 1)
            var tx = sx[ib] - sx[ia]
            var ty = sy[ib] - sy[ia]
            val len = hypot(tx, ty)
            if (len < 1e-4f) { tx = 1f; ty = 0f } else { tx /= len; ty /= len }
            nxArr[i] = -ty
            nyArr[i] = tx
        }

        val out = ArrayList<Float>(m * 4 + CAP_SEGMENTS * 4)
        // 左侧（+n）正向
        for (i in 0 until m) {
            out.add(sx[i] + nxArr[i] * sr[i])
            out.add(sy[i] + nyArr[i] * sr[i])
        }
        // 末端帽：从 +n 经 +t 转到 -n
        if (roundCaps) {
            val theta = atan2(nyArr[m - 1].toDouble(), nxArr[m - 1].toDouble())
            for (k in 1 until CAP_SEGMENTS) {
                val ang = theta - PI * k / CAP_SEGMENTS
                out.add(sx[m - 1] + (cos(ang) * sr[m - 1]).toFloat())
                out.add(sy[m - 1] + (sin(ang) * sr[m - 1]).toFloat())
            }
        }
        // 右侧（-n）反向
        for (i in m - 1 downTo 0) {
            out.add(sx[i] - nxArr[i] * sr[i])
            out.add(sy[i] - nyArr[i] * sr[i])
        }
        // 起端帽：从 -n 经 -t 转回 +n
        if (roundCaps) {
            val theta = atan2(nyArr[0].toDouble(), nxArr[0].toDouble()) - PI
            for (k in 1 until CAP_SEGMENTS) {
                val ang = theta - PI * k / CAP_SEGMENTS
                out.add(sx[0] + (cos(ang) * sr[0]).toFloat())
                out.add(sy[0] + (sin(ang) * sr[0]).toFloat())
            }
        }
        return out.toFloatArray()
    }

    /**
     * 斜口荧光笔笔宽：荧光笔笔尖是固定约 -45°(135°) 的斜方头。
     * 运笔方向与笔尖平行时细、垂直时宽 → 横划成宽带、竖划成细线。
     *
     * @param baseW 满宽（运笔垂直于笔尖时的宽度）
     * @param dirAngle 当前运笔方向角（弧度，atan2(dy,dx)）
     * @param nibAngle 笔尖朝向角（弧度，默认 135° = 3π/4，即 -45° 斜方头）
     * @param chiselMin 平行运笔时的最小宽度比例（0..1）
     */
    fun chiselWidth(
        baseW: Float,
        dirAngle: Float,
        nibAngle: Float = (3.0 * PI / 4.0).toFloat(),
        chiselMin: Float = 0.35f,
    ): Float {
        val factor = chiselMin + (1f - chiselMin) * kotlin.math.abs(sin(dirAngle - nibAngle))
        return baseW * factor
    }

    /** 收笔笔锋：把最后几个半径按比例递减，模拟提笔。 */
    fun taperTail(radii: MutableList<Float>, tailCount: Int = 3) {
        if (radii.size < tailCount + 2) return
        val factors = floatArrayOf(0.8f, 0.6f, 0.4f)
        for (k in 0 until tailCount) {
            val idx = radii.size - tailCount + k
            radii[idx] = radii[idx] * factors.getOrElse(k) { 0.4f }
        }
    }

    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            (2f * p1) + (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
    }

    private fun circle(cx: Float, cy: Float, r: Float): FloatArray {
        val seg = 16
        val out = FloatArray(seg * 2)
        for (k in 0 until seg) {
            val ang = 2 * PI * k / seg
            out[k * 2] = cx + (cos(ang) * r).toFloat()
            out[k * 2 + 1] = cy + (sin(ang) * r).toFloat()
        }
        return out
    }
}
