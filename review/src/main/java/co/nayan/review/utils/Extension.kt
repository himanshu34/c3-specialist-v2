package co.nayan.review.utils

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.review.R

fun RecyclerView.betterSmoothScrollToPosition(targetItem: Int) {
    layoutManager?.apply {
        val maxScroll = 10
        when (this) {
            is LinearLayoutManager -> {
                val topItem = findFirstVisibleItemPosition()
                val distance = topItem - targetItem
                val anchorItem = when {
                    distance > maxScroll -> targetItem + maxScroll
                    distance < -maxScroll -> targetItem - maxScroll
                    else -> topItem
                }
                if (anchorItem != topItem) scrollToPosition(anchorItem)
                post {
                    smoothScrollToPosition(targetItem)
                }
            }
            else -> smoothScrollToPosition(targetItem)
        }
    }
}

fun getUserCategoryDrawable(userCategory: String?): Int? {
    return when (userCategory) {
        "Gold" -> R.drawable.ic_gold_medal
        "Silver" -> R.drawable.ic_silver_medal
        "Bronze" -> R.drawable.ic_bronze_medal
        "New User" -> R.drawable.ic_blue_medal
        else -> null
    }
}