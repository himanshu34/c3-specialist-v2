package com.nayan.nayancamv2.env

import co.nayan.c3v2.core.models.SurgeMeta
import java.lang.Long.signum

class CompareByDistance : Comparator<SurgeMeta> {

    override fun compare(lhs: SurgeMeta, rhs: SurgeMeta): Int {
        // We cast here to ensure the multiplications won't overflow
        return signum(lhs.distance.toLong() - rhs.distance.toLong())
    }
}