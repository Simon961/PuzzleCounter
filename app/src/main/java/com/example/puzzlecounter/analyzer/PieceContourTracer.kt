package com.example.puzzlecounter.analyzer

import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sqrt

/** Contour of one segmented piece in image coordinates; [area] is the pixel count of its mask. */
class PieceContour(val path: Path, val area: Int)

/**
 * Converts the binary instance masks produced by the segmentation model into smooth contour paths.
 */
object PieceContourTracer {

    private data class Point(val x: Int, val y: Int)

    /**
     * Builds the contour for a binary [mask] of size [width] x [height] whose top-left pixel lies at
     * ([offsetX], [offsetY]) in image coordinates. Only the largest connected component is used.
     * Falls back to a rounded [fallbackRect] if the mask is empty or degenerate.
     */
    fun contourFromMask(
        mask: BooleanArray,
        width: Int,
        height: Int,
        offsetX: Int,
        offsetY: Int,
        fallbackRect: Rect,
        minFillRatio: Float = 0.05f,
    ): PieceContour {
        val fallbackArea = fallbackRect.width() * fallbackRect.height()
        if (width < 3 || height < 3) return fallback(fallbackRect, fallbackArea)

        // 1. Pad by 1 px so the boundary tracer never touches the array border.
        val pw = width + 2
        val ph = height + 2
        val padded = BooleanArray(pw * ph)
        for (y in 0 until height) {
            System.arraycopy(mask, y * width, padded, (y + 1) * pw + 1, width)
        }

        // 2. Keep only the largest 4-connected component.
        val (component, area) = largestComponent(padded, pw)
        if (area < 8 || area < fallbackArea * minFillRatio) return fallback(fallbackRect, fallbackArea)

        // 3. Moore-neighbor boundary tracing.
        val boundary = traceMooreBoundary(component, pw, ph)
        if (boundary.size < 8) return fallback(fallbackRect, fallbackArea)

        // 4. Simplify & smooth (padding offset of 1 px is compensated here).
        val simplified = ramerDouglasPeucker(boundary, epsilon = 1.5f)
        return PieceContour(buildSmoothedPath(simplified, offsetX - 1, offsetY - 1), area)
    }

    private fun fallback(rect: Rect, area: Int) = PieceContour(
        Path().apply { addRoundRect(RectF(rect), 16f, 16f, Path.Direction.CW) },
        area
    )

    private fun largestComponent(map: BooleanArray, width: Int): Pair<BooleanArray, Int> {
        val labels = IntArray(map.size)
        var bestLabel = 0
        var bestSize = 0
        var nextLabel = 1
        val queue = IntArray(map.size)

        for (start in map.indices) {
            if (!map[start] || labels[start] != 0) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            labels[start] = nextLabel
            var size = 0
            while (head < tail) {
                val idx = queue[head++]
                size++
                val x = idx % width
                if (x > 0) { val n = idx - 1; if (map[n] && labels[n] == 0) { labels[n] = nextLabel; queue[tail++] = n } }
                if (x < width - 1) { val n = idx + 1; if (map[n] && labels[n] == 0) { labels[n] = nextLabel; queue[tail++] = n } }
                if (idx >= width) { val n = idx - width; if (map[n] && labels[n] == 0) { labels[n] = nextLabel; queue[tail++] = n } }
                if (idx < map.size - width) { val n = idx + width; if (map[n] && labels[n] == 0) { labels[n] = nextLabel; queue[tail++] = n } }
            }
            if (size > bestSize) {
                bestSize = size
                bestLabel = nextLabel
            }
            nextLabel++
        }
        return BooleanArray(map.size) { labels[it] == bestLabel && bestLabel != 0 } to bestSize
    }

    /**
     * Moore-neighbor tracing starting at the first foreground pixel in raster order
     * (its western neighbor is guaranteed to be background).
     */
    private fun traceMooreBoundary(binaryMap: BooleanArray, width: Int, height: Int): List<Point> {
        val startIdx = binaryMap.indexOfFirst { it }
        if (startIdx == -1) return emptyList()
        val startX = startIdx % width
        val startY = startIdx / width

        // 8-neighbor directions, clockwise starting from West.
        val dx = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
        val dy = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)

        val boundary = mutableListOf(Point(startX, startY))
        var currX = startX
        var currY = startY
        var backtrackDir = 0
        val maxSteps = 4 * (width + height) * 4

        var steps = 0
        while (steps++ < maxSteps) {
            var foundNext = false
            for (i in 0 until 8) {
                val dir = (backtrackDir + i) % 8
                val nx = currX + dx[dir]
                val ny = currY + dy[dir]
                if (nx in 0 until width && ny in 0 until height && binaryMap[ny * width + nx]) {
                    currX = nx
                    currY = ny
                    backtrackDir = (dir + 5) % 8
                    foundNext = true
                    break
                }
            }
            if (!foundNext || (currX == startX && currY == startY)) break
            boundary.add(Point(currX, currY))
        }
        return boundary
    }

    /** Ramer-Douglas-Peucker polyline simplification. */
    private fun ramerDouglasPeucker(points: List<Point>, epsilon: Float): List<Point> {
        if (points.size < 3) return points

        var maxDist = 0f
        var index = 0
        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                index = i
            }
        }

        return if (maxDist > epsilon) {
            val left = ramerDouglasPeucker(points.subList(0, index + 1), epsilon)
            val right = ramerDouglasPeucker(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDistance(pt: Point, lineStart: Point, lineEnd: Point): Float {
        val dx = (lineEnd.x - lineStart.x).toFloat()
        val dy = (lineEnd.y - lineStart.y).toFloat()

        if (dx == 0f && dy == 0f) {
            val px = (pt.x - lineStart.x).toFloat()
            val py = (pt.y - lineStart.y).toFloat()
            return sqrt(px * px + py * py)
        }

        val num = abs(dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        val den = sqrt(dy * dy + dx * dx)
        return num / den
    }

    /** Builds a closed path with quadratic Bezier smoothing through the contour points. */
    private fun buildSmoothedPath(points: List<Point>, offsetX: Int, offsetY: Int): Path {
        val path = Path()
        if (points.isEmpty()) return path

        val xs = FloatArray(points.size) { (points[it].x + offsetX).toFloat() }
        val ys = FloatArray(points.size) { (points[it].y + offsetY).toFloat() }
        path.moveTo(xs[0], ys[0])
        for (i in 1 until points.size) {
            val midX = (xs[i - 1] + xs[i]) / 2f
            val midY = (ys[i - 1] + ys[i]) / 2f
            path.quadTo(xs[i - 1], ys[i - 1], midX, midY)
        }
        path.lineTo(xs.last(), ys.last())
        path.close()
        return path
    }
}
