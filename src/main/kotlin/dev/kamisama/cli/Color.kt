package dev.kamisama.cli

/**
 * ANSI color formatting for terminal output.
 */
object Color {
    /**
     * Check if colors should be enabled.
     */
    var enabled: Boolean = true

    // ANSI color codes
    private const val RESET = "\u001B[0m"
    private const val RED = "\u001B[31m"
    private const val GREEN = "\u001B[32m"
    private const val YELLOW = "\u001B[33m"

    /**
     * Apply color to a string if colors are enabled.
     */
    private fun colorize(
        text: String,
        colorCode: String,
    ): String =
        if (enabled) {
            "$colorCode$text$RESET"
        } else {
            text
        }

    // Color functions
    fun red(text: String): String = colorize(text, RED)

    fun green(text: String): String = colorize(text, GREEN)

    fun yellow(text: String): String = colorize(text, YELLOW)
}
