package dev.kamisama

import com.github.ajalt.clikt.core.main

/**
 * Application entry point.
 */
fun main(args: Array<String>) {
    dev.kamisama.cli.CommandRouter
        .build()
        .main(args)
}
