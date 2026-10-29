package com.logus.app.journeydetail

import com.logus.app.record.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

/**
 * S17 DAY RECAP "영상 다운로드"로 만드는 영상(인스타그램 릴스 크기, 2026-10-22 팀 결정)의 화면 배치.
 * 위 HEADER 높이에는 로고·여정 이름·날짜·시각을 쓰고, 그 아래(본문)에 영상 칸 또는 지도를 놓는다.
 */
object ReelLayout {
    const val WIDTH = 1080
    const val HEIGHT = 1920
    const val HEADER = 300
    const val BODY = HEIGHT - HEADER

    /** 칸 사이 흰 선 두께(픽셀) */
    const val DIVIDER = 4
    const val FRAME_RATE = 30
}

/** 첫 지도 장면 길이·이동 장면 길이(그중 앞부분은 이동, 나머지는 도착한 모습)·영상을 못 받은 장면 길이(밀리초) */
const val EXPORT_FIRST_MAP_MS = 2_000L
const val EXPORT_MOVE_MAP_MS = 3_000L
const val EXPORT_MOVE_PART_MS = 1_600L
const val EXPORT_EMPTY_SCENE_MS = 2_000L

/** 영상 칸 하나의 자리: 위에서부터의 위치와 높이(픽셀) */
data class LaneRect(val top: Int, val height: Int)

/**
 * 저장 영상 속 장면 하나: 시작 시각·길이(밀리초), 앱의 장면(RecapStep), 영상 칸 자리들,
 * 소리를 낼 칸(영상이 있는 칸 중 맨 위, 없으면 null).
 */
data class ExportScene(
    val startMs: Long,
    val durationMs: Long,
    val step: RecapStep,
    val lanes: List<LaneRect>,
    val audioLane: Int?,
)

/**
 * 앱의 DAY RECAP 장면들 → 저장 영상 장면들. durationsMs 는 받아 둔 영상 파일 길이(기록 id → 밀리초)로,
 * 여기에 없는 영상(받지 못함)은 검은 칸이 된다.
 * 영상 장면 길이는 그 장면 영상들 중 가장 짧은 것(0.1초 단위로 내림)이라 칸들이 함께 끝난다.
 */
fun planExport(steps: List<RecapStep>, durationsMs: Map<String, Long>): List<ExportScene> {
    var start = 0L
    return steps.map { step ->
        val scene = when (step) {
            is RecapMapScene -> ExportScene(
                startMs = start,
                durationMs = if (step.fromMinute == null) EXPORT_FIRST_MAP_MS else EXPORT_MOVE_MAP_MS,
                step = step,
                lanes = emptyList(),
                audioLane = null,
            )
            is RecapScene -> {
                val available = step.clips.map { clip -> clip.log?.id?.let { durationsMs[it] } }
                val shortest = available.filterNotNull().minOrNull()
                ExportScene(
                    startMs = start,
                    durationMs = shortest?.let { (it / 100) * 100 }?.takeIf { it >= 500 } ?: EXPORT_EMPTY_SCENE_MS,
                    step = step,
                    lanes = laneRects(step.clips.size),
                    audioLane = available.indexOfFirst { it != null }.takeIf { it >= 0 && shortest != null && shortest >= 500 },
                )
            }
        }
        start += scene.durationMs
        scene
    }
}

/** 칸 n개를 본문에 위에서부터 나눈다(칸 높이는 짝수: 영상 만들기가 짝수 크기를 좋아해서) */
fun laneRects(count: Int): List<LaneRect> {
    if (count <= 0) return emptyList()
    val height = ((ReelLayout.BODY - (count - 1) * ReelLayout.DIVIDER) / count) and 1.inv()
    return List(count) { i ->
        val top = ReelLayout.HEADER + i * (height + ReelLayout.DIVIDER)
        // 마지막 칸은 남은 높이를 채운다(맨 아래가 비지 않게)
        LaneRect(top, if (i == count - 1) (ReelLayout.HEIGHT - top) and 1.inv() else height)
    }
}

/** 지도 장면 안 시각 localMs 에서 핀 이동 진행(0~1)과 도착했는지. 앱의 지도 장면과 같은 규칙 */
fun mapProgress(step: RecapMapScene, localMs: Long): Pair<Float, Boolean> {
    if (step.fromMinute == null) return 1f to true
    val t = (localMs.toFloat() / EXPORT_MOVE_PART_MS).coerceIn(0f, 1f)
    val eased = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2 // 천천히 출발해 천천히 도착
    return eased to (localMs >= EXPORT_MOVE_PART_MS)
}

/**
 * 지도 사진의 카메라(웹 메르카토르, MapLibre 와 같은 512픽셀 타일 기준). center 는 0~1 로 정규화한 좌표.
 * 지도 사진(MapSnapshotter)과 핀 위치 계산이 같은 카메라를 써서 핀이 지도와 맞는다.
 */
data class ReelCamera(val centerX: Double, val centerY: Double, val zoom: Double, val width: Int, val height: Int) {
    private val worldPx get() = 512.0 * 2.0.pow(zoom)

    /** 좌표 → 지도 사진 안 픽셀 위치 */
    fun pixel(p: GeoPoint): Pair<Float, Float> =
        ((mercatorX(p.lng) - centerX) * worldPx + width / 2.0).toFloat() to
            ((mercatorY(p.lat) - centerY) * worldPx + height / 2.0).toFloat()

    /** 카메라 가운데 좌표(지도 사진을 찍을 때 쓴다) */
    val center: GeoPoint
        get() = GeoPoint(Math.toDegrees(atan(sinh(PI * (1 - 2 * centerY)))), centerX * 360 - 180)
}

/** 점들이 모두 들어오게 맞춘 카메라(가장자리 padding 픽셀, 가장 크게는 maxZoom) */
fun fitReelCamera(points: List<GeoPoint>, width: Int, height: Int, padding: Int, maxZoom: Double = 16.0): ReelCamera {
    if (points.isEmpty()) return ReelCamera(0.5, 0.5, 1.0, width, height)
    val xs = points.map { mercatorX(it.lng) }
    val ys = points.map { mercatorY(it.lat) }
    val cx = (xs.min() + xs.max()) / 2
    val cy = (ys.min() + ys.max()) / 2
    val dx = xs.max() - xs.min()
    val dy = ys.max() - ys.min()
    val zx = if (dx > 0) log2((width - 2.0 * padding) / (dx * 512)) else maxZoom
    val zy = if (dy > 0) log2((height - 2.0 * padding) / (dy * 512)) else maxZoom
    return ReelCamera(cx, cy, min(maxZoom, min(zx, zy)), width, height)
}

private fun mercatorX(lng: Double) = (lng + 180) / 360

private fun mercatorY(lat: Double): Double {
    val s = sin(Math.toRadians(lat)).coerceIn(-0.9999, 0.9999)
    return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
}
