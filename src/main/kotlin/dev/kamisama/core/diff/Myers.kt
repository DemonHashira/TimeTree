package dev.kamisama.core.diff

/**
 * Myers O(ND) diff algorithm for computing the shortest edit script.
 */
object Myers : DiffAlgorithm {
    /** Records position during the forward pass for backtracking. */
    private data class Step(
        val k: Int,
        val x: Int,
        val y: Int,
        val prevK: Int,
        val prevX: Int,
        val prevY: Int,
    )

    /** Computes the edit sequence transforming list a into list b. */
    override fun computeEdits(
        a: List<String>,
        b: List<String>,
    ): List<DiffAlgorithm.Edit> {
        val n = a.size
        val m = b.size
        val maxD = n + m

        // Track furthest x-coordinate reached on each diagonal
        val offset = maxD + 1
        val vSize = 2 * maxD + 3
        var v = IntArray(vSize)
        var newV = IntArray(vSize)
        v[offset + 1] = 0

        // Store step information for each d-iteration
        val trace = mutableListOf<MutableMap<Int, Step>>()

        for (d in 0..maxD) {
            val stepsForD = mutableMapOf<Int, Step>()

            for (k in -d..d step 2) {
                // Choose whether to move down (insert) or right (delete)
                val fromDown = (k == -d) || (k != d && v[offset + k - 1] < v[offset + k + 1])

                val prevK = if (fromDown) k + 1 else k - 1
                val startX = if (fromDown) v[offset + k + 1] else v[offset + k - 1] + 1
                val startY = startX - k

                // Extend along matching lines
                var x = startX
                var y = startY
                while (x < n && y < m && a[x] == b[y]) {
                    x++
                    y++
                }

                newV[offset + k] = x

                stepsForD[k] =
                    Step(
                        k = k,
                        x = x,
                        y = y,
                        prevK = prevK,
                        prevX = startX,
                        prevY = startY,
                    )

                // Check if we've reached the end
                if (x >= n && y >= m) {
                    trace.add(stepsForD)
                    return backtrack(a, b, trace, d, k)
                }
            }

            trace.add(stepsForD)
            val temp = v
            v = newV
            newV = temp
        }

        return emptyList()
    }

    /** Reconstructs the edit script by walking backward through trace. */
    private fun backtrack(
        a: List<String>,
        b: List<String>,
        trace: List<Map<Int, Step>>,
        dFinal: Int,
        kFinal: Int,
    ): List<DiffAlgorithm.Edit> {
        val editsReversed = mutableListOf<DiffAlgorithm.Edit>()

        var d = dFinal
        var k = kFinal

        while (d >= 0) {
            val step = trace[d][k]!!
            val (prevK, prevX, prevY) = Triple(step.prevK, step.prevX, step.prevY)

            // Add matching lines from the snake
            var curX = step.x
            var curY = step.y
            while (curX > prevX && curY > prevY) {
                curX--
                curY--
                editsReversed += DiffAlgorithm.Edit.Keep(a[curX])
            }

            if (d == 0) break

            // Add the insert or delete operation
            if (prevK == k - 1) {
                if (prevX <= 0) {
                    error("Backtrack inconsistency: expected Delete but prevX=$prevX")
                }
                // Came from the right -> the paid step was Delete
                val deletedLine = a[prevX - 1]
                editsReversed += DiffAlgorithm.Edit.Delete(deletedLine)
            } else {
                if (prevY <= 0) {
                    error("Backtrack inconsistency: expected Insert but prevY=$prevY")
                }
                // Came from the down -> the paid step was Insert
                val insertedLine = b[prevY - 1]
                editsReversed += DiffAlgorithm.Edit.Insert(insertedLine)
            }

            d--
            k = prevK
        }

        return editsReversed.asReversed()
    }

    /** Formats edits as unified diff with context lines. */
    override fun formatUnifiedDiff(
        edits: List<DiffAlgorithm.Edit>,
        aLabel: String,
        bLabel: String,
        contextLines: Int,
    ): String {
        if (edits.isEmpty()) {
            return ""
        }

        val result = StringBuilder()
        result.append("--- $aLabel\n")
        result.append("+++ $bLabel\n")

        val hunks = groupIntoHunks(edits, contextLines)

        for (hunk in hunks) {
            val (aStart, aCount, bStart, bCount) = hunk.header
            result.append("@@ -$aStart,$aCount +$bStart,$bCount @@\n")

            for (edit in hunk.edits) {
                when (edit) {
                    is DiffAlgorithm.Edit.Keep -> result.append(" ${edit.line}\n")
                    is DiffAlgorithm.Edit.Insert -> result.append("+${edit.line}\n")
                    is DiffAlgorithm.Edit.Delete -> result.append("-${edit.line}\n")
                }
            }
        }

        return result.toString()
    }

    private data class Hunk(
        val header: HunkHeader,
        val edits: List<DiffAlgorithm.Edit>,
    )

    private data class HunkHeader(
        val aStart: Int,
        val aCount: Int,
        val bStart: Int,
        val bCount: Int,
    )

    /** Groups edits into hunks separated by unchanged context. */
    private fun groupIntoHunks(
        edits: List<DiffAlgorithm.Edit>,
        contextLines: Int,
    ): List<Hunk> {
        if (edits.isEmpty()) return emptyList()

        val hunks = mutableListOf<Hunk>()
        val currentHunkEdits = mutableListOf<DiffAlgorithm.Edit>()
        var contextCount = 0
        var aLine = 1
        var bLine = 1
        var hunkAStart = 1
        var hunkBStart = 1
        var hunkACount = 0
        var hunkBCount = 0

        for ((index, edit) in edits.withIndex()) {
            val isChange = edit is DiffAlgorithm.Edit.Insert || edit is DiffAlgorithm.Edit.Delete

            if (isChange) {
                if (currentHunkEdits.isEmpty()) {
                    val contextStart = maxOf(0, index - contextLines)
                    for (i in contextStart until index) {
                        val ctx = edits[i]
                        if (ctx is DiffAlgorithm.Edit.Keep) {
                            currentHunkEdits.add(ctx)
                            hunkACount++
                            hunkBCount++
                        }
                    }
                    hunkAStart = aLine - currentHunkEdits.size
                    hunkBStart = bLine - currentHunkEdits.size
                }

                currentHunkEdits.add(edit)
                when (edit) {
                    is DiffAlgorithm.Edit.Delete -> {
                        hunkACount++
                        aLine++
                    }

                    is DiffAlgorithm.Edit.Insert -> {
                        hunkBCount++
                        bLine++
                    }

                    is DiffAlgorithm.Edit.Keep -> error("Unreachable: Keep should not occur in isChange branch")
                }
                contextCount = 0
            } else {
                if (currentHunkEdits.isNotEmpty()) {
                    currentHunkEdits.add(edit)
                    hunkACount++
                    hunkBCount++
                    contextCount++

                    val nextChangeIndex =
                        edits
                            .subList(index + 1, edits.size)
                            .indexOfFirst { it is DiffAlgorithm.Edit.Insert || it is DiffAlgorithm.Edit.Delete }

                    val shouldClose =
                        contextCount >= contextLines &&
                            (nextChangeIndex == -1 || nextChangeIndex >= contextLines)

                    if (shouldClose) {
                        hunks.add(
                            Hunk(
                                HunkHeader(hunkAStart, hunkACount, hunkBStart, hunkBCount),
                                currentHunkEdits.toList(),
                            ),
                        )
                        currentHunkEdits.clear()
                        hunkACount = 0
                        hunkBCount = 0
                        contextCount = 0
                    }
                }
                aLine++
                bLine++
            }
        }

        if (currentHunkEdits.isNotEmpty()) {
            hunks.add(
                Hunk(
                    HunkHeader(hunkAStart, hunkACount, hunkBStart, hunkBCount),
                    currentHunkEdits.toList(),
                ),
            )
        }

        return hunks
    }
}
