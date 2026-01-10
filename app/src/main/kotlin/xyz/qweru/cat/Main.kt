package xyz.qweru.cat

import com.github.ajalt.clikt.core.main
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarParser

fun main(args : Array<String>) =
    Configuration { Main.main(this) }
        .main(args)

object Main {
    private val logger = KotlinLogging.logger {}

    fun main(config: Configuration) {
        logger.info { "Input: ${config.input}" }
        val jar = JarParser.read(config)

        logger.info { "Output: ${config.output}" }
        JarParser.write(config, jar)
    }
}