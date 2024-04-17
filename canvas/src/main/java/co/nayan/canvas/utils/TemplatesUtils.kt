package co.nayan.canvas.utils

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.Template
import kotlin.math.min

fun List<Template>.superButtons(): List<Template> {
    return filter { it.superButton == true }
}

fun List<Template>.cluster(): Map<String, MutableList<Template>> {
    val mutableMap = mutableMapOf<String, MutableList<Template>>()
    forEach { template ->
        template.templateType?.let { key ->
            val tags = (mutableMap[key] ?: mutableListOf())
            tags.add(template)
            mutableMap[key] = tags
        }
    }
    return mutableMap
}

fun String.searchTagsList(tagsList: List<Template>): Boolean {
    var isFound = false
    tagsList.forEach lit@{
        if (this.equals(it.templateName, ignoreCase = true)) {
            isFound = true
            return@lit
        }
    }

    return isFound
}

fun editDistance(word1: String, word2: String): Int {
    val n = word1.length
    val m = word2.length
    val dp = Array(n + 1) {
        IntArray(
            m + 1
        )
    }
    for (i in 0..n) {
        for (j in 0..m) {
            if (i == 0)
                dp[i][j] = j
            else if (j == 0)
                dp[i][j] = i
            else if (word1[i - 1] == word2[j - 1])
                dp[i][j] = dp[i - 1][j - 1]
            else if (i > 1 && j > 1 && word1[i - 1] == word2[j - 2] && word1[i - 2] == word2[j - 1])
                dp[i][j] = 1 + min(
                    min(dp[i - 2][j - 2], dp[i - 1][j]),
                    min(dp[i][j - 1], dp[i - 1][j - 1])
                )
            else dp[i][j] = 1 + min(dp[i][j - 1], min(dp[i - 1][j], dp[i - 1][j - 1]))
        }
    }
    return dp[n][m]
}

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