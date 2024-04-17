package co.nayan.c3views.quadrilateral

import java.util.*

class QuadrilateralUtils {
    companion object {
        fun isDragging(touch: Pair<Float, Float>, quadrilateral: Quadrilateral): Boolean {
            val polyPoints = quadrilateral.getAbsoluteQuadrilateralPoints()
            val polygon = if (polyPoints.isNotEmpty())
                getMinimumPossibleBound(polyPoints) else Vector()
            return isInside(polygon, polygon.size, touch)
        }

        private fun getMinimumPossibleBound(points: List<Pair<Float, Float>>): Vector<Pair<Float, Float>> {
            val n = points.size
            val polygon = Vector<Pair<Float, Float>>()
            var l = 0
            for (i in 1 until points.size) {
                if (points[i].first < points[l].first) {
                    l = i
                }
            }

            var firstPointIndex = l
            var q: Int
            do {
                polygon.add(points[firstPointIndex])

                q = (firstPointIndex + 1) % n

                for (i in 0 until n) {
                    if (orientation(
                            points[firstPointIndex],
                            points[i],
                            points[q]
                        ) == 2
                    )
                        q = i
                }
                firstPointIndex = q

            } while (firstPointIndex != l)

            return polygon
        }

        private fun orientation(
            p: Pair<Float, Float>,
            q: Pair<Float, Float>,
            r: Pair<Float, Float>
        ): Int {
            val v =
                (q.second - p.second) * (r.first - q.first) - (q.first - p.first) * (r.second - q.second)

            if (v == 0F) return 0
            return if (v > 0) 1 else 2
        }

        private fun isInside(
            polygon: Vector<Pair<Float, Float>>,
            n: Int,
            p: Pair<Float, Float>
        ): Boolean {
            val inf = 10_000F
            if (n < 3) return false

            val extreme = Pair(inf, p.second)
            var count = 0
            var i = 0
            do {
                val next = (i + 1) % n
                if (doIntersect(polygon[i], polygon[next], p, extreme)) {
                    if (orientation(polygon[i], p, polygon[next]) == 0)
                        return onSegment(polygon[i], p, polygon[next])
                    count++
                }
                i = next
            } while (i != 0)

            return count and 1 == 1
        }

        private fun onSegment(
            p: Pair<Float, Float>,
            q: Pair<Float, Float>,
            r: Pair<Float, Float>
        ): Boolean {
            return (q.first <= p.first.coerceAtLeast(r.first) && q.first >= p.first.coerceAtMost(r.first)
                    && q.second <= p.second.coerceAtLeast(r.second) && q.second >= p.second.coerceAtMost(
                r.second
            ))
        }

        private fun doIntersect(
            p1: Pair<Float, Float>,
            q1: Pair<Float, Float>,
            p2: Pair<Float, Float>,
            q2: Pair<Float, Float>
        ): Boolean {
            val o1 = orientation(p1, q1, p2)
            val o2 = orientation(p1, q1, q2)
            val o3 = orientation(p2, q2, p1)
            val o4 = orientation(p2, q2, q1)

            return when {
                (o1 != o2 && o3 != o4) -> true
                (o1 == 0 && onSegment(p1, p2, q1)) -> true
                (o2 == 0 && onSegment(p1, q2, q1)) -> true
                (o3 == 0 && onSegment(p2, p1, q2)) -> true
                else -> (o4 == 0 && onSegment(p2, q1, q2))
            }
        }
    }
}