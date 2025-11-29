package dev.kamisama.core.diff

/**
 * Interface for line-based diff algorithms.
 */
interface DiffAlgorithm {
    // Single edit operation.
    sealed class Edit {
        data class Keep(
            val line: String,
        ) : Edit()

        data class Insert(
            val line: String,
        ) : Edit()

        data class Delete(
            val line: String,
        ) : Edit()
    }

    // Computes the edit sequence transforming list a into list b.
    fun computeEdits(
        a: List<String>,
        b: List<String>,
    ): List<Edit>

    // Formats edits as unified diff with context lines.
    fun formatUnifiedDiff(
        edits: List<Edit>,
        aLabel: String = "a",
        bLabel: String = "b",
        contextLines: Int = 3,
    ): String
}
