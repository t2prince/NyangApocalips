package com.jamcat.nayngapo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.jamcat.nayngapo.engine.CustomChars
import com.jamcat.nayngapo.engine.Heroes
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 인간 대신 주인공이 되는 캐릭터 그리기 (대표님 2026-10-24 캐릭터 변경):
 *  - 고양이: 내 편 치즈냥이 그림을 통통 튀기고 기울여 움직인다 (공격은 앞으로 덮치며 할퀴기 자국)
 *  - 내가 만든 캐릭터: 찍은 관절대로 그림을 팔·다리·몸·머리 10조각으로 나눠, 조각을 돌려 인간처럼 움직인다
 * 졸라맨 그림이 놓일 자리(dst)를 받아 같은 키·같은 발끝에 그린다.
 */
object CharSkin {

    enum class Pose { IDLE, WALK, JUMP_UP, JUMP_DOWN, PUNCH, KICK }

    fun handles(hero: Heroes.Hero) = hero.id == Heroes.CAT_ID || hero.id.startsWith(Heroes.CUSTOM_PREFIX)

    /** 마지막으로 그린 앞손 자리 — 총·대검을 쥐는 곳 */
    val lastHand = PointF()

    /** 졸라맨 그림 안에서 몸이 차지하는 키 비율 (발끝은 아래 끝) */
    private const val FIG_H = 0.84f

    fun draw(canvas: Canvas, hero: Heroes.Hero, dst: RectF, facingRight: Boolean, pose: Pose, prog: Float, t: Float) {
        val footX = dst.centerX(); val footY = dst.bottom - dst.height() * 0.015f
        val h = dst.height() * FIG_H
        if (hero.id == Heroes.CAT_ID) drawCat(canvas, footX, footY, h, facingRight, pose, prog, t)
        else {
            val rig = rigFor(hero.id)
            if (rig == null) drawCat(canvas, footX, footY, h, facingRight, pose, prog, t)
            else drawRig(canvas, rig, footX, footY, h, facingRight, pose, prog, t)
        }
    }

    // ===== 고양이 =====
    private val slash = Paint().apply { color = Color.WHITE; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE; isAntiAlias = true }
    private val slashInk = Paint().apply { color = Color.rgb(40, 34, 30); strokeWidth = 12f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE; isAntiAlias = true }
    private val bmpPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val tmpRect = RectF()

    private fun drawCat(canvas: Canvas, footX: Float, footY: Float, h: Float, right: Boolean, pose: Pose, prog: Float, t: Float) {
        val bmp = SpriteManager.allyCat
        val ch = h * 0.62f; val cw = bmp.width * ch / bmp.height
        val e = sin(prog.coerceIn(0f, 1f) * Math.PI.toFloat())   // 공격 뻗기 0→1→0
        var dx = 0f; var dy = 0f; var rot = 0f; var sy = 1f
        when (pose) {
            Pose.IDLE -> sy = 1f + sin(t * 3f) * 0.02f
            Pose.WALK -> { dy = -abs(sin(t * 9f)) * h * 0.06f; rot = sin(t * 9f) * 4f }
            Pose.JUMP_UP -> rot = -12f
            Pose.JUMP_DOWN -> rot = 8f
            Pose.PUNCH -> { dx = e * h * 0.22f; rot = e * 10f }
            Pose.KICK -> { dx = e * h * 0.18f; dy = -e * h * 0.12f; rot = -e * 20f }
        }
        canvas.save()
        canvas.translate(footX, footY)
        if (!right) canvas.scale(-1f, 1f)
        canvas.translate(dx, dy)
        canvas.rotate(rot, 0f, -ch * 0.4f)
        canvas.scale(1f, sy)
        tmpRect.set(-cw / 2f, -ch, cw / 2f, 0f)
        canvas.drawBitmap(bmp, null, tmpRect, bmpPaint)
        // 할퀴기 자국 (덮치는 순간)
        if ((pose == Pose.PUNCH || pose == Pose.KICK) && e > 0.5f) {
            for (k in 0..2) {
                val x0 = cw * 0.45f + k * 16f; val y0 = -ch * 0.85f + k * 10f
                canvas.drawLine(x0, y0, x0 + 34f, y0 + ch * 0.45f, slashInk)
                canvas.drawLine(x0, y0, x0 + 34f, y0 + ch * 0.45f, slash)
            }
        }
        canvas.restore()
        // 앞손 = 얼굴 앞쪽
        val dir = if (right) 1f else -1f
        lastHand.set(footX + dir * (cw * 0.42f + dx), footY - ch * 0.42f + dy)
    }

    // ===== 내가 만든 캐릭터 =====

    /** 뼈: 시작 관절 → 끝 관절 (관절 번호는 CustomChars.JOINT_NAMES) */
    private val BONE_FROM = intArrayOf(2, 1, 1, 3, 1, 5, 2, 7, 2, 9)
    private val BONE_TO = intArrayOf(1, 0, 3, 4, 5, 6, 7, 8, 9, 10)
    private const val TORSO = 0; private const val HEAD = 1
    private const val B_UARM = 2; private const val B_FARM = 3; private const val F_UARM = 4; private const val F_FARM = 5
    private const val B_THIGH = 6; private const val B_SHIN = 7; private const val F_THIGH = 8; private const val F_SHIN = 9
    /** 그리는 순서: 뒤 팔·다리 → 몸 → 머리 → 앞 다리 → 앞 팔 */
    private val DRAW_ORDER = intArrayOf(B_UARM, B_FARM, B_THIGH, B_SHIN, TORSO, HEAD, F_THIGH, F_SHIN, F_UARM, F_FARM)

    class Rig(
        val joints: FloatArray,
        val parts: Array<Bitmap?>, val partLeft: IntArray, val partTop: IntArray,
        val restAng: FloatArray, val len: FloatArray, val figH: Float,
    )

    private val rigs = HashMap<String, Rig?>()

    fun forget(id: String) { rigs.remove(id) }

    private fun rigFor(id: String): Rig? = rigs.getOrPut(id) {
        val c = CustomChars.byId(id) ?: return@getOrPut null
        val bmp = CustomChars.bitmap(id) ?: return@getOrPut null
        buildRig(bmp, c.joints)
    }

    /** 그림의 각 점을 가장 가까운 뼈에 나눠 조각 그림을 만든다 */
    fun buildRig(src: Bitmap, j: FloatArray): Rig {
        val w = src.width; val hh = src.height
        val px = IntArray(w * hh); src.getPixels(px, 0, w, 0, 0, w, hh)
        val owner = IntArray(w * hh) { -1 }
        val minX = IntArray(10) { w }; val minY = IntArray(10) { hh }; val maxX = IntArray(10) { -1 }; val maxY = IntArray(10) { -1 }
        var top = hh; var bottom = 0
        for (y in 0 until hh) for (x in 0 until w) {
            val i = y * w + x
            if ((px[i] ushr 24) < 20) continue
            var best = -1; var bd = Float.MAX_VALUE
            for (b in 0 until 10) {
                var d = segDist(x.toFloat(), y.toFloat(), j[BONE_FROM[b] * 2], j[BONE_FROM[b] * 2 + 1], j[BONE_TO[b] * 2], j[BONE_TO[b] * 2 + 1])
                if (b == TORSO) d *= 0.85f   // 몸통 둘레는 몸통 쪽으로 조금 더
                if (d < bd) { bd = d; best = b }
            }
            owner[i] = best
            if (x < minX[best]) minX[best] = x; if (x > maxX[best]) maxX[best] = x
            if (y < minY[best]) minY[best] = y; if (y > maxY[best]) maxY[best] = y
            if (y < top) top = y; if (y > bottom) bottom = y
        }
        val parts = arrayOfNulls<Bitmap>(10); val pl = IntArray(10); val pt = IntArray(10)
        for (b in 0 until 10) {
            if (maxX[b] < 0) continue
            val bw = maxX[b] - minX[b] + 1; val bh = maxY[b] - minY[b] + 1
            val out = IntArray(bw * bh)
            for (y in 0 until bh) for (x in 0 until bw) {
                val i = (y + minY[b]) * w + (x + minX[b])
                if (owner[i] == b) out[y * bw + x] = px[i]
            }
            parts[b] = Bitmap.createBitmap(out, bw, bh, Bitmap.Config.ARGB_8888)
            pl[b] = minX[b]; pt[b] = minY[b]
        }
        val rest = FloatArray(10); val len = FloatArray(10)
        for (b in 0 until 10) {
            val dx = j[BONE_TO[b] * 2] - j[BONE_FROM[b] * 2]; val dy = j[BONE_TO[b] * 2 + 1] - j[BONE_FROM[b] * 2 + 1]
            rest[b] = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            len[b] = hypot(dx, dy)
        }
        return Rig(j, parts, pl, pt, rest, len, (bottom - top + 1).coerceAtLeast(1).toFloat())
    }

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax; val vy = by - ay
        val l2 = vx * vx + vy * vy
        val u = if (l2 <= 0f) 0f else (((px - ax) * vx + (py - ay) * vy) / l2).coerceIn(0f, 1f)
        return hypot(px - (ax + vx * u), py - (ay + vy * u))
    }

    private val ang = FloatArray(10)
    private val jx = FloatArray(11); private val jy = FloatArray(11)

    /** 자세 → 뼈 각도 (오른쪽을 볼 때, 0° = 앞, 90° = 아래). 몸통·머리는 원래 각도에서 기울기만 */
    private fun posed(rig: Rig, pose: Pose, prog: Float, t: Float) {
        val e = sin(prog.coerceIn(0f, 1f) * Math.PI.toFloat())
        var lean = 0f
        fun set(bu: Float, bf: Float, fu: Float, ff: Float, bt: Float, bs: Float, ft: Float, fs: Float) {
            ang[B_UARM] = bu; ang[B_FARM] = bf; ang[F_UARM] = fu; ang[F_FARM] = ff
            ang[B_THIGH] = bt; ang[B_SHIN] = bs; ang[F_THIGH] = ft; ang[F_SHIN] = fs
        }
        when (pose) {
            Pose.IDLE -> { val b = sin(t * 3f) * 3f; set(100f + b, 95f + b, 80f - b, 85f - b, 95f, 92f, 85f, 88f) }
            Pose.WALK -> {
                val s = sin(t * 9f); val c = cos(t * 9f)
                set(90f - 28f * s, 70f - 28f * s, 90f + 28f * s, 70f + 28f * s,
                    90f + 30f * s, 95f + 30f * s + 18f * (1f - c) / 2f, 90f - 30f * s, 95f - 30f * s + 18f * (1f + c) / 2f)
                lean = 5f
            }
            Pose.JUMP_UP -> { set(-130f, -110f, -50f, -70f, 115f, 150f, 30f, 100f); lean = -5f }
            Pose.JUMP_DOWN -> { set(-160f, -140f, -20f, -10f, 100f, 120f, 60f, 95f) }
            Pose.PUNCH -> {
                set(100f + 30f * e, 95f - 35f * e, 70f - 70f * e, 85f - 85f * e, 115f, 100f, 70f, 100f)
                lean = 10f * e
            }
            Pose.KICK -> {
                set(60f, 20f, 120f, 60f, 100f, 95f, 85f - 90f * e, 88f - 93f * e)
                lean = -10f * e
            }
        }
        ang[TORSO] = rig.restAng[TORSO] + lean
        ang[HEAD] = rig.restAng[HEAD] + lean
    }

    private val m = android.graphics.Matrix()

    fun drawRig(canvas: Canvas, rig: Rig, footX: Float, footY: Float, h: Float, right: Boolean, pose: Pose, prog: Float, t: Float) {
        posed(rig, pose, prog, t)
        val s = h / rig.figH
        // 골반(0,0)에서 관절 자리를 차례로 계산
        jx[2] = 0f; jy[2] = 0f
        for (b in intArrayOf(TORSO, HEAD, B_UARM, B_FARM, F_UARM, F_FARM, B_THIGH, B_SHIN, F_THIGH, F_SHIN)) {
            val f = BONE_FROM[b]; val to = BONE_TO[b]
            val r = Math.toRadians(ang[b].toDouble())
            jx[to] = jx[f] + (cos(r) * rig.len[b] * s).toFloat()
            jy[to] = jy[f] + (sin(r) * rig.len[b] * s).toFloat()
        }
        // 낮은 쪽 발이 발끝 자리에 오게 (발 그림이 관절 아래로 조금 남는 것도 더한다)
        val footPad = (rig.figH - (maxOf(rig.joints[8 * 2 + 1], rig.joints[10 * 2 + 1]) - minTop(rig))).coerceAtLeast(0f) * s
        val oy = footY - footPad - maxOf(jy[8], jy[10])
        canvas.save()
        canvas.translate(footX, oy)
        if (!right) canvas.scale(-1f, 1f)
        for (b in DRAW_ORDER) {
            val part = rig.parts[b] ?: continue
            val f = BONE_FROM[b]
            // 원래 그림의 시작 관절 → 지금 시작 관절, 원래 각도 → 지금 각도
            m.reset()
            m.setTranslate(rig.partLeft[b] - rig.joints[f * 2], rig.partTop[b] - rig.joints[f * 2 + 1])
            m.postScale(s, s)
            m.postRotate(ang[b] - rig.restAng[b])
            m.postTranslate(jx[f], jy[f])
            canvas.drawBitmap(part, m, bmpPaint)
        }
        canvas.restore()
        val dir = if (right) 1f else -1f
        lastHand.set(footX + dir * jx[6], oy + jy[6])
    }

    private fun minTop(rig: Rig): Float {
        var top = Float.MAX_VALUE
        for (b in 0 until 10) if (rig.parts[b] != null) top = minOf(top, rig.partTop[b].toFloat())
        return top
    }
}
