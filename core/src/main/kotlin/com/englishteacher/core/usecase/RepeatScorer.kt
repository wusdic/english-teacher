package com.englishteacher.core.usecase

/** Outcome of comparing a learner's spoken repeat against the target sentence. */
data class RepeatScore(
    /** 0–100 similarity. */
    val score: Int,
    val expectedTokens: List<String>,
    val spokenTokens: List<String>,
    /** Per-expected-token flag: true if that word was matched in the learner's attempt. */
    val matchedExpected: List<Boolean>,
) {
    val passed: Boolean get() = score >= PASS_THRESHOLD

    companion object {
        const val PASS_THRESHOLD = 80
    }
}

/**
 * Scores how closely a learner repeated a target sentence. Pure and deterministic.
 *
 * Comparison is word-level and punctuation/case-insensitive, using a longest-common-subsequence
 * alignment so word order matters but minor extra/missing words are tolerated.
 */
class RepeatScorer {
    fun score(
        target: String,
        spoken: String,
    ): RepeatScore {
        val expected = tokenize(target)
        val said = tokenize(spoken)

        if (expected.isEmpty()) {
            return RepeatScore(
                score = if (said.isEmpty()) 100 else 0,
                expectedTokens = expected,
                spokenTokens = said,
                matchedExpected = emptyList(),
            )
        }

        val matchedFlags = BooleanArray(expected.size)
        val lcsLength = longestCommonSubsequence(expected, said, matchedFlags)

        // Similarity penalises both missed expected words and spurious extra words.
        val denominator = maxOf(expected.size, said.size)
        val raw = if (denominator == 0) 1.0 else lcsLength.toDouble() / denominator
        val score = (raw * 100).toInt().coerceIn(0, 100)

        return RepeatScore(
            score = score,
            expectedTokens = expected,
            spokenTokens = said,
            matchedExpected = matchedFlags.toList(),
        )
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .map { if (it.isLetterOrDigit() || it == '\'') it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }

    /**
     * Standard LCS length, additionally marking which [expected] tokens participate in the
     * subsequence (for UI highlighting of missed words).
     */
    private fun longestCommonSubsequence(
        expected: List<String>,
        said: List<String>,
        matchedExpected: BooleanArray,
    ): Int {
        val n = expected.size
        val m = said.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] =
                    if (expected[i] == said[j]) {
                        1 + dp[i + 1][j + 1]
                    } else {
                        maxOf(dp[i + 1][j], dp[i][j + 1])
                    }
            }
        }
        // Walk the table to flag matched expected tokens.
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                expected[i] == said[j] -> {
                    matchedExpected[i] = true
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        return dp[0][0]
    }
}
