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
 *  - 고양이: 내 편 치즈냥이 그림을 통통 튀기고 기울여 움직인다 (공격은 앞으로 덮친다)
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
    private val bmpPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val tmpRect = RectF()

    private fun drawCat(canvas: Canvas, footX: Float, footY: Float, h: Float, right: Boolean, pose: Pose, prog: Float, t: Float) {
        val bmp = Heroes.playerCat()?.let { teamCatSprite(it) } ?: SpriteManager.allyCat
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
        canvas.restore()
        // 앞손 = 얼굴 앞쪽
        val dir = if (right) 1f else -1f
        lastHand.set(footX + dir * (cw * 0.42f + dx), footY - ch * 0.42f + dy)
    }

    /** 캣타워 냥이의 그림 (내 편, 갑옷 없이) */
    fun teamCatSprite(c: com.jamcat.nayngapo.engine.CatTeam.TeamCat): Bitmap = when (c.kind) {
        com.jamcat.nayngapo.entity.AllyCat.Kind.BLUE -> SpriteManager.blueCatAlly
        com.jamcat.nayngapo.entity.AllyCat.Kind.GREEN -> SpriteManager.greenCatAlly
        com.jamcat.nayngapo.entity.AllyCat.Kind.NORMAL -> c.coat.ally
        com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING -> SpriteManager.flyingCatAlly
        com.jamcat.nayngapo.entity.AllyCat.Kind.GUN -> SpriteManager.gunCatAlly
        com.jamcat.nayngapo.entity.AllyCat.Kind.BOAT -> c.coat.boatAlly
        else -> com.jamcat.nayngapo.entity.AllyCat.extraSprite(c.kind, c.coat) ?: c.coat.ally
    }

    // ===== 내가 만든 캐릭터 =====
    // 그림을 조각내지 않고 그물(메시)처럼 휘게 한다 (대표님 2026-10-24 「관절 부분만 잘려. 원래 모양이 유지돼야 해」):
    // 그림 위에 촘촘한 격자를 깔고, 격자점마다 가까운 뼈를 따라 움직이게(가까울수록 많이) 섞어서 관절에서 부드럽게 굽는다.
    // 자세는 '원래 그림 + 움직인 만큼'이라 서 있을 때는 그린 모양 그대로다.

    /** 뼈: 시작 관절 → 끝 관절 (관절 번호는 CustomChars.JOINT_NAMES) */
    private val BONE_FROM = intArrayOf(2, 1, 1, 3, 1, 5, 2, 7, 2, 9)
    private val BONE_TO = intArrayOf(1, 0, 3, 4, 5, 6, 7, 8, 9, 10)
    private const val TORSO = 0; private const val HEAD = 1
    private const val B_UARM = 2; private const val B_FARM = 3; private const val F_UARM = 4; private const val F_FARM = 5
    private const val B_THIGH = 6; private const val B_SHIN = 7; private const val F_THIGH = 8; private const val F_SHIN = 9
    private const val BONES = 10
    /** 격자 칸 수 */
    private const val MESH = 24

    /** 서 있는 기본 자세 (이 자세와의 차이만큼 원래 그림의 팔다리를 돌린다) */
    private val NEUTRAL = floatArrayOf(0f, 0f, 100f, 95f, 80f, 85f, 95f, 92f, 85f, 88f)

    class Rig(
        val bmp: Bitmap,
        val joints: FloatArray,
        val restAng: FloatArray, val len: FloatArray,
        /** 격자점(그림 좌표)과 점마다 뼈 무게 [점 * BONES + 뼈] */
        val verts: FloatArray, val weights: FloatArray,
        val figH: Float, val top: Float, val bottom: Float,
    )

    private val rigs = HashMap<String, Rig?>()

    fun forget(id: String) { rigs.remove(id) }

    private fun rigFor(id: String): Rig? = rigs.getOrPut(id) {
        val c = CustomChars.byId(id) ?: return@getOrPut null
        val bmp = CustomChars.bitmap(id) ?: return@getOrPut null
        buildRig(bmp, c.joints)
    }

    fun buildRig(src: Bitmap, j: FloatArray): Rig {
        val w = src.width; val hh = src.height
        // 보이는 부분의 위·아래 (발끝 맞추기용)
        val px = IntArray(w * hh); src.getPixels(px, 0, w, 0, 0, w, hh)
        var top = hh; var bottom = 0
        for (y in 0 until hh) for (x in 0 until w) if ((px[y * w + x] ushr 24) >= 20) { if (y < top) top = y; if (y > bottom) bottom = y }
        if (bottom < top) { top = 0; bottom = hh - 1 }
        val n = MESH + 1
        val verts = FloatArray(n * n * 2)
        val weights = FloatArray(n * n * BONES)
        val d = FloatArray(BONES)
        for (gy in 0 until n) for (gx in 0 until n) {
            val k = gy * n + gx
            val vx = gx * (w - 1f) / MESH; val vy = gy * (hh - 1f) / MESH
            verts[k * 2] = vx; verts[k * 2 + 1] = vy
            for (b in 0 until BONES) {
                var dist = segDist(vx, vy, j[BONE_FROM[b] * 2], j[BONE_FROM[b] * 2 + 1], j[BONE_TO[b] * 2], j[BONE_TO[b] * 2 + 1])
                if (b == TORSO) dist *= 0.8f
                d[b] = dist
            }
            // 가장 가까운 두 뼈만 섞는다 — 관절 둘레는 반반, 뼈 한가운데는 그 뼈를 그대로
            var b1 = 0; var b2 = 1
            if (d[b2] < d[b1]) { b1 = 1; b2 = 0 }
            for (b in 2 until BONES) { if (d[b] < d[b1]) { b2 = b1; b1 = b } else if (d[b] < d[b2]) b2 = b }
            val w1 = 1f / (d[b1] + 1f).let { it * it * it }; val w2 = 1f / (d[b2] + 1f).let { it * it * it }
            weights[k * BONES + b1] = w1 / (w1 + w2); weights[k * BONES + b2] = w2 / (w1 + w2)
        }
        val rest = FloatArray(BONES); val len = FloatArray(BONES)
        for (b in 0 until BONES) {
            val dx = j[BONE_TO[b] * 2] - j[BONE_FROM[b] * 2]; val dy = j[BONE_TO[b] * 2 + 1] - j[BONE_FROM[b] * 2 + 1]
            rest[b] = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            len[b] = hypot(dx, dy)
        }
        return Rig(src, j.copyOf(), rest, len, verts, weights, (bottom - top + 1).toFloat(), top.toFloat(), bottom.toFloat())
    }

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax; val vy = by - ay
        val l2 = vx * vx + vy * vy
        val u = if (l2 <= 0f) 0f else (((px - ax) * vx + (py - ay) * vy) / l2).coerceIn(0f, 1f)
        return hypot(px - (ax + vx * u), py - (ay + vy * u))
    }

    private val ang = FloatArray(BONES)
    private val jx = FloatArray(11); private val jy = FloatArray(11)

    /** 자세 → 뼈 각도 (오른쪽을 볼 때, 0° = 앞, 90° = 아래). 결과는 '원래 각도 + (자세 - 기본 자세)' */
    private fun posed(rig: Rig, pose: Pose, prog: Float, t: Float) {
        val e = sin(prog.coerceIn(0f, 1f) * Math.PI.toFloat())
        var lean = 0f
        fun set(bu: Float, bf: Float, fu: Float, ff: Float, bt: Float, bs: Float, ft: Float, fs: Float) {
            ang[B_UARM] = bu; ang[B_FARM] = bf; ang[F_UARM] = fu; ang[F_FARM] = ff
            ang[B_THIGH] = bt; ang[B_SHIN] = bs; ang[F_THIGH] = ft; ang[F_SHIN] = fs
        }
        when (pose) {
            Pose.IDLE -> { val b = sin(t * 3f) * 2f; set(100f + b, 95f + b, 80f - b, 85f - b, 95f, 92f, 85f, 88f) }
            Pose.WALK -> {
                val s = sin(t * 9f); val c = cos(t * 9f)
                set(100f - 22f * s, 95f - 22f * s, 80f + 22f * s, 85f + 22f * s,
                    95f + 25f * s, 92f + 25f * s + 15f * (1f - c) / 2f, 85f - 25f * s, 88f - 25f * s + 15f * (1f + c) / 2f)
                lean = 4f
            }
            Pose.JUMP_UP -> { set(40f, 30f, 20f, 10f, 115f, 140f, 50f, 100f); lean = -4f }
            Pose.JUMP_DOWN -> { set(60f, 50f, 40f, 30f, 100f, 115f, 70f, 95f) }
            Pose.PUNCH -> { set(100f + 25f * e, 95f - 20f * e, 80f - 75f * e, 85f - 80f * e, 105f, 98f, 75f, 95f); lean = 8f * e }
            Pose.KICK -> { set(80f, 60f, 110f, 80f, 100f, 95f, 85f - 80f * e, 88f - 80f * e); lean = -8f * e }
        }
        for (b in 2 until BONES) ang[b] = rig.restAng[b] + (ang[b] - NEUTRAL[b])
        ang[TORSO] = rig.restAng[TORSO] + lean
        ang[HEAD] = rig.restAng[HEAD] + lean
    }

    private val meshOut = FloatArray((MESH + 1) * (MESH + 1) * 2)
    private val cosB = FloatArray(BONES); private val sinB = FloatArray(BONES)

    fun drawRig(canvas: Canvas, rig: Rig, footX: Float, footY: Float, h: Float, right: Boolean, pose: Pose, prog: Float, t: Float) {
        posed(rig, pose, prog, t)
        val s = h / rig.figH
        // 골반(0,0)에서 관절 자리를 차례로
        jx[2] = 0f; jy[2] = 0f
        for (b in intArrayOf(TORSO, HEAD, B_UARM, B_FARM, F_UARM, F_FARM, B_THIGH, B_SHIN, F_THIGH, F_SHIN)) {
            val f = BONE_FROM[b]; val to = BONE_TO[b]
            val r = Math.toRadians(ang[b].toDouble())
            jx[to] = jx[f] + (cos(r) * rig.len[b] * s).toFloat()
            jy[to] = jy[f] + (sin(r) * rig.len[b] * s).toFloat()
        }
        for (b in 0 until BONES) {
            val r = Math.toRadians((ang[b] - rig.restAng[b]).toDouble())
            cosB[b] = cos(r).toFloat(); sinB[b] = sin(r).toFloat()
        }
        // 격자점마다: 뼈 b 의 '원래 시작 관절 기준 → 돌리고 키우고 → 지금 시작 관절' 을 무게대로 섞는다
        val n = MESH + 1
        var lowest = -Float.MAX_VALUE
        for (k in 0 until n * n) {
            val vx = rig.verts[k * 2]; val vy = rig.verts[k * 2 + 1]
            var ox = 0f; var oy = 0f
            for (b in 0 until BONES) {
                val wgt = rig.weights[k * BONES + b]
                if (wgt == 0f) continue
                val f = BONE_FROM[b]
                val dx = (vx - rig.joints[f * 2]) * s; val dy = (vy - rig.joints[f * 2 + 1]) * s
                ox += wgt * (jx[f] + dx * cosB[b] - dy * sinB[b])
                oy += wgt * (jy[f] + dx * sinB[b] + dy * cosB[b])
            }
            meshOut[k * 2] = ox; meshOut[k * 2 + 1] = oy
        }
        // 발끝: 원래 그림에서 발 관절 아래로 남는 만큼을 더해 낮은 발이 바닥에 닿게
        val footPad = (rig.bottom - maxOf(rig.joints[8 * 2 + 1], rig.joints[10 * 2 + 1])).coerceAtLeast(0f) * s
        lowest = maxOf(jy[8], jy[10]) + footPad
        val oy0 = footY - lowest
        canvas.save()
        canvas.translate(footX, oy0)
        if (!right) canvas.scale(-1f, 1f)
        canvas.drawBitmapMesh(rig.bmp, MESH, MESH, meshOut, 0, null, 0, bmpPaint)
        canvas.restore()
        val dir = if (right) 1f else -1f
        lastHand.set(footX + dir * jx[6], oy0 + jy[6])
    }
}
