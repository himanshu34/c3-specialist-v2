package com.nayan.nayancamv2.env

import android.util.Size
import java.lang.Long.signum

class CompareSizesByArea : Comparator<Size> {

    override fun compare(lhs: Size, rhs: Size): Int {
        // We cast here to ensure the multiplications won't overflow
        return signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
    }
}