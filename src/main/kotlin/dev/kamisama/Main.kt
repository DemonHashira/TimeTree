package dev.kamisama

import com.github.ajalt.clikt.core.main
import dev.kamisama.cli.Color
import kotlin.system.exitProcess

/**
 * Application entry point.
 */
fun main(args: Array<String>) {
    try {
        dev.kamisama.cli.CommandRouter
            .build()
            .main(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("${Color.red("fatal:")} ${e.message}")
        exitProcess(1)
    } catch (e: IllegalStateException) {
        System.err.println("${Color.red("fatal:")} ${e.message}")
        exitProcess(1)
    }
}
