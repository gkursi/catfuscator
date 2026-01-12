package xyz.qweru.cat.config

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import java.io.File

class Configuration(private val callback: Configuration.() -> Unit) : CliktCommand() {
    val input: String by option(
        "--input",
        help = "Path to the target jar file"
    )
        .required()
        .validate {
            require(File(it).isFile) { "The provided path is not a file" }
            require(it.endsWith(".jar")) { "The provided path is not a JAR file" }
        }

    val noOverwrite by option(
        "--no-overwrite",
        help = "Prevents from overwriting existing files",
        eager = true
    )
        .flag()

    val output: String by option(
        "--output",
        help = "Path to the output jar file"
    )
        .required()
        .validate {
            require(!noOverwrite || !File(it).exists()) { "The provided path already exists" }
        }


    /* Transformer options */

    val threadTransform by option(
        "--thread-transform",
        help = "Enable/disable multithreading for transforming classes"
    )
        .boolean()
        .default(true)

    val threadTransformCapacity by option(
        "--thread-transform-capacity",
        help = "Tasks per thread for transforming classes"
    )
        .int()
        .default(5)

    /* Remapping options */

    val remapManifest by option(
        "--remap-metadata",
        help = "Remap jar metadata"
    )
        .boolean()
        .default(true)

    val threadRemap by option(
        "--thread-remap",
        help = "Enable/disable multithreading for remapping"
    )
        .boolean()
        .default(true)

    val threadRemapCapacity by option(
        "--thread-remap-capacity",
        help = "Tasks per thread for remapping"
    )
        .int()
        .default(20)

    /* ASM parsing options */

    val strip by option(
        "--strip",
        help = "Strip source debug elements"
    )
        .flag()

    val threadAsm by option(
        "--thread-asm",
        help = "Multithread ASM parsing and bytecode generation, generally not recommended"
    )
        .flag()

    val threadAsmCapacity by option(
        "--thread-asm-capacity",
        help = "Tasks per thread for ASM multithreading"
    )
        .int()
        .default(50)

    override fun run() = callback()
}