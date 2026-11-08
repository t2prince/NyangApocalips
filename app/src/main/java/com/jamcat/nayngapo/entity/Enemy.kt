package com.jamcat.nayngapo.entity

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain
import kotlin.math.sign
import kotlin.math.abs
import kotlin.math.sin

abstract class Enemy(
    override var x: Float, var y: Float, baseHp: Int, baseAttack: Int
) : Crowdable {
    /** 야간 전투면 체력·공격력 두 배 (대표님 2026-10-17 「의사를 구하면 야간 전투를 할 수 있어, 냥이들의 공격력이랑 피가 두 배가 돼」) */
    val maxHp = if (nightBattle) baseHp * 2 else baseHp
    val attackPower = if (nightBattle) baseAttack * 2 else baseAttack
    override var crowdTimer = 0f
    override val width = 50f
    open val height = 50f
    var hp = maxHp
    var velocityX = 0f
    var facingRight = false
    var isDead = false
    var jumpZ = 0f

    /** 사망 모션 (쓰러진 뒤 잠깐 재생되고 나면 화면에서 사라진다) */
    val death = CatDeathAnim()
    /** 죽었지만 아직 사망 모션이 재생 중인가 */
    val isDying: Boolean get() = isDead && death.isActive
    /** 마지막으로 그린 스프라이트 — 사망 모션에서 뒤집어 재사용한다 */
    protected var lastSprite: Bitmap? = null

    /** 채집통에 잡혔다 (대표님 2026-10-10) — 쓰러지는 모습 없이 사라지고 내 팀 냥이가 된다 */
    var captured = false
        private set
    fun capture() { if (!isDead) { isDead = true; captured = true } }

    /** 보스인가 — 보스는 채집통으로 못 잡는다 (보스 클래스는 이름이 …Boss) */
    val isBoss: Boolean get() = this::class.java.simpleName.endsWith("Boss")

    /** 체력이 다했을 때 호출: 사망 상태로 바꾸고 사망 모션을 시작한다 */
    protected fun die() {
        if (isDead) return
        isDead = true
        death.start()
    }

    fun updateDeath(deltaTime: Float) = death.update(deltaTime)

    fun drawDeath(canvas: Canvas) {
        if (nightBattle) { drawDust(canvas); return }
        death.draw(canvas, lastSprite ?: SpriteManager.normalCat, facingRight,
            x + width / 2f, y + jumpZ + windLift)
    }

    /** 야간 전투에서 쓰러지면 뒤집히지 않고 그 자리에서 가루가 되어 흩어진다 (대표님 2026-10-17 「죽을 때는 아무 소리 안 나게, 뒤집히는 거 없이 가루가 나옴」) */
    private fun drawDust(canvas: Canvas) {
        val t = (1f - death.timer / CatDeathAnim.DURATION).coerceIn(0f, 1f)
        val cx = x + width / 2f
        val h = (lastSprite?.height?.toFloat() ?: height).coerceAtLeast(40f)
        val bw = (lastSprite?.width?.toFloat() ?: width)
        val baseY = y + jumpZ + windLift
        // 처음 잠깐은 몸이 옅어지며 사라지고, 가루가 위·옆으로 흩날린다
        lastSprite?.let { sp ->
            val face = nightEyes(sp, bigEyes)
            val bmp = if (facingRight) face else SpriteManager.getFlipped(face)
            dustPaint.alpha = (255 * (1f - t * 2.5f)).toInt().coerceIn(0, 255)
            if (dustPaint.alpha > 0) canvas.drawBitmap(bmp, cx - bmp.width / 2f, baseY - bmp.height + 8f, dustPaint)
        }
        for (k in 0 until DUST_COUNT) {
            val fx = ((k * 37) % 100) / 100f - 0.5f
            val fy = ((k * 61) % 100) / 100f
            val px = cx + fx * bw + fx * 140f * t + sin(k + t * 6f) * 10f
            val py = baseY - fy * h - t * (60f + (k % 5) * 30f)
            dustDot.alpha = (220 * (1f - t)).toInt().coerceIn(0, 255)
            dustDot.color = if (k % 3 == 0) Color.argb(dustDot.alpha, 255, 255, 255) else Color.argb(dustDot.alpha, 200, 196, 186)
            canvas.drawCircle(px, py, 3f + (k % 3) * 1.5f * (1f - t * 0.5f), dustDot)
        }
    }
    private val dustPaint = Paint()
    private val dustDot = Paint().apply { isAntiAlias = true }

    /** 야간 전투 눈: 큰 흰 눈을 가진 냥이(똥·털북숭·모루)는 눈동자를 하얗게 */
    open val bigEyes: Boolean get() = false

    /** 몸에 닿았을 때 플레이어가 잃는 체력 (잡몹 1, 보스 3 — 기획4) */
    open val contactDamage = 1

    /** 닿으면 하트 반 칸만 깎는가 (새끼 고양이) */
    open val halfHeart: Boolean get() = false

    /** 순간이동 등으로 잠시 사라져 몸이 닿지 않는 상태인지 */
    open val isTangible: Boolean get() = true

    /**
     * 몸 말고 따로 때리는 자리 (빙산냥이의 뻗은 얼음 주먹, 2026-10-25) — 인간이 닿으면 strikeDamage 만큼 맞는다. 없으면 null.
     * 호출 직후에만 유효한 사각형을 돌려준다.
     */
    open fun strikeBox(): RectF? = null
    open val strikeDamage: Int get() = contactDamage
    /** 몸·주먹에 맞은 인간이 얼어 못 움직이는 시간(초) — 빙산냥이 3초 (기획15). 0 = 안 얼린다 */
    open val freezeOnHit: Float get() = 0f

    /** 우리 편 얼음냥이의 얼음에 맞아 얼어 있는 남은 시간 (2026-10-25, Claude 판단) — 그동안 움직이지 않는다 */
    var frozenTimer = 0f
    /** 얼린다 — 보스는 안 언다 (Claude 판단) */
    fun freeze(seconds: Float) { if (!isBoss && !isDead) frozenTimer = maxOf(frozenTimer, seconds) }

    /** 얼어 있으면 몸에 옅은 하늘색 얼음 껍질을 덮어 그린다 */
    fun drawFrozen(canvas: Canvas) {
        if (frozenTimer <= 0f) return
        val h = maxOf(height, lastSpriteHeight)
        val cx = x + width / 2f
        val hw = maxOf(width, (lastSprite?.width ?: 0).toFloat()) * 0.55f
        frozenRect.set(cx - hw, y + jumpZ - h - 4f, cx + hw, y + 8f)
        canvas.drawRoundRect(frozenRect, 16f, 16f, frozenFill)
        canvas.drawRoundRect(frozenRect, 16f, 16f, frozenEdge)
        canvas.drawLine(frozenRect.left + 10f, frozenRect.top + 14f, frozenRect.left + 26f, frozenRect.top + 30f, frozenShine)
        canvas.drawLine(frozenRect.right - 14f, frozenRect.bottom - 30f, frozenRect.right - 24f, frozenRect.bottom - 16f, frozenShine)
    }

    // 전신 히트박스 (부유 연출 jumpZ 포함): 경사 지형에서도 근접 공격이 빗나가지 않는다
    private val hitBoxRect = RectF()
    /** 전신 히트박스 — 매 프레임 새로 만들지 않고 하나를 갱신해 돌려준다 (호출 직후에만 유효) */
    open val hitBox: RectF get() = hitBoxRect.apply { set(x, y + jumpZ - height, x + width, y + 10f) }

    /**
     * 피격 무적 시간. 공격 판정이 여러 프레임에 걸쳐 겹쳐도 한 번의 공격은 한 번만 적중시킨다
     * (디자인 시트의 HP 수치가 의미를 갖도록).
     */
    protected var hitCooldown = 0f

    /** 애니메이션 진행 시간 (프레임 선택용) */
    protected var animTime = 0f

    open fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        animTime += deltaTime
        updateHop(deltaTime)

        // 뒤에 멀리 남겨진 냥이는 플레이어를 쫓아온다 (한 방향 진행에서 클리어 불가 방지)
        if (x + width < playerX - Constants.ENEMY_CATCHUP_DISTANCE) {
            velocityX = Constants.ENEMY_CATCHUP_SPEED
        }
        // 횡뷰 플랫포머: 좌우로만 이동, 발은 항상 지형 표면 위 (공중 연출은 jumpZ)
        x += velocityX * deltaTime
        x = x.coerceIn(Constants.PLAY_AREA_LEFT + 5f, Constants.PLAY_AREA_RIGHT - width - 5f)
        val oldY = y
        y = Terrain.groundY(x + width / 2f)
        updateClimb(oldY, deltaTime)

        // 냥이는 자기 머리(바라보는) 방향으로만 걷는다 — 이동 중에는 시선이 이동 방향을 따라간다.
        // 멈춰 있을 때만 각 냥이가 정한 시선(보통 플레이어 쪽)을 유지한다.
        if (velocityX != 0f) facingRight = velocityX > 0f
    }

    abstract fun draw(canvas: Canvas)

    open fun takeDamage(amount: Int) {
        if (isDead || hitCooldown > 0f) return
        hitCooldown = HIT_COOLDOWN
        hp -= amount
        if (hp <= 0) die()
    }

    /** 애니메이션 프레임 선택 (fps 간격으로 순환) */
    protected fun frame(frames: List<Bitmap>, fps: Float): Bitmap =
        frames[((animTime * fps).toInt()) % frames.size]

    open fun getProjectiles(): List<Projectile> = emptyList()

    /** 새로 불러낸 냥이들 (여왕 벌냥이가 벌냥이를 부른다) — 화면이 매 프레임 가져가 스테이지에 넣는다 */
    open fun takeSpawns(): List<Enemy> = emptyList()

    /** 모래바람 등 환경 효과로 몸이 살짝 들리는 양 (음수 = 위로) */
    var windLift = 0f

    // 함정·구덩이를 뛰어넘는 도약 (연출용 오프셋)
    private var hopOffset = 0f
    /** 갑판처럼 높이가 확 바뀐 곳: 그림만 원래 높이에서 새 높이로 0.15초쯤 따라간다 */
    protected var climbOffset = 0f
    protected fun updateClimb(oldY: Float, deltaTime: Float) {
        if (abs(y - oldY) > 40f) climbOffset += oldY - y
        if (climbOffset != 0f) {
            val step = 700f * deltaTime
            climbOffset = if (abs(climbOffset) <= step) 0f else climbOffset - sign(climbOffset) * step
        }
    }
    private var hopVelocity = 0f

    /** 앞에 위험이 있을 때 호출 — 이미 떠 있으면 무시된다 */
    fun hopOverHazard() {
        if (hopOffset == 0f && hopVelocity == 0f) hopVelocity = HOP_VELOCITY
    }

    private fun updateHop(deltaTime: Float) {
        if (hopOffset == 0f && hopVelocity == 0f) return
        hopVelocity += HOP_GRAVITY * deltaTime
        hopOffset += hopVelocity * deltaTime
        if (hopOffset >= 0f) { hopOffset = 0f; hopVelocity = 0f }
    }

    protected fun drawSprite(canvas: Canvas, sprite: Bitmap) {
        val cx = x + width / 2f
        val drawY = y + jumpZ + windLift + hopOffset + climbOffset

        // Shadow (몸 크기에 비례)
        val sw = width * 0.45f
        scratchRect.set(cx - sw, y - 5f, cx + sw, y + 7f)
        canvas.drawOval(scratchRect, shadowPaint)

        // 원안 스프라이트는 오른쪽을 본다 — 왼쪽을 볼 때만 뒤집는다 (머리 방향 = 이동 방향)
        // 야간 전투: 눈이 하얗게 변한다 (눈동자를 하얗게 칠한 그림)
        val face = if (nightBattle) nightEyes(sprite, bigEyes) else sprite
        val drawBmp = if (facingRight) face else SpriteManager.getFlipped(face)
        val sx = cx - drawBmp.width / 2f
        val sy = drawY - drawBmp.height + 8f
        canvas.drawBitmap(drawBmp, sx, sy, null)
        lastSpriteHeight = drawBmp.height.toFloat()
        lastSprite = sprite
    }

    /** 방금 그린 스프라이트의 높이 — 체력 게이지를 머리 위로 띄우는 데 쓴다 */
    protected var lastSpriteHeight = 0f

    /** 적 체력 게이지: 머리 위에 몸 너비만큼 표시 */
    protected fun drawHpBar(canvas: Canvas) {
        val cx = x + width / 2f
        // 그림이 논리 높이보다 크면 그림 위로 올려 겹치지 않게 한다
        val barY = y + jumpZ - maxOf(height, lastSpriteHeight) - 16f
        val barW = width
        val barH = 7f
        val frac = (hp / maxHp.toFloat()).coerceIn(0f, 1f)
        // 체력 비율에 따라 초록 → 노랑 → 빨강
        barFgPaint.color = when {
            frac > 0.5f -> Color.rgb(90, 210, 90)
            frac > 0.25f -> Color.rgb(240, 200, 60)
            else -> Color.rgb(230, 70, 70)
        }
        canvas.drawRect(cx - barW / 2, barY, cx + barW / 2, barY + barH, barBgPaint)
        canvas.drawRect(cx - barW / 2, barY, cx - barW / 2 + barW * frac, barY + barH, barFgPaint)
        canvas.drawRect(cx - barW / 2, barY, cx + barW / 2, barY + barH, barOutlinePaint)
    }

    companion object {
        /** 지금 야간 전투인가 — 땅 스테이지에 들어갈 때 GameView 가 정한다 (다른 화면으로 가면 꺼진다) */
        @JvmStatic var nightBattle = false

        private val nightCache = HashMap<Bitmap, Bitmap>()
        private const val DARK = 90
        private const val WHITE = 225
        private const val DUST_COUNT = 36

        /**
         * 야간 전투의 눈이 하얀 그림 (그림마다 한 번 만들어 둔다).
         * - 보통 냥이: 눈은 흰 주둥이(가장 큰 흰 덩어리) 바로 위, 털에 둘러싸인 작은 검은 점 두 개 → 하얗게
         * - 큰 흰 눈 냥이(bigEyes: 똥·털북숭·모루냥이): 큰 흰 눈 안의 검은 눈동자 → 하얗게
         */
        fun nightEyes(src: Bitmap, bigEyes: Boolean = false): Bitmap = nightCache.getOrPut(src) {
            val w = src.width; val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            fun op(i: Int) = (px[i] ushr 24) > 200
            val dark = BooleanArray(w * h) { op(it) && Color.red(px[it]) < DARK && Color.green(px[it]) < DARK && Color.blue(px[it]) < DARK }
            val white = BooleanArray(w * h) { op(it) && Color.red(px[it]) > WHITE && Color.green(px[it]) > WHITE && Color.blue(px[it]) > WHITE }
            fun comps(mask: BooleanArray): List<IntArray> {
                val seen = BooleanArray(w * h); val out = mutableListOf<IntArray>()
                val q = IntArray(w * h)
                for (s0 in 0 until w * h) {
                    if (!mask[s0] || seen[s0]) continue
                    var head = 0; var tail = 0; q[tail++] = s0; seen[s0] = true
                    while (head < tail) {
                        val i = q[head++]; val x = i % w; val y = i / w
                        if (x > 0 && mask[i - 1] && !seen[i - 1]) { seen[i - 1] = true; q[tail++] = i - 1 }
                        if (x < w - 1 && mask[i + 1] && !seen[i + 1]) { seen[i + 1] = true; q[tail++] = i + 1 }
                        if (y > 0 && mask[i - w] && !seen[i - w]) { seen[i - w] = true; q[tail++] = i - w }
                        if (y < h - 1 && mask[i + w] && !seen[i + w]) { seen[i + w] = true; q[tail++] = i + w }
                    }
                    out.add(q.copyOf(tail))
                }
                return out
            }
            val out = px.copyOf()
            val whites = comps(white).filter { it.size >= 20 }.sortedByDescending { it.size }
            if (bigEyes) {
                // 큰 흰 눈(가장 큰 흰 덩어리 두 개) 안에서, 네 방향 중 셋 이상이 흰색으로 이어지는 검은 칸 = 눈동자
                val reach = (w * 0.15f).toInt().coerceAtLeast(6)
                fun hits(x0: Int, y0: Int, dx: Int, dy: Int): Boolean {
                    var x = x0; var y = y0
                    for (k in 0 until reach) {
                        x += dx; y += dy
                        if (x !in 0 until w || y !in 0 until h) return false
                        val i = y * w + x
                        if (dark[i]) continue
                        return white[i]
                    }
                    return false
                }
                for (m in whites.take(2)) {
                    var l = w; var r = 0; var t = h; var b = 0
                    for (i in m) { val x = i % w; val y = i / w; if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
                    for (y in t..b) for (x in l..r) {
                        val i = y * w + x
                        if (!dark[i]) continue
                        var n = 0
                        if (hits(x, y, 1, 0)) n++; if (hits(x, y, -1, 0)) n++; if (hits(x, y, 0, 1)) n++; if (hits(x, y, 0, -1)) n++
                        if (n >= 3) out[i] = Color.WHITE
                    }
                }
            } else if (whites.isNotEmpty()) {
                val m = whites[0]
                var l = w; var r = 0; var top = h
                for (i in m) { val x = i % w; val y = i / w; if (x < l) l = x; if (x > r) r = x; if (y < top) top = y }
                val mcx = (l + r) / 2f
                val maxA = (w * h * 0.006f).toInt().coerceAtLeast(6); val maxD = (w * 0.10f).toInt().coerceAtLeast(4)
                val cand = mutableListOf<Pair<Float, IntArray>>()
                for (c in comps(dark)) {
                    if (c.size < 3 || c.size > maxA) continue
                    var cl = w; var cr = 0; var ct = h; var cb = 0; var sx = 0f; var sy = 0f
                    for (i in c) { val x = i % w; val y = i / w; if (x < cl) cl = x; if (x > cr) cr = x; if (y < ct) ct = y; if (y > cb) cb = y; sx += x; sy += y }
                    val bw = cr - cl + 1; val bh = cb - ct + 1
                    if (bw > maxD || bh > maxD || maxOf(bw, bh) > 2.5f * minOf(bw, bh)) continue
                    // 둘레가 모두 털(어둡지도 희지도 않은 불투명)이어야 한다
                    var furAround = true
                    loop@ for (i in c) {
                        val x = i % w; val y = i / w
                        for ((dx, dy) in NB) {
                            val xx = x + dx; val yy = y + dy
                            if (xx !in 0 until w || yy !in 0 until h) { furAround = false; break@loop }
                            val j = yy * w + xx
                            if (dark[j]) continue
                            if (white[j] || !op(j)) { furAround = false; break@loop }
                        }
                    }
                    if (!furAround) continue
                    val cx = sx / c.size; val cy = sy / c.size
                    if (cy in (top - 0.30f * h)..(top + 2f) && cx in (l - 0.12f * w)..(r + 0.06f * w))
                        cand.add(((cx - mcx) * (cx - mcx) + (cy - top) * (cy - top)) to c)
                }
                for ((_, c) in cand.sortedBy { it.first }.take(2)) for (i in c) out[i] = Color.WHITE
            }
            Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        }
        private val NB = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        private val frozenRect = RectF()
        private val frozenFill = Paint().apply { color = Color.argb(110, 170, 225, 255); isAntiAlias = true }
        private val frozenEdge = Paint().apply {
            color = Color.argb(200, 90, 160, 220); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
        }
        private val frozenShine = Paint().apply {
            color = Color.argb(220, 255, 255, 255); strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        }

        /** 한 번의 공격이 한 번만 적중하도록 하는 피격 간격(초) */
        const val HIT_COOLDOWN = 0.35f

        // 함정을 뛰어넘는 도약
        const val HOP_VELOCITY = -560f
        const val HOP_GRAVITY = 1800f

        // draw 경로에서 매 프레임 재사용하는 페인트/사각형 (게임 스레드 단일 접근)
        private val shadowPaint = Paint().apply { color = Color.argb(50, 0, 0, 0); isAntiAlias = true }
        private val scratchRect = RectF()
        private val barBgPaint = Paint().apply { color = Color.argb(190, 50, 50, 50); isAntiAlias = true }
        private val barFgPaint = Paint().apply { isAntiAlias = true }
        private val barOutlinePaint = Paint().apply {
            color = Color.argb(200, 20, 20, 20); style = Paint.Style.STROKE
            strokeWidth = 1.5f; isAntiAlias = true
        }
    }
}

// ============ Normal Cat ============
class NormalCat(x: Float, y: Float, val coat: com.jamcat.nayngapo.engine.CatCoat = com.jamcat.nayngapo.engine.CatCoat.randomForEnemy())
    : Enemy(x, y, 15, 3) {   // 시트: 냥이 HP15, 냥냥펀치 3 — 무늬(노랑이·하양이·삼색이·턱시도·고등어)만 다르다
    override val width = 80f
    override val height = 95f
    private val moveSpeed = 60f
    private var wanderTimer = 0f
    private var wanderDirX = 0f
    private var hopPhase = (Math.random() * Math.PI).toFloat()   // 개체마다 박자를 다르게
    private var attackAnim = 0f      // 0보다 크면 냥냥펀치 모션 재생 중

    init { pickNewDirection() }

    private fun pickNewDirection() {
        wanderTimer = 1f + (Math.random() * 2f).toFloat()
        wanderDirX = (if (Math.random() < 0.5) -1f else 1f) * moveSpeed   // 횡뷰: 좌우 배회만
    }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        wanderTimer -= deltaTime

        val dx = playerX - x
        if (abs(dx) < 250f) {
            velocityX = sign(dx) * moveSpeed * 1.5f   // 추격
        } else {
            if (wanderTimer <= 0f) pickNewDirection()
            velocityX = wanderDirX
        }

        // 코앞까지 붙으면 냥냥펀치 모션
        if (attackAnim > 0f) attackAnim -= deltaTime
        else if (abs(dx) < 110f) attackAnim = 0.5f

        // 이동 중엔 총총 뛰는 귀여운 홉 연출.
        // 걷기 프레임 자체에 몸이 위아래로 출렁이는 움직임이 들어 있으므로
        // 코드 홉은 살짝만 얹는다 (크게 주면 두 번 튀어 보인다)
        if (abs(velocityX) > 1f) {
            hopPhase += deltaTime * 9f
            jumpZ = -abs(kotlin.math.sin(hopPhase)) * 5f
        } else {
            jumpZ = 0f
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        // 시트(cat5)의 공격 / 점프 / 이동 / 대기 애니메이션을 상태에 따라 재생.
        // 이동 판정을 점프보다 먼저 본다 — 걸을 때의 총총 홉(jumpZ)까지 점프로 잡으면
        // 걷는 내내 걷기↔점프 프레임이 번갈아 튀어 애니메이션이 덜덜거린다.
        val frames = when {
            attackAnim > 0f -> coat.attack
            abs(velocityX) > 1f -> coat.walk
            jumpZ < -4f -> coat.jump
            else -> coat.idle
        }
        val fps = if (attackAnim > 0f) 8f else 7f
        drawSprite(canvas, frame(frames, fps))
        drawHpBar(canvas)
    }
}

// ============ Hammer Cat ============
/**
 * 해머냥이 (기획7: 피 10, 공격력 3). 얼룩 냥이가 큰 망치를 들고 다가와 내려친다.
 * 몸은 삼색이 그림을 빌려 쓰고 망치는 코드로 그린다(들어올렸다가 내려치는 회전).
 */
class HammerCat(x: Float, y: Float) : Enemy(x, y, 10, 3) {
    override val width = 80f
    override val height = 95f
    private val moveSpeed = 50f            // 망치가 무거워 치즈냥이보다 느리다
    private var wanderTimer = 0f
    private var wanderDirX = 0f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var swing = 0f                 // 0보다 크면 내려치는 중 (SWING_TIME 에서 0으로)
    /** 기본은 흑백 얼룩이(절반). 나머지는 삼색이·고등어·턱시도·하양이 (대표님 2026-09-20) */
    val coat = if (Math.random() < 0.5) com.jamcat.nayngapo.engine.CatCoat.BICOLOR
                       else HAMMER_COATS[(Math.random() * HAMMER_COATS.size).toInt().coerceIn(0, HAMMER_COATS.size - 1)]

    init { pickNewDirection() }

    private fun pickNewDirection() {
        wanderTimer = 1f + (Math.random() * 2f).toFloat()
        wanderDirX = (if (Math.random() < 0.5) -1f else 1f) * moveSpeed
    }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        wanderTimer -= deltaTime
        val dx = playerX - x
        if (abs(dx) < 260f) velocityX = sign(dx) * moveSpeed * 1.4f
        else { if (wanderTimer <= 0f) pickNewDirection(); velocityX = wanderDirX }

        if (swing > 0f) swing -= deltaTime
        else if (abs(dx) < 120f) swing = SWING_TIME    // 코앞이면 망치를 치켜들었다 내려친다

        if (abs(velocityX) > 1f) { hopPhase += deltaTime * 8f; jumpZ = -abs(kotlin.math.sin(hopPhase)) * 4f }
        else jumpZ = 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        val frames = when {
            swing > 0f -> coat.attack
            abs(velocityX) > 1f -> coat.walk
            else -> coat.idle
        }
        val bmp = frame(frames, 7f)
        drawSprite(canvas, bmp)
        drawHammer(canvas, bmp)
        drawHpBar(canvas)
    }

    /** 망치: 앞발 자리에서 나가는 손잡이 + 네모 머리. 내려칠 땐 뒤로 치켜들었다가 앞으로 쾅. */
    private fun drawHammer(canvas: Canvas, bmp: Bitmap) {
        val cx = x + width / 2f
        val baseY = y + jumpZ + windLift - bmp.height * 0.45f     // 앞발 높이
        val dir = if (facingRight) 1f else -1f
        val handX = cx + dir * bmp.width * 0.30f
        // 진행률 0→1: 뒤로 들었다가(−70°) 앞으로 내려친다(+35°)
        val angle = if (swing > 0f) {
            val t = 1f - swing / SWING_TIME
            if (t < 0.45f) -70f * (t / 0.45f) else -70f + 105f * ((t - 0.45f) / 0.55f)
        } else -25f
        val len = bmp.height * 0.55f
        canvas.save()
        canvas.translate(handX, baseY)
        canvas.scale(dir, 1f)
        canvas.rotate(angle)
        // 손잡이 (위로 뻗음)
        canvas.drawRoundRect(-3f, -len, 3f, 0f, 3f, 3f, handlePaint)
        canvas.drawRoundRect(-3f, -len, 3f, 0f, 3f, 3f, hammerEdgePaint)
        // 망치 머리
        val hw = bmp.width * 0.30f; val hh = bmp.height * 0.18f
        headRect.set(-hw * 0.35f, -len - hh * 0.5f, hw * 0.65f, -len + hh * 0.5f)
        canvas.drawRoundRect(headRect, 3f, 3f, headPaint)
        canvas.drawRoundRect(headRect, 3f, 3f, hammerEdgePaint)
        canvas.restore()
    }

    companion object {
        private const val SWING_TIME = 0.6f
        private val HAMMER_COATS = listOf(
            com.jamcat.nayngapo.engine.CatCoat.CALICO, com.jamcat.nayngapo.engine.CatCoat.MACKEREL,
            com.jamcat.nayngapo.engine.CatCoat.TUXEDO, com.jamcat.nayngapo.engine.CatCoat.WHITE,
        )
        private val headRect = RectF()
        private val handlePaint = Paint().apply { color = Color.rgb(150, 100, 60); isAntiAlias = true }
        private val headPaint = Paint().apply { color = Color.rgb(120, 124, 134); isAntiAlias = true }
        private val hammerEdgePaint = Paint().apply {
            color = Color.rgb(40, 40, 48); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
    }
}

// ============ Gun Cat ============
/**
 * 총냥이 (기획8 `Plan/기획8_총냥이.jpeg`: 공격 2, 피 15). 일반(노란) 냥이 등에 총이 달려 있고 보라색 총알을 쏜다.
 * 몸은 치즈냥이 그림, 등의 총은 코드로 그린다. 탄은 사거리 안에서 2초마다 수평으로.
 */
class GunCat(x: Float, y: Float) : Enemy(x, y, 15, 2) {
    override val width = 80f
    override val height = 95f
    private val moveSpeed = 55f
    private var wanderTimer = 0f
    private var wanderDirX = 0f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var shootTimer = 1.5f
    private var recoil = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()
    private val coat = com.jamcat.nayngapo.engine.CatCoat.CHEESE

    init { pickNewDirection() }

    private fun pickNewDirection() {
        wanderTimer = 1f + (Math.random() * 2f).toFloat()
        wanderDirX = (if (Math.random() < 0.5) -1f else 1f) * moveSpeed
    }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        wanderTimer -= deltaTime
        val dx = playerX - x
        val dist = abs(dx)
        // 사거리 안이면 거리를 두고 서서 쏜다, 너무 가까우면 물러난다
        velocityX = when {
            dist < 180f -> -sign(dx) * moveSpeed
            dist < FIRE_RANGE -> 0f
            else -> { if (wanderTimer <= 0f) pickNewDirection(); wanderDirX }
        }
        shootTimer -= deltaTime
        if (recoil > 0f) recoil -= deltaTime
        if (shootTimer <= 0f && dist < FIRE_RANGE) {
            shootTimer = 2f; recoil = 0.25f
            val dir = if (facingRight) 1f else -1f
            pendingProjectiles.add(
                Projectile(x + width / 2f + dir * 34f, y - height * 0.92f, dir * 620f, 0f, attackPower, false,
                    tint = Color.rgb(170, 70, 220), radius = 5f)   // 보라색 총알, 등 위 총열 끝에서
            )
        }
        if (abs(velocityX) > 1f) { hopPhase += deltaTime * 9f; jumpZ = -abs(kotlin.math.sin(hopPhase)) * 5f }
        else jumpZ = 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        val frames = if (abs(velocityX) > 1f) coat.walk else coat.idle
        val bmp = frame(frames, 7f)
        drawSprite(canvas, bmp)
        drawGun(canvas, bmp)
        drawHpBar(canvas)
    }

    /** 등에 달린 총(대표님 2026-09-20 「일반 냥이의 등에 총이 달린 모습」): 등 위 받침 + 앞으로 뻗은 총열. 쏠 때 반동으로 살짝 들린다. */
    private fun drawGun(canvas: Canvas, bmp: Bitmap) {
        val cx = x + width / 2f
        val dir = if (facingRight) 1f else -1f
        val backX = cx - dir * bmp.width * 0.14f                   // 등(머리 반대쪽) 위
        // 등 윤곽에 딱 붙인다 — 받침이 몸 안으로 조금 들어가게 (대표님 2026-09-20 「몸과 총 사이가 붙어 있어야」)
        val backY = y + jumpZ + windLift - bmp.height * 0.78f
        val kick = if (recoil > 0f) -12f * (recoil / 0.25f) else 0f
        canvas.save()
        canvas.translate(backX, backY)
        canvas.scale(dir, 1f)
        // 받침(등에 고정)
        gunRect.set(-bmp.width * 0.09f, -2f, bmp.width * 0.09f, bmp.height * 0.11f)   // 받침: 몸 안까지 내려 틈이 없게
        canvas.drawRoundRect(gunRect, 2f, 2f, gunPaint); canvas.drawRoundRect(gunRect, 2f, 2f, gunEdgePaint)
        canvas.rotate(-GUN_ANGLE + kick)   // 총열은 머리를 안 가리게 위로 든다 (구운 아군 그림과 같은 각도)
        val bh = bmp.height * 0.10f
        gunRect.set(-bmp.width * 0.12f, -bh, bmp.width * 0.10f, 0f)            // 총 몸통
        canvas.drawRoundRect(gunRect, 3f, 3f, gunPaint); canvas.drawRoundRect(gunRect, 3f, 3f, gunEdgePaint)
        val bl = bmp.width * 0.42f
        gunRect.set(bmp.width * 0.08f, -bh * 0.8f, bmp.width * 0.08f + bl, -bh * 0.2f)   // 총열(앞으로)
        canvas.drawRoundRect(gunRect, 2f, 2f, gunPaint); canvas.drawRoundRect(gunRect, 2f, 2f, gunEdgePaint)
        if (recoil > 0.15f) canvas.drawCircle(bmp.width * 0.08f + bl + 6f, -bh * 0.5f, 6f, flashPaint)   // 총구 불꽃
        canvas.restore()
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val FIRE_RANGE = 520f
        /** 총열을 위로 드는 각도 — scripts/make_gun_cat.py 의 ANGLE 과 같게 */
        private const val GUN_ANGLE = 25f
        private val gunRect = RectF()
        private val gunPaint = Paint().apply { color = Color.rgb(70, 72, 80); isAntiAlias = true }
        private val gunEdgePaint = Paint().apply {
            color = Color.rgb(30, 30, 36); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2f
        }
        private val flashPaint = Paint().apply { color = Color.rgb(230, 120, 255); isAntiAlias = true }
    }
}

// ============ Thief Cat ============
/**
 * 도둑냥이 (대표님 2026-09-20): 도둑처럼 생겼고(눈가리개·보따리), 레이저는 쏘지 않는다.
 * 인간에게 닿으면 루비 2개를 훔쳐 달아나고, 잠시 뒤 다시 노린다. 잡으면 훔친 루비를 되찾는다.
 * 스테이지마다 한 마리뿐이다.
 */
class ThiefCat(x: Float, y: Float) : Enemy(x, y, 50, 15) {
    override val width = 85f
    override val height = 100f
    private val moveSpeed = 180f
    private val detectRange = 300f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    /** 훔쳐서 들고 있는 루비 — 쓰러지면 돌려준다 */
    var stolen = 0
        private set
    /** 훔친 직후 달아나는 시간 */
    private var fleeTimer = 0f
    /** 훔칠 수 있을 때까지 남은 시간 (연속으로 털어가지 않게) */
    private var stealCooldown = 0f

    /** 도둑은 때리지 않고 훔친다 */
    override val contactDamage = 0

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        val dx = playerX - x
        val dist = abs(dx)
        if (fleeTimer > 0f) fleeTimer -= deltaTime
        if (stealCooldown > 0f) stealCooldown -= deltaTime

        if (fleeTimer > 0f) {
            velocityX = -sign(dx) * moveSpeed * 1.3f         // 훔친 뒤엔 반대쪽으로 줄행랑
            facingRight = velocityX > 0f
        } else {
            facingRight = playerX > x
            velocityX = when {
                dist < detectRange && dist > 30f -> sign(dx) * moveSpeed
                dist <= 30f -> 0f
                else -> if (facingRight) moveSpeed * 0.3f else -moveSpeed * 0.3f
            }
        }

        // 빠르게 총총 뛰는 홉 연출
        if (abs(velocityX) > 1f) {
            hopPhase += deltaTime * 12f
            jumpZ = -abs(kotlin.math.sin(hopPhase)) * 14f
        } else {
            jumpZ = 0f
        }
        super.update(deltaTime, playerX, playerY)
    }

    /** 인간에게 닿았다 — 지금 훔칠 수 있으면 true 를 돌려주고 달아나기 시작한다 */
    fun tryStealOnTouch(): Boolean {
        if (stealCooldown > 0f || fleeTimer > 0f) return false
        stealCooldown = 4f
        fleeTimer = 2.5f
        return true
    }

    fun addStolen(n: Int) { stolen += n }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.thiefCat)
        drawHpBar(canvas)
    }

    companion object {
        /** 한 번에 훔쳐 가는 루비 */
        const val STEAL_AMOUNT = 2
    }
}

// ============ Laser Cat ============
class LaserCat(x: Float, y: Float) : Enemy(x, y, 15, 30) {   // 시트: 레이저냥이 HP15, 레이저 30
    override val width = 110f
    override val height = 100f
    private var shootTimer = 3f
    private var isCharging = false
    private var chargeTimer = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        velocityX = 0f
        facingRight = playerX > x

        // 화면에 보일 만한 거리에서만 발사 준비 (보이지 않는 곳에서 날아오는 레이저 방지)
        val playerDist = abs(playerX - x)
        shootTimer -= deltaTime
        if (shootTimer <= 0f && !isCharging && playerDist < Constants.ENEMY_FIRE_RANGE) {
            isCharging = true
            chargeTimer = 1f
        }
        if (isCharging) {
            chargeTimer -= deltaTime
            if (chargeTimer <= 0f) {
                isCharging = false
                shootTimer = 3.5f
                val dir = if (facingRight) 1f else -1f
                pendingProjectiles.add(
                    Projectile(x + width / 2f, y - height * 0.5f, 800f * dir, 0f, 30, false,
                        tint = Color.rgb(92, 208, 108), radius = 9f)
                )
            }
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        // 시트(cat4)의 레이저 냥이: 충전/발사 중에는 공격 프레임
        val frames = if (isCharging) SpriteManager.laserAttack else SpriteManager.laserIdle
        drawSprite(canvas, frame(frames, 6f))
        drawHpBar(canvas)
        if (isCharging) {
            val cx = x + width / 2f
            val barrelDir = if (facingRight) 1f else -1f
            val chargePaint = Paint().apply {
                color = Color.RED
                alpha = (150 + (sin(chargeTimer.toDouble() * 20) * 100).toInt()).coerceIn(0, 255)
                isAntiAlias = true
            }
            canvas.drawCircle(cx + 68f * barrelDir, y - height / 2f, 12f, chargePaint)
        }
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ 나쁜 파랑냥이 (HP 20, 대미지 5) ============
/** 수북한 꼬리에 털이 부슬한 나쁜 편 파랑냥이: 빠르게 달려들어 문다 */
class BlueCat(x: Float, y: Float) : Enemy(x, y, 20, 5) {
    override val width = 92f
    override val height = 104f
    private var hopPhase = (Math.random() * Math.PI).toFloat()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        val dx = playerX - x
        velocityX = if (abs(dx) < 500f) sign(dx) * 200f else sign(dx) * 90f

        hopPhase += deltaTime * 13f
        jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 16f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.blueCatEvil)
        drawHpBar(canvas)
    }
}

// ============ 빨강·주황·노랑냥이 (파랑냥이 색만 바꾼 냥이 — 대표님 2026-09-20). 각자 공격이 다르다 ============
/**
 * 빨간냥이 — 불을 뱉는다 (대표님 2026-09-27 「빨간 냥이는 불을 뱉어」).
 * 다가오다 인간이 불 사거리 안에 들면 멈춰 서서, 입에서 불꽃 줄기를 0.8초 동안 뿜는다(3초마다).
 * 불꽃 하나하나가 적 탄이라 인간은 스치면 하트 1(무적 시간이 있어 한 번 뿜는 동안 한 번만), 아군 냥이는 불꽃마다 1.
 * 사거리 약 300·뿜는 시간·간격·불꽃 수는 Claude 판단. HP 20.
 */
class RedCat(x: Float, y: Float) : Enemy(x, y, 20, 5) {
    override val width = 92f
    override val height = 104f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var breathCooldown = 1.5f
    /** 뿜는 중이면 남은 시간 */
    private var breathing = 0f
    private var puffTimer = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        val dx = playerX - x
        if (breathing <= 0f) facingRight = playerX > x      // 뿜는 동안은 방향 고정
        val dist = abs(dx)
        breathCooldown -= deltaTime
        if (breathing <= 0f && breathCooldown <= 0f && dist < BREATH_RANGE) {
            breathing = BREATH_TIME; breathCooldown = BREATH_EVERY; puffTimer = 0f
        }
        if (breathing > 0f) {
            breathing -= deltaTime
            velocityX = 0f
            jumpZ = 0f
            puffTimer -= deltaTime
            if (puffTimer <= 0f) {
                puffTimer = PUFF_EVERY
                val dir = if (facingRight) 1f else -1f
                val mouthX = x + width / 2f + dir * width * 0.45f
                val mouthY = y - height * 0.45f
                val spread = (Math.random().toFloat() - 0.5f) * 120f
                val r = 13f
                pendingProjectiles.add(
                    Projectile(mouthX - r, mouthY - r, dir * FLAME_SPEED, spread, 1, false,
                        tint = Color.rgb(255, 140, 30), radius = r, fuse = FLAME_LIFE, isFlame = true)
                )
            }
        } else {
            velocityX = when {
                dist < BREATH_RANGE * 0.6f -> 0f                  // 충분히 가까우면 멈춰 불을 기다린다
                dist < 500f -> sign(dx) * 200f
                else -> sign(dx) * 90f
            }
            if (abs(velocityX) > 1f) { hopPhase += deltaTime * 13f; jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 16f }
            else jumpZ = 0f
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.redCatEvil)
        if (breathing > 0f) {
            // 입가에 불씨가 이글거린다
            val dir = if (facingRight) 1f else -1f
            val mx = x + width / 2f + dir * width * 0.45f
            val my = y + jumpZ - height * 0.45f
            emberPaint.alpha = (180 + 70 * sin(breathing * 50.0)).toInt().coerceIn(0, 255)
            canvas.drawCircle(mx, my, 14f, emberPaint)
        }
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val BREATH_RANGE = 300f
        private const val BREATH_TIME = 0.8f
        private const val BREATH_EVERY = 3f
        private const val PUFF_EVERY = 0.08f
        private const val FLAME_SPEED = 480f
        /** 불꽃이 살아 있는 시간 — 480 × 0.55 ≈ 260px 날아가 식는다 */
        private const val FLAME_LIFE = 0.55f
        private val emberPaint = Paint().apply { color = Color.rgb(255, 170, 40); isAntiAlias = true }
    }
}
/**
 * 주황냥이 — 용암으로 공격한다 (대표님 2026-09-27 「주황 냥이는 용암으로 공격해」).
 * 거리를 두고 2.2초마다 용암 덩이를 인간 쪽으로 포물선으로 던진다. 덩이에 맞으면 하트 1(적 탄 규칙),
 * 땅에 떨어지면 2.5초 동안 끓는 용암 웅덩이가 남아 밟으면 하트 1 (웅덩이는 GamePlayScreen 이 관리).
 * 사거리 480·간격·비행 시간·웅덩이 시간은 Claude 판단. HP 20.
 */
class OrangeCat(x: Float, y: Float) : Enemy(x, y, 20, 5) {
    override val width = 92f
    override val height = 104f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var throwTimer = 1.2f
    private var windup = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        val dx = playerX - x
        val dist = abs(dx)
        velocityX = when {
            dist < 220f -> -sign(dx) * 150f          // 너무 가까우면 물러난다
            dist < THROW_RANGE -> 0f                  // 사거리 안: 서서 던진다
            else -> sign(dx) * 160f
        }
        throwTimer -= deltaTime
        if (windup > 0f) windup -= deltaTime
        if (throwTimer <= 0f && dist < THROW_RANGE) {
            throwTimer = THROW_EVERY; windup = 0.25f
            // 인간 발밑에 떨어지게 포물선 속도를 거꾸로 계산한다
            val r = 16f
            val sx = x + width / 2f + (if (facingRight) 1f else -1f) * width * 0.4f
            val sy = y - height * 0.7f
            val tx = playerX + Constants.PLAYER_WIDTH / 2f
            val ty = playerY - 20f
            val t = FLIGHT_TIME
            val vx = (tx - sx) / t
            val vy = (ty - sy) / t - GRAVITY * t / 2f
            pendingProjectiles.add(
                Projectile(sx - r, sy - r, vx, vy, 1, false, tint = Color.rgb(255, 110, 20), radius = r,
                    gravity = GRAVITY, isLava = true)
            )
        }
        if (abs(velocityX) > 1f) { hopPhase += deltaTime * 13f; jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 16f }
        else jumpZ = 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.orangeCatEvil)
        if (windup > 0f) {
            // 던지는 순간 입가에 용암이 부글
            val dir = if (facingRight) 1f else -1f
            glow.alpha = (220 * windup / 0.25f).toInt().coerceIn(0, 255)
            canvas.drawCircle(x + width / 2f + dir * width * 0.4f, y + jumpZ - height * 0.7f, 18f, glow)
        }
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val THROW_RANGE = 480f
        private const val THROW_EVERY = 2.2f
        private const val FLIGHT_TIME = 0.75f
        private const val GRAVITY = 1500f
        private val glow = Paint().apply { color = Color.rgb(255, 140, 30); isAntiAlias = true }
    }
}
/**
 * 노랑냥이 — 번개를 부른다 (대표님 2026-09-24). 번개는 **먹구름에서 떨어진다** (대표님 2026-09-25).
 * 2026-09-27 그림(「이게 노랑냥이 공격이야」): 부르면 **하늘 위쪽 전체가 먹구름으로 덮이고** 거기서 번개가 인간에게 내리꽂힌다.
 * 먹구름 띠는 화면이 그린다(GamePlayScreen.drawStormSky — storm 세기·stormTargets 를 읽는다).
 * 거리를 두고 2.5초마다 인간 자리를 노리고, 구름이 모이면(그 자리 구름 속이 번쩍인다) 번개가 곧장 내리꽂힌다.
 * 노랑냥이가 쓰러지면 떠 있던 먹구름도 사라진다. HP 20 · 공격(번개 대미지) 2
 */
class YellowCat(x: Float, y: Float) : Enemy(x, y, 20, 2) {
    override val width = 92f
    override val height = 104f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var shootTimer = 0.8f
    private var flash = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    /** 먹구름 하나: 자리 + 생긴 뒤 흐른 시간. 모이는 동안 비켜 설 틈이 있다 */
    private class StormCloud(val x: Float, val y: Float) {
        var t = 0f
        var struck = false
    }

    private val clouds = mutableListOf<StormCloud>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        val dx = playerX - x
        val dist = abs(dx)
        velocityX = when {
            dist < 200f -> -sign(dx) * 150f          // 너무 가까우면 물러난다
            dist < FIRE_RANGE -> 0f                   // 사거리 안: 서서 부른다
            else -> sign(dx) * 160f
        }
        shootTimer -= deltaTime
        if (flash > 0f) flash -= deltaTime
        if (shootTimer <= 0f && dist < FIRE_RANGE) {
            shootTimer = SHOOT_EVERY; flash = 0.2f
            // 부르는 순간 하늘이 먹구름으로 덮이고, 인간이 선 자리를 노린다
            clouds.add(StormCloud(playerX + Constants.PLAYER_WIDTH / 2f, skyBottomY))
        }

        val it = clouds.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.t += deltaTime
            if (!c.struck && c.t >= GATHER_TIME) {
                // 구름이 다 모였다 — 구름 밑에서 번개가 곧장 아래로 내리꽂힌다
                c.struck = true
                val r = 8f
                pendingProjectiles.add(
                    Projectile(c.x - r, skyBottomY + 20f, 0f, BOLT_SPEED, attackPower, false,
                        tint = Color.rgb(255, 214, 40), radius = r, isLightning = true, boltTopY = skyBottomY - 20f)
                )
            }
            if (c.t >= GATHER_TIME + LINGER_TIME) it.remove()
        }

        if (abs(velocityX) > 1f) { hopPhase += deltaTime * 13f; jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 16f }
        else jumpZ = 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.yellowCatEvil)
        if (flash > 0f) {                              // 부르는 순간 몸 주위 번쩍
            val cx = x + width / 2f; val cy = y + jumpZ - height * 0.5f
            sparkPaint.alpha = (200 * flash / 0.2f).toInt().coerceIn(0, 255)
            for (k in 0 until 6) {
                val a = k * 60.0 + flash * 400.0
                canvas.drawLine(cx, cy, cx + (60 * Math.cos(Math.toRadians(a))).toFloat(), cy + (60 * Math.sin(Math.toRadians(a))).toFloat(), sparkPaint)
            }
        }
        drawHpBar(canvas)
    }

    /** 하늘 먹구름 세기 0~1 — 부르면 빠르게 짙어지고 번개를 친 뒤 흩어진다. 쓰러지면 0 */
    val storm: Float
        get() {
            if (isDead) return 0f
            var m = 0f
            for (c in clouds) {
                val v = if (c.struck) 1f - (c.t - GATHER_TIME) / LINGER_TIME else (c.t / 0.2f)
                m = maxOf(m, v.coerceIn(0f, 1f))
            }
            return m
        }

    /** 번개가 떨어질 자리들: (월드 x, 모인 정도 0~1 — 1 이면 이미 쳤음) */
    val stormTargets: List<Pair<Float, Float>>
        get() = if (isDead) emptyList() else clouds.map { it.x to (it.t / GATHER_TIME).coerceAtMost(1f) }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val FIRE_RANGE = 560f
        /** 번개를 부르는 간격 — 「공격이 너무 느려」(대표님 2026-09-27)로 2.5초 → 1.5초 */
        private const val SHOOT_EVERY = 1.5f
        /** 먹구름이 모여 번개가 떨어지기까지 — 이 사이에 비켜 서면 피한다 (Claude 판단) */
        private const val GATHER_TIME = 0.5f
        /** 번개를 친 뒤 구름이 흩어지는 시간 */
        private const val LINGER_TIME = 0.4f
        /** 하늘 먹구름 띠의 아랫자락 (월드 y) — 화면이 카메라에 맞춰 매 프레임 알려 준다 */
        var skyBottomY = 240f
        private val sparkPaint = Paint().apply { color = Color.rgb(255, 240, 120); strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
        private const val BOLT_SPEED = 3400f   // 하늘에서 땅까지 약 0.25초
    }
}

// ============ 꿀 스테이지(21) — 기획10 `Plan/기획10_꿀스테이지_벌냥이_꽃냥이.jpg` (대표님 2026-09-27) ============
/**
 * 벌냥이 — HP 3. 노랑 몸에 둥근 검은 띠, 하늘색 날개 한 쌍, 엉덩이 침, 꼬리 없음. 공중에 떠 다닌다(벌집 구조물은 2026-10-04 삭제):
 * 인간이 다가오면 머리 위로 날아와 떠 있다가, 뒤로 돌아 **엉덩이 침으로 내려와 찌른다**(닿으면 하트 1).
 * 떠 있는 동안은 높이 있어 닿지 않고, 찌르러 내려왔을 때 때릴 수 있다. 공격력(아군 피해)은 기획서 판독 중 — 임시 10.
 * 쫓아오는 거리·높이·찌르기 간격은 Claude 판단.
 */
class BeeCat(x: Float, y: Float) : Enemy(x, y, 3, 10) {
    override val width = 84f
    override val height = 80f
    private enum class State { FLY, DIVE }
    private var state = State.FLY
    private var stingT = 0f
    private var cooldown = 0.8f
    private var bob = (Math.random() * 6.0).toFloat()
    private val box = RectF()

    init { jumpZ = HOVER_Z }

    /** 떠 있는 동안은 높이 있어 닿지 않는다 — 찌르러 내려왔을 때만 때리고 맞는다 */
    override val isTangible: Boolean get() = state == State.DIVE || jumpZ > HOVER_Z * 0.6f
    override val hitBox: RectF get() = box.apply { set(x, y + jumpZ - height, x + width, y + jumpZ + 6f) }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        animTime += deltaTime
        bob += deltaTime * 4f
        y = com.jamcat.nayngapo.engine.Terrain.groundY(x + width / 2f)
        val pcx = playerX + Constants.PLAYER_WIDTH / 2f
        val cx = x + width / 2f
        when (state) {
            State.FLY -> {
                // 공중에 떠서 파닥이다가, 인간이 가까이 오면 머리 위로 날아간다
                jumpZ += (HOVER_Z + sin(bob) * 14f - jumpZ) * (deltaTime * 3f).coerceAtMost(1f)
                val dx = pcx - cx
                facingRight = dx > 0f
                val chase = abs(dx) < CHASE_RANGE
                x += sign(dx) * (if (chase && abs(dx) > 60f) FLY_SPEED else 0f) * deltaTime
                cooldown -= deltaTime
                if (cooldown <= 0f && abs(dx) < 110f && jumpZ < HOVER_Z * 0.7f) { state = State.DIVE; stingT = 0f }
            }
            State.DIVE -> {
                // 뒤로 돌아 엉덩이 침을 인간에게 — 내려왔다가 다시 떠오른다
                stingT += deltaTime
                facingRight = pcx < cx            // 엉덩이(침)가 인간 쪽
                jumpZ = when {
                    stingT < 0.35f -> HOVER_Z + (STING_Z - HOVER_Z) * (stingT / 0.35f)
                    stingT < 0.6f -> STING_Z
                    else -> STING_Z + (HOVER_Z - STING_Z) * ((stingT - 0.6f) / 0.4f).coerceAtMost(1f)
                }
                if (stingT >= 1.0f) { state = State.FLY; cooldown = STING_EVERY }
            }
        }
        x = x.coerceIn(Constants.PLAY_AREA_LEFT + 5f, Constants.PLAY_AREA_RIGHT - width - 5f)
    }

    override fun takeDamage(amount: Int) {
        if (isTangible) super.takeDamage(amount)
    }

    override fun draw(canvas: Canvas) {
        // 날개가 파닥이게: 그림을 살짝 위아래로 떨게 한다 (날개는 그림 속 기획서 모양 그대로)
        val flapLift = sin(animTime * 40f) * 2f
        jumpZ += flapLift
        drawSprite(canvas, SpriteManager.beeCat)
        jumpZ -= flapLift
        drawHpBar(canvas)
    }

    companion object {
        /** 인간이 이 안에 오면 쫓아온다 (Claude 판단) */
        private const val CHASE_RANGE = 800f
        private const val HOVER_Z = -210f
        private const val STING_Z = -70f
        private const val FLY_SPEED = 230f
        private const val STING_EVERY = 1.6f
    }
}

/**
 * 떠난 냥이 (대표님 2026-10-17 「떠난 고양이는 특정한 스테이지에 이름표가 있는 채로 남아 있어, 채집통으로 데려올 수 있어」).
 * 배고파서 떠난 내 팀 냥이가 그 스테이지를 이름표를 단 채 어슬렁거린다. 싸우지 않고(닿아도 안 다침, 때려도 안 다침),
 * 채집통을 가지고 인간이 때리면 다시 내 팀으로 돌아온다. 스테이지를 깨는 데는 세지 않는다.
 */
class StrayTeamCat(val teamCat: com.jamcat.nayngapo.engine.CatTeam.TeamCat, x: Float) : Enemy(x, Constants.GROUND_Y, 1, 0) {
    override val width = 80f
    override val height = 92f
    override val contactDamage = 0
    private val home = x
    private var dir = if (Math.random() < 0.5) 1f else -1f
    private var wait = 0f
    private var hop = 0f
    private val sprite: Bitmap = AllyCat(0f, 0f, teamCat.kind, level = teamCat.level, teamId = teamCat.id,
        coat = teamCat.coat, kitten = teamCat.isKitten).spriteNow()

    /** 싸우지 않는다 — 인간·냥이와 부딪히지 않고 우리 냥이도 노리지 않는다 */
    override val isTangible: Boolean get() = false

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        animTime += deltaTime
        if (wait > 0f) { wait -= deltaTime; velocityX = 0f }
        else {
            velocityX = dir * 70f
            if (abs(x - home) > 220f) { dir = -sign(x - home); wait = 1f + (Math.random() * 2f).toFloat() }
            else if (Math.random() < deltaTime * 0.3) wait = 1f + (Math.random() * 1.5f).toFloat()
        }
        hop = if (abs(velocityX) > 1f) -abs(sin(animTime * 8f)) * 6f else 0f
        jumpZ = hop
        x += velocityX * deltaTime
        y = Terrain.groundY(x + width / 2f)
        if (velocityX != 0f) facingRight = velocityX > 0f
    }

    override fun takeDamage(amount: Int) { /* 떠난 냥이는 다치지 않는다 — 채집통으로만 데려온다 */ }

    override fun draw(canvas: Canvas) {
        val scale = if (teamCat.isKitten) AllyCat.KITTEN_SCALE else 1f
        val bmp = if (facingRight) sprite else SpriteManager.getFlipped(sprite)
        val bw = bmp.width * scale; val bh = bmp.height * scale
        val cx = x + width / 2f
        val by = y + jumpZ + 8f
        tagRect.set(cx - bw / 2f, by - bh, cx + bw / 2f, by)
        canvas.drawBitmap(bmp, null, tagRect, tagSpritePaint)
        // 나무 막대에 흰 보따리를 매고 있다 (대표님 2026-10-17): 등 위에 얹은 막대가 뒤로 비스듬히, 뒤 끝에 흰 보따리 (얼굴은 안 가린다)
        val dir = if (facingRight) 1f else -1f
        val sx = cx - dir * bw * 0.04f; val sy = by - bh * 0.74f
        val ex = cx - dir * bw * 0.60f; val ey = by - bh * 1.02f + sin(animTime * 8f) * 2f
        canvas.drawLine(sx, sy, ex, ey, stickPaint)
        val r = bw * 0.20f
        tagRect.set(ex - r, ey - r * 0.1f, ex + r, ey + r * 1.7f)
        canvas.drawOval(tagRect, bundlePaint); canvas.drawOval(tagRect, bundleInk)
        // 묶은 매듭
        canvas.drawCircle(ex, ey, r * 0.28f, bundlePaint); canvas.drawCircle(ex, ey, r * 0.28f, bundleInk)
        // 이름표: '이름 (떠난 냥이)'
        val label = "${teamCat.name} · 떠난 냥이"
        tagText.textSize = 26f
        val tw = tagText.measureTr(label) + 24f
        tagRect.set(cx - tw / 2f, by - bh - 44f, cx + tw / 2f, by - bh - 10f)
        canvas.drawRoundRect(tagRect, 14f, 14f, tagBg)
        canvas.drawTr(label, cx, tagRect.bottom - 9f, tagText)
    }

    companion object {
        private val tagRect = RectF()
        private val tagSpritePaint = Paint().apply { isFilterBitmap = true; alpha = 230 }
        private val stickPaint = Paint().apply { color = Color.rgb(140, 92, 48); strokeWidth = 7f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
        private val bundlePaint = Paint().apply { color = Color.rgb(250, 250, 246); isAntiAlias = true }
        private val bundleInk = Paint().apply { color = Color.rgb(40, 34, 30); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
        private val tagBg = Paint().apply { color = Color.argb(190, 80, 60, 50); isAntiAlias = true }
        private val tagText = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    }
}

/**
 * 여왕 벌냥이 — 30스테이지 보스 (대표님 2026-10-10 「30스테이지 보스는 여왕 벌냥이야」). GPT 그림 `queen_bee_cat`(금관을 쓴 큰 벌냥이).
 * 높이 떠서 인간 위를 따라다니다가 2.8초마다 엉덩이 침으로 내려와 찌르고(내려왔을 때만 맞는다), 7초마다 벌냥이 둘을 불러낸다(한 번에 최대 6마리까지).
 * **HP 25 · 공격 3** (대표님 2026-10-16) · 닿으면 하트 3(보스) · 간격·높이·부르는 수는 Claude 판단.
 */
class QueenBeeCatBoss(x: Float, y: Float) : Enemy(x, y, 25, 3) {
    override val width = 170f
    override val height = 150f
    override val contactDamage = 3
    private enum class State { FLY, DIVE }
    private var state = State.FLY
    private var stingT = 0f
    private var cooldown = 2.0f
    private var summonT = 4f
    private var bob = 0f
    private val box = RectF()
    private val spawns = mutableListOf<Enemy>()
    private val summoned = mutableListOf<Enemy>()

    init { jumpZ = HOVER_Z }

    override val isTangible: Boolean get() = state == State.DIVE || jumpZ > HOVER_Z * 0.6f
    override val hitBox: RectF get() = box.apply { set(x, y + jumpZ - height, x + width, y + jumpZ + 6f) }

    override fun takeSpawns(): List<Enemy> {
        if (spawns.isEmpty()) return emptyList()
        val out = spawns.toList(); spawns.clear(); return out
    }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        animTime += deltaTime
        bob += deltaTime * 3f
        y = Terrain.groundY(x + width / 2f)
        val pcx = playerX + Constants.PLAYER_WIDTH / 2f
        val cx = x + width / 2f
        // 벌냥이 부르기 (살아 있는 부하가 MAX_MINIONS 보다 적을 때)
        summonT -= deltaTime
        summoned.removeAll { it.isDead }
        if (summonT <= 0f) {
            summonT = SUMMON_EVERY
            repeat(2) { k ->
                if (summoned.size < MAX_MINIONS) {
                    val b = BeeCat(cx + (if (k == 0) -120f else 120f), y)
                    summoned.add(b); spawns.add(b)
                }
            }
        }
        when (state) {
            State.FLY -> {
                jumpZ += (HOVER_Z + sin(bob) * 18f - jumpZ) * (deltaTime * 3f).coerceAtMost(1f)
                val dx = pcx - cx
                facingRight = dx > 0f
                if (abs(dx) < 900f && abs(dx) > 80f) x += sign(dx) * FLY_SPEED * deltaTime
                cooldown -= deltaTime
                if (cooldown <= 0f && abs(dx) < 160f && jumpZ < HOVER_Z * 0.7f) { state = State.DIVE; stingT = 0f }
            }
            State.DIVE -> {
                stingT += deltaTime
                facingRight = pcx < cx            // 엉덩이(침)가 인간 쪽
                jumpZ = when {
                    stingT < 0.4f -> HOVER_Z + (STING_Z - HOVER_Z) * (stingT / 0.4f)
                    stingT < 0.8f -> STING_Z
                    else -> STING_Z + (HOVER_Z - STING_Z) * ((stingT - 0.8f) / 0.5f).coerceAtMost(1f)
                }
                if (stingT >= 1.3f) { state = State.FLY; cooldown = STING_EVERY }
            }
        }
        x = x.coerceIn(Constants.PLAY_AREA_LEFT + 5f, Constants.PLAY_AREA_RIGHT - width - 5f)
    }

    override fun takeDamage(amount: Int) {
        if (isTangible) super.takeDamage(amount)
    }

    override fun draw(canvas: Canvas) {
        val flapLift = sin(animTime * 34f) * 3f
        jumpZ += flapLift
        drawSprite(canvas, SpriteManager.queenBeeCat ?: SpriteManager.beeCat)
        jumpZ -= flapLift
        drawHpBar(canvas)
    }

    companion object {
        private const val HOVER_Z = -240f
        private const val STING_Z = -80f
        private const val FLY_SPEED = 180f
        private const val STING_EVERY = 2.8f
        private const val SUMMON_EVERY = 7f
        private const val MAX_MINIONS = 6
    }
}

/**
 * 꽃냥이 — HP 15 · 공격 10. 주황 줄무늬 노란 몸, 얼굴에 초록 잎, 머리에서 분홍 꽃대 두 개.
 * 거리를 두고 서서 **꽃에서 꽃가루를 뿜고**, 꽃가루를 맞은 인간은 **숨이 막힌다**(하트 1 + 잠깐 느려짐).
 * 사거리 320·3초마다 0.9초 뿜기·느려짐 1.5초는 Claude 판단.
 */
class FlowerCat(x: Float, y: Float) : Enemy(x, y, 15, 10) {
    override val width = 92f
    override val height = 104f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var sprayCooldown = 1.5f
    private var spraying = 0f
    private var puff = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        val dx = playerX - x
        if (spraying <= 0f) facingRight = dx > 0f
        val dist = abs(dx)
        sprayCooldown -= deltaTime
        if (spraying <= 0f && sprayCooldown <= 0f && dist < SPRAY_RANGE) { spraying = SPRAY_TIME; sprayCooldown = SPRAY_EVERY; puff = 0f }
        if (spraying > 0f) {
            spraying -= deltaTime
            velocityX = 0f; jumpZ = 0f
            puff -= deltaTime
            if (puff <= 0f) {
                puff = 0.07f
                val dir = if (facingRight) 1f else -1f
                // 꽃대 끝(머리 위 앞쪽)에서 인간 쪽으로 흩날린다
                val fx = x + width / 2f + dir * width * 0.35f
                val fy = y - height * 1.15f
                val r = 7f
                pendingProjectiles.add(
                    Projectile(fx - r, fy - r, dir * (300f + Math.random().toFloat() * 120f), 60f + (Math.random().toFloat() - 0.5f) * 180f,
                        1, false, tint = Color.rgb(250, 220, 80), radius = r, fuse = 0.8f, isPollen = true)
                )
            }
        } else {
            velocityX = when {
                dist < SPRAY_RANGE * 0.7f -> 0f
                dist < 500f -> sign(dx) * 150f
                else -> sign(dx) * 80f
            }
            if (abs(velocityX) > 1f) { hopPhase += deltaTime * 12f; jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 12f } else jumpZ = 0f
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.flowerCat)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val SPRAY_RANGE = 320f
        private const val SPRAY_TIME = 0.9f
        private const val SPRAY_EVERY = 3f
    }
}

// ============ 31 해적 스테이지 (기획12·13, 2026-10-04 「해적 스테이지 만들어줘」 — 공격은 Claude 판단) ============
/**
 * 배냥이 — HP 20. 나무배를 타고 모래 위를 미끄러지듯 빠르게 다가와 **배로 들이받는다**(닿으면 하트 1).
 * 배가 출렁이며 다가오고, 한 번 들이받으면 뒤로 물러났다가 다시 온다. 속도·물러나는 시간은 Claude 판단.
 */
class BoatCat(x: Float, y: Float) : Enemy(x, y, 20, 5) {
    companion object {
        /** 배냥이 그림에서 배 바닥 높이 (그 아래는 다리 — 물에 잠긴다) */
        const val BOAT_HULL_BOTTOM = 0.88f
    }
    /** 배냥이 종류 = 무늬 (하양이·턱시도·삼색이·고등어·치즈, 대표님 2026-10-09) — 무작위 */
    val coat: com.jamcat.nayngapo.engine.CatCoat = listOf(
        com.jamcat.nayngapo.engine.CatCoat.CHEESE, com.jamcat.nayngapo.engine.CatCoat.WHITE, com.jamcat.nayngapo.engine.CatCoat.CALICO,
        com.jamcat.nayngapo.engine.CatCoat.TUXEDO, com.jamcat.nayngapo.engine.CatCoat.MACKEREL).random()
    override val width = 110f
    override val height = 90f
    private var rock = (Math.random() * 6.0).toFloat()
    private var backoff = 0f

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        rock += deltaTime * 5f
        val dx = playerX - x
        if (backoff > 0f) {
            backoff -= deltaTime
            velocityX = -sign(dx) * 160f
        } else {
            velocityX = if (abs(dx) < 700f) sign(dx) * 240f else sign(dx) * 90f
            if (abs(dx) < 50f) backoff = 0.8f              // 들이받고 물러난다
        }
        facingRight = dx > 0f
        jumpZ = -abs(sin(rock.toDouble())).toFloat() * 6f
        super.update(deltaTime, playerX, playerY)
        // 배냥이는 배라 해적선 갑판에는 못 오른다 — 뱃전 앞 물에서 멈춘다
        if (Terrain.onDeck(x + width / 2f)) {
            x = if (x + width / 2f < (Terrain.deckLeft + Terrain.deckRight) / 2f) Terrain.deckLeft - width / 2f - 1f
                else Terrain.deckRight - width / 2f + 1f
            y = Terrain.groundY(x + width / 2f)
            climbOffset = 0f
        }
        if (velocityX != 0f && backoff > 0f) facingRight = dx > 0f   // 물러날 때도 인간 쪽을 본다
    }

    override fun draw(canvas: Canvas) {
        // 바다라 배 밑의 발은 물에 잠겨 안 보인다 (대표님 2026-10-09): 배 바닥(그림 높이의 88%)이 물 위에 오게 그리고 그 아래는 자른다
        val base = coat.boatEnemy
        val bmp = if (facingRight) base else SpriteManager.getFlipped(base)
        val cx = x + width / 2f
        val water = y + jumpZ + 6f
        val top = water - bmp.height * BOAT_HULL_BOTTOM
        canvas.save()
        canvas.clipRect(cx - bmp.width / 2f - 2f, top - 4f, cx + bmp.width / 2f + 2f, water)
        canvas.drawBitmap(bmp, cx - bmp.width / 2f, top, null)
        canvas.restore()
        lastSpriteHeight = bmp.height * BOAT_HULL_BOTTOM
        lastSprite = base
        drawHpBar(canvas)
    }
}

/**
 * 해적냥이 — 31 보스, HP 40 (기획13). 해적선 앞을 지키며 다가와 **갈고리로 할퀴며 돌진**한다(보스라 닿으면 하트 3).
 * 2.5초마다 살짝 웅크렸다가 인간 쪽으로 빠르게 돌진. 간격·돌진 속도는 Claude 판단.
 */
class PirateCatBoss(x: Float, y: Float) : Enemy(x, y, 40, 8) {
    override val width = 120f
    override val height = 120f
    override val contactDamage = 3
    private var dashTimer = 2.0f
    private var windup = 0f
    private var dashing = 0f
    private var dir = 1f
    private var hopPhase = 0f
    /** 해적선 갑판 범위 — 이 밖으로는 안 나온다 (Level 이 정한다, 대표님 2026-10-09) */
    var deckLeft = Float.NEGATIVE_INFINITY
    var deckRight = Float.POSITIVE_INFINITY

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        val dx = playerX - x
        dashTimer -= deltaTime
        when {
            dashing > 0f -> { dashing -= deltaTime; velocityX = dir * 620f; jumpZ = 0f }
            windup > 0f -> {
                windup -= deltaTime; velocityX = 0f; jumpZ = 0f
                if (windup <= 0f) { dashing = 0.45f; dir = sign(dx) }
            }
            else -> {
                facingRight = dx > 0f
                velocityX = if (abs(dx) > 160f) sign(dx) * 110f else 0f
                if (abs(velocityX) > 1f) { hopPhase += deltaTime * 10f; jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 8f } else jumpZ = 0f
                if (dashTimer <= 0f && abs(dx) < 700f) { windup = 0.45f; dashTimer = 2.5f }
            }
        }
        super.update(deltaTime, playerX, playerY)
        if (x < deckLeft || x + width > deckRight) {
            x = x.coerceIn(deckLeft, (deckRight - width).coerceAtLeast(deckLeft))
            y = Terrain.groundY(x + width / 2f)
        }
        if (dashing > 0f) facingRight = dir > 0f
    }

    override fun draw(canvas: Canvas) {
        // 돌진 직전엔 몸을 웅크리며 떤다
        if (windup > 0f) jumpZ = sin(animTime * 60f) * 3f
        drawSprite(canvas, SpriteManager.pirateCat)
        drawHpBar(canvas)
    }
}

// ============ 새끼 고양이 (대표님 2026-09-27) ============
/**
 * 새끼 고양이 — HP 3 (처음 6 → 대표님 정정) · 닿으면 **하트 반 칸**. 치즈냥이를 작게 줄인 모습(무늬는 치즈냥이처럼 무작위).
 * 종종걸음으로 빠르게 쫓아와 문다. 속도·크기(치즈냥이의 60%)·배치는 Claude 판단. 아군 냥이에게는 1 피해.
 */
class KittenCat(x: Float, y: Float, val coat: com.jamcat.nayngapo.engine.CatCoat = com.jamcat.nayngapo.engine.CatCoat.randomForEnemy())
    : Enemy(x, y, 3, 1) {
    override val width = 52f
    override val height = 58f
    override val halfHeart: Boolean get() = true
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var attackAnim = 0f      // 0보다 크면 냥냥펀치 모션 (치즈냥이와 똑같이 — 대표님 2026-09-27)

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        val dx = playerX - x
        velocityX = if (abs(dx) < 600f) sign(dx) * 190f else sign(dx) * 80f
        // 코앞까지 붙으면 냥냥펀치 모션
        if (attackAnim > 0f) attackAnim -= deltaTime
        else if (abs(dx) < 80f) attackAnim = 0.5f
        if (abs(velocityX) > 1f) {
            hopPhase += deltaTime * 12f
            jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 4f
        } else jumpZ = 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        // 치즈냥이와 같은 공격·이동·대기 애니메이션을 작게 줄여 재생
        val frames = when {
            attackAnim > 0f -> coat.attack
            abs(velocityX) > 1f -> coat.walk
            else -> coat.idle
        }
        val fps = if (attackAnim > 0f) 8f else 9f
        drawSprite(canvas, small(frame(frames, fps)))
        drawHpBar(canvas)
    }

    companion object {
        private val cache = HashMap<Bitmap, Bitmap>()
        /** 무늬별 치즈냥이 그림을 60% 로 줄여 한 번만 만들어 둔다 */
        fun small(src: Bitmap): Bitmap = cache.getOrPut(src) {
            Bitmap.createScaledBitmap(src, (src.width * 0.6f).toInt(), (src.height * 0.6f).toInt(), true)
        }
    }
}

// ============ 머드냥이 (기획9 `Plan/기획9_머드냥이.jpg`, 대표님 2026-09-27) ============
/**
 * 머드냥이 — HP 20 · 공격력 2. **늪 속에 숨어 있어 평소엔 안 보인다**(늪 위로 등만 살짝 볼록).
 * **인간이 늪에 빠졌을 때만 공격**한다: 늪에서 튀어 올라 인간 머리 위를 넘어 반대편으로 뛰어들며, 닿으면 하트 2.
 * **늪 밖으로는 못 나온다** — 공격할 때만 잠깐 튀어나오고, 그때만 때릴 수 있다.
 * 늪마다 한 마리 (스테이지 배치는 Claude 판단). 숨어 있으면 스테이지 클리어 조건에서 빠진다(Level.stageCleared).
 * 도약 시간 0.9초·높이·다시 뛰기까지 1.6초는 Claude 판단.
 */
open class MudCat(private val swamp: Swamp) : Enemy(swamp.x + swamp.width / 2f - 46f, swamp.y, 20, 2) {
    /** 튀어나올 때 그림 (꿀냥이는 꿀색) */
    protected open val sprite: android.graphics.Bitmap get() = SpriteManager.mudCatEvil

    override val width = 92f
    override val height = 90f
    override val contactDamage = 2

    /** 화면이 매 프레임 알려 준다: 인간이 이 늪에 빠져 있는가, 인간 가운데 x */
    var preyInSwamp = false
    var preyX = 0f

    private var leapT = -1f           // 뛰는 중이면 0~1
    private var fromX = 0f
    private var toX = 0f
    private var rest = 0.6f           // 다시 뛰기까지
    private var bubble = (Math.random() * 3.0).toFloat()

    val isHidden: Boolean get() = leapT < 0f

    /** 이 x 가 제 늪 안쪽인가 */
    fun inSwampOf(cx: Float) = swamp.swallows(cx)
    override val isTangible: Boolean get() = !isHidden

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        animTime += deltaTime
        bubble += deltaTime
        y = swamp.y
        if (leapT >= 0f) {
            leapT += deltaTime / LEAP_TIME
            val t = leapT.coerceAtMost(1f)
            x = fromX + (toX - fromX) * t
            jumpZ = -4f * LEAP_HEIGHT * t * (1f - t)            // 포물선
            facingRight = toX > fromX
            if (leapT >= 1f) { leapT = -1f; jumpZ = 0f; rest = REST_TIME }
            return
        }
        jumpZ = 0f
        if (rest > 0f) rest -= deltaTime
        if (preyInSwamp && rest <= 0f) {
            // 인간 머리 위를 넘어 반대편으로 — 도착점도 늪 안쪽
            val c = x + width / 2f
            val side = if (c < preyX) 1f else -1f
            val land = (preyX + side * 150f).coerceIn(swamp.x + 30f, swamp.x + swamp.width - 30f)
            fromX = x
            toX = land - width / 2f
            leapT = 0f
        }
    }

    override fun takeDamage(amount: Int) {
        if (isTangible) super.takeDamage(amount)
    }

    override fun draw(canvas: Canvas) {
        if (isHidden) {
            // 늪 위로 진흙 등만 볼록 — 가끔 거품이 올라온다
            val cx = x + width / 2f
            val by = y + 12f
            moundRect.set(cx - 34f, by - 20f, cx + 34f, by + 14f)
            canvas.drawArc(moundRect, 180f, 180f, true, if (swamp.honey) honeyMoundPaint else moundPaint)
            canvas.drawArc(moundRect, 180f, 180f, false, moundInk)
            val t = bubble % 2.2f
            if (t < 1.2f) canvas.drawCircle(cx + 22f, by - 8f - t * 12f, 4f + t * 3f, bubbleInk)
            return
        }
        drawSprite(canvas, sprite)
        // 몸에서 진흙 방울이 떨어진다
        val cx = x + width / 2f
        for (k in 0 until 3) {
            val ph = (animTime * 3f + k * 0.33f) % 1f
            canvas.drawCircle(cx + (k - 1) * 22f, y + jumpZ + 4f + ph * 40f, 5f * (1f - ph) + 2f, moundPaint)
        }
        drawHpBar(canvas)
    }

    companion object {
        private const val LEAP_TIME = 0.9f
        private const val LEAP_HEIGHT = 120f
        private const val REST_TIME = 1.6f
        private val moundPaint = Paint().apply { color = Color.rgb(150, 102, 60); isAntiAlias = true }
        private val honeyMoundPaint = Paint().apply { color = Color.rgb(240, 160, 32); isAntiAlias = true }
        private val moundInk = Paint().apply { color = Color.rgb(40, 30, 22); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
        private val bubbleInk = Paint().apply { color = Color.rgb(132, 118, 74); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
        private val moundRect = RectF()
    }
}

/**
 * 꿀냥이 — 꿀 지역(21~30)에서는 머드냥이 대신 **꿀 늪에 꿀냥이**가 숨어 있다 (대표님 2026-10-09).
 * 동작·체력·공격은 머드냥이와 같다(HP 20 · 튀어 오를 때 하트 2). 그림만 황금빛 꿀색(GPT).
 */
class HoneyCat(swamp: Swamp) : MudCat(swamp) {
    override val sprite: android.graphics.Bitmap get() = SpriteManager.honeyCat
}

// ============ 나쁜 그린냥이 (HP 20, 슬라임 대미지 10) ============
/** 수북한 꼬리의 나쁜 편 그린냥이: 멈춰 서서 슬라임을 뱉는다 */
class GreenCat(x: Float, y: Float) : Enemy(x, y, 20, 10) {
    override val width = 96f
    override val height = 104f
    private var spitTimer = 1.6f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x

        val dist = abs(playerX - x)
        velocityX = if (dist > 420f) sign(playerX - x) * 70f else 0f

        spitTimer -= deltaTime
        if (spitTimer <= 0f && dist < Constants.ENEMY_FIRE_RANGE) {
            spitTimer = 2.2f
            val dir = if (facingRight) 1f else -1f
            pendingProjectiles.add(
                Projectile(x + width / 2f, y - height * 0.45f, 260f * dir, 0f, 10, false,
                    tint = Color.rgb(120, 190, 90), radius = 13f)
            )
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.greenCatEvil)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ 날아냥이 (HP 25, 표창 대미지 15) ============
/** 땅을 총총 뛰어다니며 표창을 던진다 (고양이는 날지 않는다) */
class FlyingCat(x: Float, y: Float) : Enemy(x, y, 25, 15) {
    override val width = 86f
    override val height = 90f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var throwTimer = 1.2f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x

        val dx = playerX - x
        velocityX = if (abs(dx) > 340f) sign(dx) * 150f else sign(dx) * 40f

        // 땅을 딛고 총총 뛴다
        hopPhase += deltaTime * 11f
        jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 14f

        throwTimer -= deltaTime
        if (throwTimer <= 0f && abs(dx) < Constants.ENEMY_FIRE_RANGE) {
            throwTimer = 1.8f
            val dir = if (facingRight) 1f else -1f
            pendingProjectiles.add(
                Projectile(x + width / 2f, y + jumpZ - height * 0.35f, 620f * dir, 0f, 15, false,
                    tint = Color.rgb(150, 154, 160), radius = 7f)
            )
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.flyingCat)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ 털북숭냥이 (HP 50, 대미지 10) ============
/** 털이 두꺼워 단단하다. 털뭉치를 발사한다 (대표님 2026-11-08 「털뭉치를 발사해」, 예전엔 털 세 가닥) */
class FluffyCat(x: Float, y: Float) : Enemy(x, y, 50, 10) {
    override val bigEyes: Boolean get() = true   // 야간 전투: 큰 흰 눈의 눈동자가 하얗게
    override val width = 100f
    override val height = 105f
    private var rollPhase = (Math.random() * Math.PI).toFloat()
    private var shootTimer = 2f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        val dx = playerX - x
        velocityX = sign(dx) * 95f

        // 털뭉치 하나를 앞으로 곧게 날린다 (빠르기·크기·간격 Claude 판단)
        shootTimer -= deltaTime
        if (shootTimer <= 0f && abs(dx) < Constants.ENEMY_FIRE_RANGE) {
            shootTimer = 2.4f
            val dir = if (facingRight) 1f else -1f
            pendingProjectiles.add(
                Projectile(x + width / 2f, y - height * 0.45f, 420f * dir, 0f, 10, false,
                    radius = 15f, isFurball = true)
            )
        }

        rollPhase += deltaTime * 7f
        jumpZ = -abs(sin(rollPhase.toDouble())).toFloat() * 8f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.fluffyCat)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ 모루냥이 (HP 75, 대미지 10) ============
/** 쇳덩이 같은 몸. 아주 느리지만 가장 단단하다 */
class AnvilCat(x: Float, y: Float) : Enemy(x, y, 75, 10) {   // 시트: 모루냥이 HP75, 대미지 10
    override val bigEyes: Boolean get() = true   // 야간 전투: 큰 흰 눈의 눈동자가 하얗게
    override val width = 112f
    override val height = 110f

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        velocityX = sign(playerX - x) * 55f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.anvilCat)
        drawHpBar(canvas)
    }
}

// ============ 똥냥이 (HP 30, 대미지 3) ============
/** 느릿느릿 다가와 고약한 냄새를 풍긴다 */
class PoopCat(x: Float, y: Float) : Enemy(x, y, 30, 3) {
    override val bigEyes: Boolean get() = true   // 야간 전투: 큰 흰 눈의 눈동자가 하얗게
    override val width = 92f
    override val height = 100f
    private var wobble = (Math.random() * Math.PI).toFloat()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        velocityX = sign(playerX - x) * 70f

        wobble += deltaTime * 4.5f
        jumpZ = -abs(sin(wobble.toDouble())).toFloat() * 6f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.poopCat)
        drawHpBar(canvas)
    }
}

// ============ Human Cat Boss (Stage 1) ============
class HumanCatBoss(x: Float, y: Float) : Enemy(x, y, 3, 5) {
    override val width = 110f
    override val height = 120f
    var phase = 1
    val maxPhases = 10
    private var actionTimer = 1.5f
    private val baseSpeed = 100f

    init { facingRight = false }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        actionTimer -= deltaTime

        if (actionTimer <= 0f) {
            actionTimer = when {
                phase <= 3 -> 1.5f; phase <= 6 -> 1.0f; phase <= 9 -> 0.7f; else -> 0.4f
            }
            val speed = baseSpeed + phase * 20f
            velocityX = sign(playerX - x) * speed
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun takeDamage(amount: Int) {
        hp -= amount
        if (hp <= 0) {
            if (phase < maxPhases) { phase++; hp = 3 } else die()
        }
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.bossHumanCat)
        val cx = x + width / 2f
        val textPaint = Paint().apply { color = Color.WHITE; textSize = 14f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        canvas.drawTr("P$phase/$maxPhases", cx, y - height - 5f, textPaint)
        drawHpBar(canvas)
    }
}

// ============ Evil Cat Boss (Stage 2) ============
class EvilCatBoss(x: Float, y: Float) : Enemy(x, y, 30, 20) {
    override val width = 145f
    override val height = 160f
    private val moveSpeed = 90f
    private var chargeTimer = 3f
    private var isCharging = false

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        chargeTimer -= deltaTime

        if (chargeTimer <= 0f && !isCharging) {
            isCharging = true
            velocityX = sign(playerX - x) * moveSpeed * 4f   // 돌진
            chargeTimer = 0.5f
        } else if (isCharging) {
            if (chargeTimer <= 0f) { isCharging = false; chargeTimer = 3f; velocityX = 0f }
        } else {
            velocityX = sign(playerX - x) * moveSpeed
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.bossEvilCat)
        drawHpBar(canvas)
    }
}

// ============ Wizard Cat Boss (Final) ============
class WizardCatBoss(x: Float, y: Float) : Enemy(x, y, 3, 250) {
    override val width = 130f
    override val height = 145f
    private var floatTimer = 0f
    private var shootTimer = 1.5f
    private val pendingProjectiles = mutableListOf<Projectile>()

    // 마법사 냥이는 데미지 크기와 상관없이 '세 번' 맞아야 쓰러진다
    override fun takeDamage(amount: Int) {
        if (hitCooldown > 0f) return
        hitCooldown = HIT_COOLDOWN
        hp -= 1
        if (hp <= 0) die()
    }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        floatTimer += deltaTime
        jumpZ = sin(floatTimer.toDouble() * 2.0).toFloat() * 20f - 30f

        val dx = playerX - x
        val dist = abs(dx)
        if (dist > 150f) {
            velocityX = sign(dx) * 70f
        } else {
            velocityX = 0f
        }

        shootTimer -= deltaTime
        if (shootTimer <= 0f && dist < Constants.ENEMY_FIRE_RANGE) {   // 화면에 보일 거리에서만 발사
            shootTimer = 1.2f
            val cx = x + width / 2f
            // 횡뷰: 속도가 다른 수평 탄막 5발
            val dir = if (facingRight) 1f else -1f
            for (speed in intArrayOf(250, 350, 450, 550, 650)) {
                pendingProjectiles.add(Projectile(cx, y - height * 0.3f, speed * dir, 0f, 50, false))
            }
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        val cx = x + width / 2f
        drawSprite(canvas, SpriteManager.bossWizardCat)

        // Aura effect
        val auraPaint = Paint().apply {
            color = Color.rgb(180, 100, 255)
            alpha = (80 + (sin(floatTimer.toDouble() * 4) * 40).toInt()).coerceIn(0, 255)
            style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
        }
        canvas.drawCircle(cx, y + jumpZ - height / 2f, 85f, auraPaint)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ 순간냥이 보스 (10스테이지 — 기획4·5) ============
/**
 * 초원의 최종 보스. 순간이동으로 사라졌다가 플레이어 근처에 다시 나타난다.
 * 몸에 닿으면 데미지 3 (기획4: "보스에 맞으면 데미지 3").
 */
class TeleportCatBoss(x: Float, y: Float) : Enemy(x, y, 60, 3) {
    override val width = 150f
    override val height = 160f
    override val contactDamage = 3

    private var teleportTimer = 2.8f
    private var vanishTimer = 0f          // 0보다 크면 사라져 있는 중
    private var floatPhase = 0f
    private var appearFlash = 0f          // 나타난 직후의 반짝임 연출

    override val isTangible: Boolean get() = vanishTimer <= 0f

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        floatPhase += deltaTime

        if (vanishTimer > 0f) {
            vanishTimer -= deltaTime
            if (vanishTimer <= 0f) {
                // 플레이어의 앞 또는 뒤, 화면 안쪽에 다시 나타난다
                val side = if (Math.random() < 0.5) -1f else 1f
                x = playerX + side * (320f + (Math.random() * 160f).toFloat())
                teleportTimer = 2.5f
                appearFlash = 0.35f
            }
            return                        // 사라진 동안은 움직이지도, 닿지도 않는다
        }

        if (appearFlash > 0f) appearFlash -= deltaTime
        facingRight = playerX > x
        teleportTimer -= deltaTime
        if (teleportTimer <= 0f) {
            vanishTimer = 0.55f
            velocityX = 0f
            return
        }

        velocityX = sign(playerX - x) * 120f
        jumpZ = sin(floatPhase.toDouble() * 3.0).toFloat() * 6f
        super.update(deltaTime, playerX, playerY)
    }

    override fun takeDamage(amount: Int) {
        if (isTangible) super.takeDamage(amount)
    }

    override fun draw(canvas: Canvas) {
        if (!isTangible) return
        drawSprite(canvas, SpriteManager.teleportCat)
        if (appearFlash > 0f) {
            val cx = x + width / 2f
            val p = Paint().apply {
                color = Color.argb((200 * appearFlash / 0.35f).toInt(), 255, 255, 255)
                style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true
            }
            canvas.drawCircle(cx, y + jumpZ - height / 2f, 90f * (1.3f - appearFlash / 0.35f), p)
        }
        drawHpBar(canvas)
    }
}

// ============ 덩굴냥이 (정글 12~20) ============
/**
 * 나뭇잎 모자를 쓰고 나무 덩굴에 매달려 그네처럼 날아다니는 냥이.
 * 덩굴 뿌리가 플레이어 쪽으로 천천히 미끄러져 오고, 흔들림이 가장 낮아질 때
 * 점프 공격이나 로켓으로 맞출 수 있다. 공중에 있으므로 바닥 함정은 무시한다.
 */
class VineCat(x: Float, y: Float) : Enemy(x, y, 15, 2) {
    override val width = 84f
    override val height = 92f

    private var anchorX = x
    private val anchorY = Constants.GROUND_Y - 700f
    private val vineLen = 580f + (Math.random() * 100f).toFloat()
    private var phase = (Math.random() * Math.PI * 2).toFloat()
    private val swingSpeed = 1.4f + (Math.random() * 0.6f).toFloat()
    private val amp = 0.85f + (Math.random() * 0.35f).toFloat()   // 흔들림 폭(라디안)

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        // 공중 그네: 지면 스냅을 하는 super.update는 부르지 않는다
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        animTime += deltaTime

        phase += deltaTime * swingSpeed
        anchorX += sign(playerX - anchorX) * 60f * deltaTime

        val angle = sin(phase.toDouble()).toFloat() * amp
        x = anchorX + sin(angle.toDouble()).toFloat() * vineLen - width / 2f
        y = anchorY + kotlin.math.cos(angle.toDouble()).toFloat() * vineLen

        // 머리 방향 = 그네가 나아가는 방향
        facingRight = kotlin.math.cos(phase.toDouble()) * amp >= 0
    }

    override fun draw(canvas: Canvas) {
        val cx = x + width / 2f
        val topY = y - height + 6f

        // 저 아래 지면의 그림자
        val gy = Terrain.groundY(cx)
        vineShadow.set(cx - 34f, gy - 5f, cx + 34f, gy + 7f)
        canvas.drawOval(vineShadow, vineShadowPaint)

        // 덩굴 줄 + 잎사귀
        canvas.drawLine(anchorX, anchorY, cx, topY, vinePaint)
        for (k in 1..3) {
            val t = k / 4f
            val lx = anchorX + (cx - anchorX) * t
            val ly = anchorY + (topY - anchorY) * t
            val side = if (k % 2 == 0) 1f else -1f
            canvas.drawOval(lx, ly - 6f, lx + 24f * side, ly + 6f, vineLeafPaint)
        }

        // 나뭇잎 모자를 쓴 냥이 (원안 스프라이트 + 모자 합성본)
        val bmp = if (facingRight) SpriteManager.vineCat
            else SpriteManager.getFlipped(SpriteManager.vineCat)
        canvas.drawBitmap(bmp, cx - bmp.width / 2f, y - bmp.height + 8f, null)

        drawHpBar(canvas)
    }

    private companion object {
        val vinePaint = Paint().apply {
            color = Color.rgb(62, 124, 58); style = Paint.Style.STROKE
            strokeWidth = 8f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        }
        val vineLeafPaint = Paint().apply { color = Color.rgb(74, 142, 66); isAntiAlias = true }
        val vineShadowPaint = Paint().apply { color = Color.argb(40, 0, 0, 0); isAntiAlias = true }
        val vineShadow = RectF()
    }
}

// ============ 51~60 빙산 (대표님 2026-10-25 「51-60까지 만들어」「거기에 나오는 고양이들은 그냥 만들고 고양이 도감에 추가해」) ============
/**
 * 얼음냥이 — 그림·능력은 기획서에 없어 전부 Claude 판단: HP 20 · 공격 5(아군 피해).
 * 다른 냥이처럼 인간 쪽으로 걸어오다 240 앞에서 멈추고, 2.5초마다 얼음 덩이를 포물선으로 던진다(사거리 650).
 * 얼음 덩이에 맞으면 하트 1 + 1초 동안 얼어서 못 움직인다. 그림 cat_ice (오른쪽을 보는 하늘색 냥이, 두꺼운 꼬리).
 */
class IceCat(x: Float, y: Float) : Enemy(x, y, 20, 5) {
    override val width = 80f
    override val height = 80f
    private val moveSpeed = 65f
    private var wanderTimer = 0f
    private var wanderDirX = 0f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var throwTimer = 1.2f + Math.random().toFloat()
    private var throwAnim = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        wanderTimer -= deltaTime
        val dx = playerX - x
        val dist = abs(dx)
        velocityX = when {
            dist < 240f -> 0f
            dist < 900f -> sign(dx) * moveSpeed
            else -> {
                if (wanderTimer <= 0f) {
                    wanderTimer = 1f + (Math.random() * 2f).toFloat()
                    wanderDirX = (if (Math.random() < 0.5) -1f else 1f) * moveSpeed
                }
                wanderDirX
            }
        }
        throwTimer -= deltaTime
        if (throwAnim > 0f) throwAnim -= deltaTime
        if (throwTimer <= 0f && dist < THROW_RANGE) {
            throwTimer = THROW_EVERY; throwAnim = 0.3f
            val dir = if (dx >= 0f) 1f else -1f
            // 날아가는 시간 0.85초에 인간 쪽에 떨어지게 (너무 가깝거나 멀면 속도를 자른다)
            val vx = dir * (dist / FLIGHT_TIME).coerceIn(220f, 700f)
            val vy = -GRAVITY * FLIGHT_TIME / 2f
            val r = 13f
            pendingProjectiles.add(
                Projectile(x + width / 2f + dir * 28f - r, y - height * 0.95f - r, vx, vy, attackPower, false,
                    radius = r, gravity = GRAVITY, isIceChunk = true, iceFreeze = FREEZE_TIME)
            )
        }
        if (abs(velocityX) > 1f) { hopPhase += deltaTime * 9f; jumpZ = -abs(sin(hopPhase)) * 5f }
        else jumpZ = if (throwAnim > 0f) -8f * sin(throwAnim / 0.3f * Math.PI.toFloat()) else 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.iceCat)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val THROW_RANGE = 650f
        private const val THROW_EVERY = 2.5f
        private const val FLIGHT_TIME = 0.85f
        private const val GRAVITY = 900f
        /** 얼음 덩이에 맞은 인간이 얼어 있는 시간 (Claude 판단) */
        const val FREEZE_TIME = 1f
    }
}

/**
 * 잡몹 순간냥이 (52~60 표 「순간냥이」, 2026-10-25) — 10스테이지 보스 순간냥이(TeleportCatBoss)의 작은 판.
 * HP 20 · 공격 3(아군 피해) · 닿으면 하트 1 (Claude 판단). 인간이 700 안에 있으면 3.5~5초마다 사라졌다가 인간 앞이나 뒤 220~340 에 나타난다.
 */
class TeleportCat(x: Float, y: Float) : Enemy(x, y, 20, 3) {
    override val width = 80f
    override val height = 90f
    private var teleportTimer = 3.5f + (Math.random() * 1.5).toFloat()
    private var vanishTimer = 0f
    private var floatPhase = (Math.random() * 6.0).toFloat()
    private var appearFlash = 0f
    private val flashPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }

    override val isTangible: Boolean get() = vanishTimer <= 0f

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        floatPhase += deltaTime
        if (vanishTimer > 0f) {
            if (hitCooldown > 0f) hitCooldown -= deltaTime
            vanishTimer -= deltaTime
            if (vanishTimer <= 0f) {
                val side = if (Math.random() < 0.5) -1f else 1f
                x = (playerX + side * (220f + (Math.random() * 120f).toFloat()))
                    .coerceIn(Constants.PLAY_AREA_LEFT + 5f, Constants.PLAY_AREA_RIGHT - width - 5f)
                y = Terrain.groundY(x + width / 2f)
                teleportTimer = 3.5f + (Math.random() * 1.5).toFloat()
                appearFlash = 0.3f
            }
            return
        }
        if (appearFlash > 0f) appearFlash -= deltaTime
        facingRight = playerX > x
        val dx = playerX - x
        teleportTimer -= deltaTime
        if (teleportTimer <= 0f && abs(dx) < 700f) {
            vanishTimer = 0.5f
            velocityX = 0f
            return
        }
        velocityX = if (abs(dx) < 900f) sign(dx) * 90f else 0f
        jumpZ = sin(floatPhase * 3f) * 4f
        super.update(deltaTime, playerX, playerY)
    }

    override fun takeDamage(amount: Int) {
        if (isTangible) super.takeDamage(amount)
    }

    override fun draw(canvas: Canvas) {
        if (!isTangible) return
        drawSprite(canvas, SpriteManager.teleportCatMob)
        if (appearFlash > 0f) {
            flashPaint.color = Color.argb((200 * appearFlash / 0.3f).toInt(), 255, 255, 255)
            canvas.drawCircle(x + width / 2f, y + jumpZ - height / 2f, 60f * (1.3f - appearFlash / 0.3f), flashPaint)
        }
        drawHpBar(canvas)
    }
}

/**
 * 빙산냥이 — 60스테이지 보스 (기획15 `Plan/기획15_빙산보스.jpg`): HP 45 · 필살기 5.
 * 커다란 네모 얼음 덩어리 위에 작은 냥이가 엎드려 있고, 인간 쪽 옆구리에서 얼음 팔 둘(둥근 주먹)이 나온다. 그림 cat_iceberg_boss.
 * 공격을 차례로 돌아가며 쓴다: ① 주먹 휘두르기 ② 팔 뻗기 ③ 땅에서 얼음 가시가 솟고 얼음 조각 2개를 던짐 ④ 필살기 — 아래 팔이 땅을 따라 길게 뻗어 인간을 얼린다.
 * **인간이 맞으면 하트 4(필살기 5), 3초 동안 얼어 못 움직인다** (기획15). 간격·거리·빠르기는 Claude 판단.
 */
class IcebergCatBoss(x: Float, y: Float) : Enemy(x, y, 45, 4) {
    override val width = BLOCK_W
    override val height = 180f
    override val contactDamage = 4
    override val freezeOnHit: Float get() = FREEZE_TIME
    override val strikeDamage: Int get() = if (state == State.ULT) ULT_DAMAGE else contactDamage

    private enum class State { WALK, PUNCH, STRETCH, SPIKES, ULT }
    private var state = State.WALK
    private var stateT = 0f
    private var cooldown = 2.2f
    private var nextAttack = 0
    /** 필살기: 아래 팔이 땅을 따라 뻗은 길이 · 뻗을 목표 길이 */
    private var ultExt = 0f
    private var ultReach = 0f
    private var spikeX = 0f
    private var thrown = 0
    private var lastPlayerCx = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()
    private val strike = RectF()

    private val bmp: Bitmap get() = SpriteManager.icebergBoss ?: fallback

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        val pcx = playerX + Constants.PLAYER_WIDTH / 2f
        lastPlayerCx = pcx
        val cx = x + width / 2f
        val dx = pcx - cx
        val front = abs(dx) - width / 2f        // 얼음 덩어리 앞면에서 인간까지
        stateT += deltaTime
        when (state) {
            State.WALK -> {
                facingRight = dx > 0f
                velocityX = if (front > 260f && abs(dx) < 1400f) sign(dx) * 70f else 0f
                cooldown -= deltaTime
                if (cooldown <= 0f && abs(dx) < 1100f) startAttack(front)
            }
            State.PUNCH -> { velocityX = 0f; if (stateT >= PUNCH_TIME) endAttack() }
            State.STRETCH -> { velocityX = 0f; if (stateT >= STRETCH_TIME) endAttack() }
            State.SPIKES -> {
                velocityX = 0f
                // 쿵 뛰어올랐다가(0.6초) 내려앉는 순간 인간 발밑에 얼음 가시 셋이 솟는다
                jumpZ = if (stateT < SPIKE_WARN) -sin(stateT / SPIKE_WARN * Math.PI.toFloat()) * 60f else 0f
                if (stateT >= SPIKE_WARN && thrown == 0) {
                    thrown = 1
                    for (k in -1..1) {
                        val sx = spikeX + k * 75f
                        val r = 34f
                        pendingProjectiles.add(Projectile(sx - r, Terrain.groundY(sx) - r * 2f, 0f, 0f, attackPower, false,
                            radius = r, fuse = 0.9f, isIceSpike = true, iceFreeze = FREEZE_TIME, heartDamage = contactDamage))
                    }
                }
                // 얼음 조각 2개를 인간 쪽으로 던진다
                if ((stateT >= 0.8f && thrown == 1) || (stateT >= 1.05f && thrown == 2)) {
                    thrown++
                    throwChunk(pcx)
                }
                if (stateT >= SPIKES_TIME) endAttack()
            }
            State.ULT -> {
                velocityX = 0f
                if (stateT >= ULT_WINDUP) {
                    if (ultReach == 0f) {
                        val (sx, _) = world(LOWER_SHOULDER_U, LOWER_SHOULDER_V)
                        ultReach = (abs(pcx - sx) - (LOWER_REST_FIST_U - LOWER_SHOULDER_U)).coerceIn(150f, ULT_MAX_REACH)
                    }
                    val t = stateT - ULT_WINDUP
                    val outT = ultReach / ULT_SPEED
                    ultExt = when {
                        t < outT -> t * ULT_SPEED
                        t < outT + ULT_HOLD -> ultReach
                        else -> (ultReach - (t - outT - ULT_HOLD) * ULT_SPEED * 0.8f).coerceAtLeast(0f)
                    }
                    if (t > outT + ULT_HOLD && ultExt <= 0f) endAttack()
                }
            }
        }
        super.update(deltaTime, playerX, playerY)
        if (state != State.WALK) facingRight = facingLock
    }

    private var facingLock = true

    private fun startAttack(front: Float) {
        stateT = 0f; thrown = 0; ultExt = 0f; ultReach = 0f
        facingLock = facingRight
        var a = nextAttack % 4
        // 주먹 휘두르기는 코앞에서만 — 멀면 팔 뻗기로 (Claude 판단)
        if (a == 0 && front > 300f) a = 1
        nextAttack = a + 1
        state = when (a) {
            0 -> State.PUNCH
            1 -> State.STRETCH
            2 -> { spikeX = lastPlayerCx; State.SPIKES }
            else -> State.ULT
        }
    }

    private fun endAttack() {
        state = State.WALK; stateT = 0f; ultExt = 0f; jumpZ = 0f
        cooldown = ATTACK_GAP
    }

    private fun throwChunk(pcx: Float) {
        val (sx, sy) = world(UPPER_REST_FIST_U, UPPER_REST_FIST_V)
        val d = pcx - sx
        val vx = sign(d) * (abs(d) / 0.9f).coerceIn(250f, 800f)
        val r = 16f
        pendingProjectiles.add(Projectile(sx - r, sy - r, vx, -GRAVITY * 0.45f, attackPower, false,
            radius = r, gravity = GRAVITY, isIceChunk = true, iceFreeze = FREEZE_TIME, heartDamage = contactDamage))
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    // ===== 팔 모양 (그림 좌표 u·v = 오른쪽을 볼 때 그림 왼쪽 위 기준) =====
    /** 그림 좌표 → 월드 좌표 (왼쪽을 보면 얼음 덩어리 기준으로 거울) */
    private fun world(u: Float, v: Float): Pair<Float, Float> {
        val top = y + jumpZ + 8f - bmp.height
        val wx = if (facingRight) x + u else x + width - u
        return wx to top + v
    }

    /** 위 팔 주먹의 그림 좌표 (각도·길이로) */
    private fun upperFist(): Pair<Float, Float> {
        val t = stateT
        var ang = REST_ANGLE
        var len = UPPER_LEN
        when (state) {
            State.PUNCH -> {
                ang = when {
                    t < 0.35f -> lerp(REST_ANGLE, -75f, t / 0.35f)
                    t < 0.6f -> lerp(-75f, 40f, (t - 0.35f) / 0.25f)
                    else -> lerp(40f, REST_ANGLE, ((t - 0.6f) / 0.3f).coerceAtMost(1f))
                }
                len = UPPER_LEN + 35f
            }
            State.STRETCH -> {
                ang = -5f
                len = UPPER_LEN + STRETCH_LEN * stretchFrac()
            }
            else -> Unit
        }
        val rad = Math.toRadians(ang.toDouble())
        return (UPPER_SHOULDER_U + len * kotlin.math.cos(rad).toFloat()) to (UPPER_SHOULDER_V + len * kotlin.math.sin(rad).toFloat())
    }

    private fun stretchFrac(): Float {
        val t = stateT
        return when {
            t < 0.3f -> t / 0.3f
            t < 0.6f -> 1f
            t < 1.1f -> 1f - (t - 0.6f) / 0.5f
            else -> 0f
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun strikeBox(): RectF? {
        val active = when (state) {
            State.PUNCH -> stateT in 0.4f..0.75f
            State.STRETCH -> stretchFrac() > 0.3f
            State.ULT -> ultExt > 30f
            else -> false
        }
        if (!active) return null
        val (fu, fv) = if (state == State.ULT) (LOWER_REST_FIST_U + ultExt) to ULT_FIST_V else upperFist()
        val (wx, wy) = world(fu, fv)
        strike.set(wx - FIST_R, wy - FIST_R, wx + FIST_R, wy + FIST_R)
        return strike
    }

    override fun draw(canvas: Canvas) {
        val img0 = bmp
        lastSprite = img0
        lastSpriteHeight = img0.height.toFloat()
        val shake = if (state == State.ULT && stateT < ULT_WINDUP) sin(animTime * 70f) * 4f else 0f
        val feet = y + jumpZ + windLift
        val top = feet + 8f - img0.height
        // 그림자
        shadowRect.set(x - 10f, y - 6f, x + width + 10f, y + 8f)
        canvas.drawOval(shadowRect, bossShadow)
        // 필살기 모으기: 얼음 덩어리 둘레가 파랗게 빛난다
        if (state == State.ULT && stateT < ULT_WINDUP) {
            glowPaint.alpha = (90 + 80 * sin(animTime * 14f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(x + width / 2f, feet - height * 0.5f, width * 0.8f, glowPaint)
        }
        // 얼음 가시 자리: 솟기 전 땅에 금이 가며 빛난다
        if (state == State.SPIKES && stateT < SPIKE_WARN) {
            val gy = Terrain.groundY(spikeX)
            warnPaint.alpha = (110 + 100 * sin(animTime * 20f)).toInt().coerceIn(0, 255)
            shadowRect.set(spikeX - 130f, gy - 10f, spikeX + 130f, gy + 10f)
            canvas.drawOval(shadowRect, warnPaint)
            for (k in -2..2) canvas.drawLine(spikeX + k * 22f, gy, spikeX + k * 34f, gy - 6f - abs(k) * 2f, crackPaint)
        }
        val img = if (facingRight) img0 else SpriteManager.getFlipped(img0)
        val left = (if (facingRight) x else x + width - img0.width) + shake
        val codeArms = state == State.PUNCH || state == State.STRETCH || state == State.ULT
        if (codeArms) {
            // 그림의 팔은 잘라 내고 얼음 덩어리(+위의 냥이)만 그린 뒤, 움직이는 팔을 코드로 그린다
            canvas.save()
            canvas.clipRect(x - 4f + shake, top - 10f, x + BLOCK_CLIP + shake, feet + 20f)
            canvas.drawBitmap(img, left, top, null)
            canvas.restore()
            val (us, vs) = world(UPPER_SHOULDER_U, UPPER_SHOULDER_V)
            val (uf, vf) = upperFist().let { world(it.first, it.second) }
            val (ls, lvs) = world(LOWER_SHOULDER_U, LOWER_SHOULDER_V)
            if (state == State.ULT) {
                // 아래 팔: 어깨에서 땅으로 내려가 땅을 따라 길게
                val (ex, ey) = world(LOWER_ELBOW_U, ULT_FIST_V)
                val (fx, fy) = world(LOWER_REST_FIST_U + ultExt, ULT_FIST_V)
                drawArm(canvas, floatArrayOf(ls + shake, lvs, ex + shake, ey, fx + shake, fy))
            } else {
                val (fx, fy) = world(LOWER_REST_FIST_U, LOWER_REST_FIST_V)
                drawArm(canvas, floatArrayOf(ls, lvs, fx, fy))
            }
            drawArm(canvas, floatArrayOf(us + shake, vs, uf + shake, vf))
        } else {
            canvas.drawBitmap(img, left, top, null)
        }
        drawHpBar(canvas)
    }

    /** 굵고 둥근 하늘색 얼음 팔(검은 외곽선) + 끝의 둥근 주먹. pts = 어깨부터 주먹까지 꺾이는 점들 (x, y, x, y …) */
    private fun drawArm(canvas: Canvas, pts: FloatArray) {
        armPath.reset()
        armPath.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { armPath.lineTo(pts[i], pts[i + 1]); i += 2 }
        canvas.drawPath(armPath, armEdge)
        canvas.drawPath(armPath, armFill)
        val fx = pts[pts.size - 2]; val fy = pts[pts.size - 1]
        canvas.drawCircle(fx, fy, FIST_R, fistFill)
        canvas.drawCircle(fx, fy, FIST_R, fistEdge)
        canvas.drawCircle(fx - FIST_R * 0.35f, fy - FIST_R * 0.35f, FIST_R * 0.2f, fistShine)
    }

    companion object {
        /** 얼음 덩어리(맞는 몸) 너비 — 그림 360×193 에서 덩어리는 왼쪽 약 210 */
        const val BLOCK_W = 210f
        private const val BLOCK_CLIP = 214f
        /** 맞으면 얼어 있는 시간 3초 (기획15) · 필살기 5 */
        const val FREEZE_TIME = 3f
        const val ULT_DAMAGE = 5
        private const val ATTACK_GAP = 1.6f
        private const val PUNCH_TIME = 0.9f
        private const val STRETCH_TIME = 1.2f
        private const val SPIKE_WARN = 0.6f
        private const val SPIKES_TIME = 1.5f
        private const val ULT_WINDUP = 0.9f
        private const val ULT_SPEED = 1500f
        private const val ULT_HOLD = 0.6f
        private const val ULT_MAX_REACH = 1000f
        private const val GRAVITY = 1000f
        // 그림(360×193) 기준 팔 자리
        private const val UPPER_SHOULDER_U = 205f
        private const val UPPER_SHOULDER_V = 88f
        private const val UPPER_REST_FIST_U = 318f
        private const val UPPER_REST_FIST_V = 58f
        private const val LOWER_SHOULDER_U = 198f
        private const val LOWER_SHOULDER_V = 140f
        private const val LOWER_REST_FIST_U = 312f
        private const val LOWER_REST_FIST_V = 152f
        private const val LOWER_ELBOW_U = 228f
        private const val ULT_FIST_V = 157f
        private const val UPPER_LEN = 117f
        private const val REST_ANGLE = -15f
        private const val STRETCH_LEN = 330f
        private const val FIST_R = 30f

        private val shadowRect = RectF()
        private val armPath = Path()
        private val bossShadow = Paint().apply { color = Color.argb(55, 0, 0, 0); isAntiAlias = true }
        private val glowPaint = Paint().apply { color = Color.rgb(120, 200, 255); isAntiAlias = true }
        private val warnPaint = Paint().apply { color = Color.rgb(150, 215, 255); isAntiAlias = true }
        private val crackPaint = Paint().apply { color = Color.rgb(40, 80, 120); strokeWidth = 3f; isAntiAlias = true }
        private val armEdge = Paint().apply {
            color = Color.rgb(20, 24, 32); style = Paint.Style.STROKE; strokeWidth = 34f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
        }
        private val armFill = Paint().apply {
            color = Color.rgb(170, 220, 250); style = Paint.Style.STROKE; strokeWidth = 27f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
        }
        private val fistFill = Paint().apply { color = Color.rgb(170, 220, 250); isAntiAlias = true }
        private val fistEdge = Paint().apply { color = Color.rgb(20, 24, 32); style = Paint.Style.STROKE; strokeWidth = 3.5f; isAntiAlias = true }
        private val fistShine = Paint().apply { color = Color.WHITE; isAntiAlias = true }

        /** 그림(cat_iceberg_boss)이 없을 때 코드로 그린 빙산냥이 — 네모 얼음 + 위에 엎드린 냥이 + 오른쪽 팔 둘 */
        private val fallback: Bitmap by lazy {
            val b = Bitmap.createBitmap(360, 193, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val edge = Paint().apply { color = Color.rgb(20, 24, 32); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
            val ice = Paint().apply { color = Color.rgb(150, 205, 245); isAntiAlias = true }
            val iceLit = Paint().apply { color = Color.rgb(205, 236, 255); isAntiAlias = true }
            val cat = Paint().apply { color = Color.rgb(130, 190, 240); isAntiAlias = true }
            // 팔 (덩어리 뒤에서 나온다)
            for ((s, f) in listOf((205f to 88f) to (318f to 58f), (198f to 140f) to (312f to 152f))) {
                c.drawLine(s.first, s.second, f.first, f.second, armEdge)
                c.drawLine(s.first, s.second, f.first, f.second, armFill)
                c.drawCircle(f.first, f.second, 30f, fistFill); c.drawCircle(f.first, f.second, 30f, edge)
            }
            c.drawRect(6f, 44f, 210f, 189f, ice)
            c.drawRect(6f, 44f, 70f, 189f, iceLit)
            c.drawRect(6f, 44f, 210f, 189f, edge)
            // 엎드린 냥이
            c.drawOval(70f, 14f, 170f, 50f, cat); c.drawOval(70f, 14f, 170f, 50f, edge)
            c.drawCircle(160f, 24f, 18f, cat); c.drawCircle(160f, 24f, 18f, edge)
            val ear = Path().apply { moveTo(148f, 10f); lineTo(152f, -2f + 6f); lineTo(160f, 8f); close(); moveTo(164f, 8f); lineTo(172f, 2f); lineTo(174f, 14f); close() }
            c.drawPath(ear, cat); c.drawPath(ear, edge)
            val eye = Paint().apply { color = Color.rgb(20, 24, 32); isAntiAlias = true }
            c.drawCircle(158f, 22f, 2.5f, eye); c.drawCircle(168f, 22f, 2.5f, eye)
            b
        }
    }
}
