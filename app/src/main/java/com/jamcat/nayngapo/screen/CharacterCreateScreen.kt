package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CustomChars
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.ui.CharSkin
import kotlin.math.hypot
import kotlin.math.min

/**
 * 캐릭터 추가 (대표님 2026-10-24):
 *  「추가는 이미지 파일을 첨부한 다음, 관절 부분을 직접 붙여 인간의 움직임대로 할 수 있어」
 *  「추가에서 스티커로 붙여넣기를 누르면 자기가 그린 테두리 안에 있는 것만 선택할 수 있어」
 *  「추가에서는 자동으로 사람 관절이 그려져. 관절을 더 추가는 못 하고, 움직일 수만 있어」
 * 사진 고르기 → (그대로 쓰기 | 스티커로 붙여넣기: 손가락으로 테두리 그리기) → 졸라맨 관절 11개가 저절로 놓이고 끌어서 옮기기 → 움직임 미리 보기 → 저장하고 되기.
 */
class CharacterCreateScreen(private val gameView: GameView) : Screen {

    private enum class Step { PICKING, MODE, LASSO, JOINTS, PREVIEW }
    private var step = Step.PICKING

    @Volatile private var pickedResult: Bitmap? = null
    @Volatile private var pickFinished = false
    private var image: Bitmap? = null
    private val lasso = Path()
    private val lassoPts = ArrayList<PointF>()
    private var lassoClosed = false
    /** 자동으로 선따기 켜짐 — 테두리를 닫으면 가까운 선에 맞춘 그림을 미리 보여 준다 */
    private var autoSnap = false
    private var snapped: Bitmap? = null
    /** 선따기 버튼을 누른 손가락은 테두리로 그리지 않는다 */
    private var skipStroke = false
    private val joints = ArrayList<PointF>()   // 그림 좌표
    private var rig: CharSkin.Rig? = null
    private var timer = 0f
    private var noticeText = ""
    private var noticeTimer = 0f

    // 그림이 놓인 자리 (화면) — 그림 좌표 ↔ 화면 좌표
    private val imgRect = RectF()
    private var imgScale = 1f

    private val btnA = RectF(); private val btnB = RectF(); private val btnC = RectF()

    private val bg = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val checker = Paint().apply { color = Color.rgb(232, 228, 220) }
    private val title = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val btn = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val btn2 = Paint().apply { color = Color.rgb(160, 220, 255); isAntiAlias = true }
    private val btnOff = Paint().apply { color = Color.rgb(214, 210, 204); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 4.5f; isAntiAlias = true }
    private val lassoPaint = Paint().apply { color = Color.rgb(240, 70, 120); style = Paint.Style.STROKE; strokeWidth = 7f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND; isAntiAlias = true; pathEffect = DashPathEffect(floatArrayOf(22f, 12f), 0f) }
    private val bonePaint = Paint().apply { color = Color.argb(200, 60, 130, 230); strokeWidth = 8f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val dotPaint = Paint().apply { color = Color.rgb(240, 70, 70); isAntiAlias = true }
    private val small = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val card = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val bmpPaint = Paint().apply { isFilterBitmap = true }

    init {
        gameView.pickImage { bmp -> pickedResult = bmp; pickFinished = true }
    }

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        if (step == Step.PICKING && pickFinished) {
            val b = pickedResult
            if (b == null) { gameView.showCharacterSelect(); return }
            image = b; step = Step.MODE
        }
    }

    private fun notice(t: String) { noticeText = t; noticeTimer = 2f }

    // ===== 그리기 =====
    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        title.textSize = h * 0.055f
        val head = when (step) {
            Step.PICKING -> "사진을 고르는 중…"
            Step.MODE -> "어떻게 쓸까요?"
            Step.LASSO -> when {
                lassoClosed && autoSnap -> "가까운 선에 맞춰 땄어요 — 이대로 쓸까요?"
                lassoClosed -> "이 테두리 안만 쓸까요?"
                autoSnap -> "✨ 대충 둘레를 그리면 가까운 선을 테두리로 해요"
                else -> "✂ 손가락으로 캐릭터 둘레를 그려요"
            }
            Step.JOINTS -> "관절을 끌어서 그림에 맞춰요"
            Step.PREVIEW -> "이렇게 움직여요!"
        }
        canvas.drawTr(head, w / 2f, h * 0.08f, title)
        val img = image ?: return

        // 그림 자리: 미리 보기 때는 왼쪽 절반, 아니면 가운데 크게
        val area = if (step == Step.PREVIEW) RectF(w * 0.04f, h * 0.12f, w * 0.46f, h * 0.84f) else RectF(w * 0.18f, h * 0.12f, w * 0.82f, h * 0.84f)
        imgScale = min(area.width() / img.width, area.height() / img.height)
        val iw = img.width * imgScale; val ih = img.height * imgScale
        imgRect.set(area.centerX() - iw / 2f, area.centerY() - ih / 2f, area.centerX() + iw / 2f, area.centerY() + ih / 2f)
        // 투명한 곳이 보이게 바둑판
        val cell = 24f
        var yy = imgRect.top; var row = 0
        while (yy < imgRect.bottom) {
            var xx = imgRect.left + (row % 2) * cell
            while (xx < imgRect.right) { canvas.drawRect(xx, yy, min(xx + cell, imgRect.right), min(yy + cell, imgRect.bottom), checker); xx += cell * 2 }
            yy += cell; row++
        }
        canvas.drawBitmap(if (step == Step.LASSO && lassoClosed) snapped ?: img else img, null, imgRect, bmpPaint)
        canvas.drawRect(imgRect, ink)

        when (step) {
            Step.MODE -> {
                drawBtn(canvas, btnA, w * 0.06f, h * 0.87f, w * 0.30f, "그대로 쓰기", btn2, h)
                drawBtn(canvas, btnB, w * 0.38f, h * 0.87f, w * 0.30f, "✂ 스티커로 붙여넣기", btn, h)
                drawBtn(canvas, btnC, w * 0.76f, h * 0.87f, w * 0.18f, "취소", btnOff, h)
            }
            Step.LASSO -> {
                if (!(lassoClosed && snapped != null)) canvas.drawPath(lasso, lassoPaint)
                if (lassoClosed) {
                    drawBtn(canvas, btnA, w * 0.10f, h * 0.87f, w * 0.30f, "다시 그리기", btnOff, h)
                    drawBtn(canvas, btnB, w * 0.60f, h * 0.87f, w * 0.30f, "다음 ▶", btn, h)
                    btnC.setEmpty()
                } else {
                    btnA.setEmpty(); btnB.setEmpty()
                    // 자동으로 선따기 (대표님 2026-10-24 「스티커로 하기에서는 자동으로 선따기도 있어」「대략적인 테두리를 그리면 가까운 선을 테두리로 해 줘」)
                    drawBtn(canvas, btnC, w * 0.30f, h * 0.87f, w * 0.40f, if (autoSnap) "✨ 자동으로 선따기: 켜짐" else "✨ 자동으로 선따기: 꺼짐", if (autoSnap) btn else btn2, h)
                }
            }
            Step.JOINTS -> {
                drawJoints(canvas, h)
                drawBtn(canvas, btnA, w * 0.04f, h * 0.87f, w * 0.24f, "처음 자리로", btnOff, h)
                drawBtn(canvas, btnB, w * 0.38f, h * 0.87f, w * 0.30f, "다음 ▶", btn, h)
                drawBtn(canvas, btnC, w * 0.78f, h * 0.87f, w * 0.18f, "취소", btnOff, h)
            }
            Step.PREVIEW -> {
                drawJoints(canvas, h)
                // 오른쪽: 걷기 → 주먹 → 발차기 → 점프를 번갈아
                val r = rig
                rectTmp.set(w * 0.52f, h * 0.14f, w * 0.96f, h * 0.82f)
                canvas.drawRoundRect(rectTmp, 20f, 20f, card); canvas.drawRoundRect(rectTmp, 20f, 20f, ink)
                if (r != null) {
                    val cyc = timer % 6f
                    val (pose, prog) = when {
                        cyc < 2f -> CharSkin.Pose.WALK to 0f
                        cyc < 3f -> CharSkin.Pose.PUNCH to ((cyc - 2f) / 0.5f).coerceAtMost(1f)
                        cyc < 4f -> CharSkin.Pose.KICK to ((cyc - 3f) / 0.6f).coerceAtMost(1f)
                        cyc < 4.5f -> CharSkin.Pose.JUMP_UP to 0f
                        cyc < 5f -> CharSkin.Pose.JUMP_DOWN to 0f
                        else -> CharSkin.Pose.IDLE to 0f
                    }
                    val lift = if (pose == CharSkin.Pose.JUMP_UP || pose == CharSkin.Pose.JUMP_DOWN) h * 0.08f else 0f
                    CharSkin.drawRig(canvas, r, rectTmp.centerX(), rectTmp.bottom - h * 0.06f - lift, rectTmp.height() * 0.70f, true, pose, prog, timer)
                    canvas.drawLine(rectTmp.left + 30f, rectTmp.bottom - h * 0.06f, rectTmp.right - 30f, rectTmp.bottom - h * 0.06f, ink)
                }
                drawBtn(canvas, btnA, w * 0.06f, h * 0.87f, w * 0.26f, "관절 다시 옮기기", btnOff, h)
                drawBtn(canvas, btnB, w * 0.56f, h * 0.87f, w * 0.36f, "저장하고 되기 ✓", btn, h)
                btnC.setEmpty()
            }
            else -> Unit
        }

        if (noticeTimer > 0f) {
            small.textSize = h * 0.038f
            val nw = small.measureTr(noticeText) + 60f
            rectTmp.set(w / 2f - nw / 2f, h * 0.44f, w / 2f + nw / 2f, h * 0.44f + h * 0.08f)
            canvas.drawRoundRect(rectTmp, 20f, 20f, card); canvas.drawRoundRect(rectTmp, 20f, 20f, ink)
            canvas.drawTr(noticeText, w / 2f, rectTmp.centerY() + h * 0.013f, small)
        }
    }

    private val rectTmp = RectF()

    private fun drawBtn(canvas: Canvas, r: RectF, x: Float, y: Float, bw: Float, label: String, p: Paint, h: Float) {
        r.set(x, y, x + bw, y + h * 0.10f)
        canvas.drawRoundRect(r, 26f, 26f, p); canvas.drawRoundRect(r, 26f, 26f, ink)
        small.textSize = h * 0.040f
        canvas.drawTr(label, r.centerX(), r.centerY() + h * 0.014f, small)
    }

    private fun sx(p: PointF) = imgRect.left + p.x * imgScale
    private fun sy(p: PointF) = imgRect.top + p.y * imgScale

    private val bonesFrom = intArrayOf(2, 1, 1, 3, 1, 5, 2, 7, 2, 9)
    private val bonesTo = intArrayOf(1, 0, 3, 4, 5, 6, 7, 8, 9, 10)

    private fun drawJoints(canvas: Canvas, h: Float) {
        for (b in bonesFrom.indices) {
            val f = bonesFrom[b]; val t = bonesTo[b]
            if (f < joints.size && t < joints.size) canvas.drawLine(sx(joints[f]), sy(joints[f]), sx(joints[t]), sy(joints[t]), bonePaint)
        }
        small.textSize = h * 0.026f
        for ((i, p) in joints.withIndex()) {
            val rr = if (i == dragIdx) 26f else 18f   // 손가락으로 잡기 쉽게 크게
            canvas.drawCircle(sx(p), sy(p), rr, dotPaint); canvas.drawCircle(sx(p), sy(p), rr, ink)
            canvas.drawTr(CustomChars.JOINT_NAMES[i], sx(p), sy(p) - 30f, small)
        }
    }

    /** 졸라맨 관절 자리 (가로 = 키 기준 -0.3~0.3, 세로 0~1) — 관절 순서는 CustomChars.JOINT_NAMES */
    private val stick = floatArrayOf(0f, 0.08f, 0f, 0.22f, 0f, 0.55f, -0.16f, 0.36f, -0.24f, 0.50f, 0.16f, 0.36f, 0.24f, 0.50f, -0.10f, 0.76f, -0.14f, 0.98f, 0.10f, 0.76f, 0.14f, 0.98f)

    /** 그림에서 보이는 부분(투명 아닌 곳)에 맞춰 졸라맨 관절을 저절로 놓는다 */
    private fun autoJoints() {
        val img = image ?: return
        val w = img.width; val hh = img.height
        val px = IntArray(w * hh); img.getPixels(px, 0, w, 0, 0, w, hh)
        var l = w; var t = hh; var rr = -1; var b = -1
        for (y in 0 until hh) for (x in 0 until w) if ((px[y * w + x] ushr 24) >= 20) { if (x < l) l = x; if (x > rr) rr = x; if (y < t) t = y; if (y > b) b = y }
        if (rr < 0) { l = 0; t = 0; rr = w - 1; b = hh - 1 }
        val bh = (b - t).toFloat(); val cx = (l + rr) / 2f
        // 그림이 납작하면 가로도 그림 폭에 맞춘다
        val sxw = minOf(bh, (rr - l) / 0.6f)
        joints.clear()
        for (k in 0 until CustomChars.JOINTS)
            joints.add(PointF((cx + stick[k * 2] * sxw).coerceIn(0f, w - 1f), (t + stick[k * 2 + 1] * bh).coerceIn(0f, hh - 1f)))
    }

    private var dragIdx = -1

    // ===== 터치 =====
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        if (step == Step.LASSO && !lassoClosed) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !btnC.isEmpty && btnC.contains(x, y)) { autoSnap = !autoSnap; skipStroke = true; return true }
            if (skipStroke) { if (event.actionMasked == MotionEvent.ACTION_UP) skipStroke = false; return true }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lasso.reset(); lassoPts.clear(); lasso.moveTo(x, y); lassoPts.add(PointF(x, y)) }
                MotionEvent.ACTION_MOVE -> {
                    val last = lassoPts.lastOrNull()
                    if (last == null || hypot(x - last.x, y - last.y) > 6f) { lasso.lineTo(x, y); lassoPts.add(PointF(x, y)) }
                }
                MotionEvent.ACTION_UP -> {
                    if (lassoPts.size < 12) { notice("캐릭터 둘레를 한 바퀴 크게 그려요"); lasso.reset(); lassoPts.clear() }
                    else { lasso.close(); lassoClosed = true; snapped = if (autoSnap) snapToLines() else null }
                }
            }
            return true
        }
        if (step == Step.JOINTS) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 가장 가까운 관절을 잡는다 (손가락 60px 안)
                    var best = -1; var bd = 60f
                    for ((i, p) in joints.withIndex()) { val d = hypot(x - sx(p), y - sy(p)); if (d < bd) { bd = d; best = i } }
                    dragIdx = best
                }
                MotionEvent.ACTION_MOVE -> if (dragIdx >= 0) {
                    val img = image
                    if (img != null) joints[dragIdx].set(((x - imgRect.left) / imgScale).coerceIn(0f, img.width - 1f), ((y - imgRect.top) / imgScale).coerceIn(0f, img.height - 1f))
                }
                MotionEvent.ACTION_UP -> {
                    if (dragIdx < 0) when {
                        btnA.contains(x, y) -> autoJoints()
                        btnB.contains(x, y) -> makePreview()
                        btnC.contains(x, y) -> gameView.showCharacterSelect()
                    }
                    dragIdx = -1
                }
            }
            return true
        }
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        when (step) {
            Step.MODE -> when {
                btnA.contains(x, y) -> { autoJoints(); step = Step.JOINTS }
                btnB.contains(x, y) -> { step = Step.LASSO; lasso.reset(); lassoPts.clear(); lassoClosed = false }
                btnC.contains(x, y) -> gameView.showCharacterSelect()
            }
            Step.LASSO -> when {
                btnA.contains(x, y) -> { lasso.reset(); lassoPts.clear(); lassoClosed = false; snapped = null }
                btnB.contains(x, y) -> { val sn = snapped; if (sn != null) useSnapped(sn) else cutSticker() }
            }
            Step.PREVIEW -> when {
                btnA.contains(x, y) -> { rig = null; step = Step.JOINTS }   // 관절은 그대로 두고 다시 옮기기
                btnB.contains(x, y) -> save()
            }
            else -> Unit
        }
        return true
    }

    /** 테두리 안만 남기고 잘라낸다 (바깥은 투명) */
    private fun cutSticker() {
        val img = image ?: return
        // 화면 테두리 → 그림 좌표
        val path = Path()
        for ((i, p) in lassoPts.withIndex()) {
            val ix = (p.x - imgRect.left) / imgScale; val iy = (p.y - imgRect.top) / imgScale
            if (i == 0) path.moveTo(ix, iy) else path.lineTo(ix, iy)
        }
        path.close()
        val b = RectF(); path.computeBounds(b, true)
        b.intersect(0f, 0f, img.width.toFloat(), img.height.toFloat())
        if (b.width() < 10f || b.height() < 10f) { notice("테두리가 그림 밖이에요 — 다시 그려요"); lasso.reset(); lassoPts.clear(); lassoClosed = false; return }
        val out = Bitmap.createBitmap(b.width().toInt(), b.height().toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.translate(-b.left, -b.top)
        c.clipPath(path)
        c.drawBitmap(img, 0f, 0f, null)
        image = out
        autoJoints()
        step = Step.JOINTS
    }

    /** 손가락 테두리를 그림 좌표 Path 로 */
    private fun lassoInImage(): Path {
        val path = Path()
        for ((i, p) in lassoPts.withIndex()) {
            val ix = (p.x - imgRect.left) / imgScale; val iy = (p.y - imgRect.top) / imgScale
            if (i == 0) path.moveTo(ix, iy) else path.lineTo(ix, iy)
        }
        path.close()
        return path
    }

    /**
     * 자동으로 선따기 (대표님 2026-10-24 「대략적인 테두리를 그리면 가까운 선을 테두리로 해 줘」):
     * 그린 테두리 둘레(안팎으로 띠)에서, 바깥부터 번져 들어가다 선(진한 경계)을 만나면 멈춘다 — 멈춘 자리가 새 테두리.
     * 띠보다 깊은 안쪽은 늘 남고, 띠보다 먼 바깥은 늘 지운다. 띠 폭·선 기준은 Claude 판단. 결과는 그림과 같은 크기(바깥은 투명).
     */
    private fun snapToLines(): Bitmap? {
        val img = image ?: return null
        val w = img.width; val hh = img.height
        val path = lassoInImage()
        val band = (maxOf(w, hh) * 0.10f).coerceAtLeast(16f)   // 그린 테두리에서 안팎으로 이만큼 안의 선에 붙는다
        // 안쪽(채우기)·띠(굵은 선) 마스크
        val inside = Bitmap.createBitmap(w, hh, Bitmap.Config.ALPHA_8)
        Canvas(inside).drawPath(path, Paint().apply { style = Paint.Style.FILL })
        val bandBmp = Bitmap.createBitmap(w, hh, Bitmap.Config.ALPHA_8)
        Canvas(bandBmp).drawPath(path, Paint().apply { style = Paint.Style.STROKE; strokeWidth = band * 2f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND })
        // ALPHA_8 은 줄 끝이 채워질 수 있어 줄 길이(rowBytes)로 읽는다
        val rbIn = inside.rowBytes; val rbBand = bandBmp.rowBytes
        val inA = ByteArray(rbIn * hh); inside.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(inA))
        val bandA = ByteArray(rbBand * hh); bandBmp.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(bandA))
        val px = IntArray(w * hh); img.getPixels(px, 0, w, 0, 0, w, hh)
        // 밝기 + 선(밝기 차이가 큰 곳·진한 곳)
        val lum = IntArray(w * hh) { val c = px[it]; if ((c ushr 24) < 20) 255 else (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10 }
        fun isLine(i: Int): Boolean {
            val x = i % w; val y = i / w
            if (x == 0 || y == 0 || x == w - 1 || y == hh - 1) return false
            val gx = lum[i + 1] - lum[i - 1]; val gy = lum[i + w] - lum[i - w]
            return gx * gx + gy * gy > LINE_GRAD * LINE_GRAD || lum[i] < LINE_DARK
        }
        val bg = BooleanArray(w * hh)
        val queue = IntArray(w * hh); var head = 0; var tail = 0
        fun inBand(i: Int) = bandA[(i / w) * rbBand + i % w].toInt() != 0
        fun isIn(i: Int) = inA[(i / w) * rbIn + i % w].toInt() != 0
        // 띠 밖의 바깥은 모두 배경 (출발점)
        for (i in 0 until w * hh) if (!isIn(i) && !inBand(i)) { bg[i] = true; queue[tail++] = i }
        fun push(i: Int) {
            if (bg[i]) return
            if (!inBand(i)) return            // 띠 밖 안쪽은 늘 남긴다
            if (isLine(i)) return             // 선에서 멈춘다
            bg[i] = true; queue[tail++] = i
        }
        // 그림 가장자리가 띠 안이면 거기서도 출발
        for (x in 0 until w) { push(x); push((hh - 1) * w + x) }
        for (y in 0 until hh) { push(y * w); push(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < hh - 1) push(i + w)
        }
        var kept = 0
        for (i in 0 until w * hh) if (bg[i]) px[i] = 0 else if ((px[i] ushr 24) >= 20) kept++
        if (kept < 50) return null
        return Bitmap.createBitmap(px, w, hh, Bitmap.Config.ARGB_8888)
    }

    /** 자동으로 딴 그림: 보이는 부분만 잘라 관절 단계로 */
    private fun useSnapped(sn: Bitmap) {
        val w = sn.width; val hh = sn.height
        val px = IntArray(w * hh); sn.getPixels(px, 0, w, 0, 0, w, hh)
        var l = w; var t = hh; var rr = -1; var b = -1
        for (y in 0 until hh) for (x in 0 until w) if ((px[y * w + x] ushr 24) >= 20) { if (x < l) l = x; if (x > rr) rr = x; if (y < t) t = y; if (y > b) b = y }
        if (rr < 0) { notice("남은 그림이 없어요 — 다시 그려요"); return }
        image = Bitmap.createBitmap(sn, l, t, rr - l + 1, b - t + 1)
        snapped = null
        autoJoints()
        step = Step.JOINTS
    }

    private fun makePreview() {
        val img = image ?: return
        val arr = FloatArray(CustomChars.JOINTS * 2) { i -> if (i % 2 == 0) joints[i / 2].x else joints[i / 2].y }
        rig = CharSkin.buildRig(img, arr)
        step = Step.PREVIEW
        timer = 0f
    }

    private fun save() {
        val img = image ?: return
        val arr = FloatArray(CustomChars.JOINTS * 2) { i -> if (i % 2 == 0) joints[i / 2].x else joints[i / 2].y }
        val c = CustomChars.add(img, arr)
        GameProgress.selectHero(c.id)
        SoundManager.playCatHit()
        gameView.showCharacterSelect()
    }

    private companion object {
        /** 자동 선따기: 선으로 볼 밝기 차이(이웃 점끼리) · 이보다 어두우면 선 */
        const val LINE_GRAD = 70
        const val LINE_DARK = 90
    }

    override fun onBack(): Boolean {
        when (step) {
            Step.PREVIEW -> { rig = null; step = Step.JOINTS }
            Step.JOINTS -> gameView.showCharacterSelect()
            Step.LASSO -> step = Step.MODE
            else -> gameView.showCharacterSelect()
        }
        return true
    }
}
