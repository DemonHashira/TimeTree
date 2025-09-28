package dev.kamisama

import com.github.ajalt.clikt.core.main

/**
 * Application entry point - delegates to CLI command router for argument processing.
 */
fun main(args: Array<String>) {
    // Build and execute the command tree using Clikt CLI framework
    dev.kamisama.cli.CommandRouter
        .build()
        .main(args)
}
