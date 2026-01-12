package xyz.qweru.cat

import com.github.ajalt.clikt.core.main
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarParser
import xyz.qweru.cat.mapping.JarRemapper
import xyz.qweru.cat.transform.crash.SyntheticMethodTransformer
import xyz.qweru.cat.transform.fake.FakeClassTransformer
import xyz.qweru.cat.transform.rename.ClassRenameTransformer
import xyz.qweru.cat.transform.rename.FieldRenameTransformer
import xyz.qweru.cat.transform.rename.LocalFieldRenameTransformer
import xyz.qweru.cat.transform.rename.MethodRenameTransformer

fun main(args : Array<String>) =
    Configuration { Main.main(this) }
        .main(args)

object Main {
    private val logger = KotlinLogging.logger {}

    fun main(config: Configuration) {
        // read from disk
        logger.info { "Input: ${config.input}" }
        val jar = JarParser.read(config)

        // apply transformers
        // todo: configurable transformers
        FakeClassTransformer(jar, config)
        SyntheticMethodTransformer(jar, config)

        ClassRenameTransformer(jar, config)
        MethodRenameTransformer(jar, config)
        FieldRenameTransformer(jar, config)
        LocalFieldRenameTransformer(jar, config)

        // remap container
        JarRemapper.remap(jar, config)

        // write container to disk
        logger.info { "Output: ${config.output}" }
        JarParser.write(jar, config)
    }
}