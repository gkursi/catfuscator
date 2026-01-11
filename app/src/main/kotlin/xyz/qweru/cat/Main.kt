package xyz.qweru.cat

import com.github.ajalt.clikt.core.main
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarParser
import xyz.qweru.cat.mapping.JarRemapper

fun main(args : Array<String>) =
    Configuration { Main.main(this) }
        .main(args)

object Main {
    private val logger = KotlinLogging.logger {}

    fun main(config: Configuration) {
        logger.info { "Input: ${config.input}" }
        val jar = JarParser.read(config)

        JarRemapper.remap(jar, config)

        logger.info { "Output: ${config.output}" }
        JarParser.write(jar, config)
    }
}