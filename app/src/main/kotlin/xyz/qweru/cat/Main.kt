package xyz.qweru.cat

import com.github.ajalt.clikt.core.main
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.jar.JarParser
import xyz.qweru.cat.jar.JarRemapper
import xyz.qweru.cat.transform.crash.SyntheticMethodTransformer
import xyz.qweru.cat.transform.encrypt.NumberEncryptTransformer
import xyz.qweru.cat.transform.encrypt.StringEncryptTransformer
import xyz.qweru.cat.transform.fake.FakeClassTransformer
import xyz.qweru.cat.transform.fake.FakeMethodTransformer
import xyz.qweru.cat.transform.flow.ExcessiveLabelTransformer
import xyz.qweru.cat.transform.flow.GotoReplaceTransformer
import xyz.qweru.cat.transform.process.FieldValueDefinitionTransformer
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
        transform(jar, config)

        // remap container
        JarRemapper.remap(jar, config)

        // write container to disk
        logger.info { "Output: ${config.output}" }
        JarParser.write(jar, config)
    }

    private fun transform(jar: JarContainer, config: Configuration) {
//        FakeClassTransformer(jar, config)
//        FakeMethodTransformer(jar, config)

        StringEncryptTransformer(jar, config)
        FieldValueDefinitionTransformer(jar, config)
        ExcessiveLabelTransformer(jar, config)
        GotoReplaceTransformer(jar, config)

        repeat(2) { NumberEncryptTransformer(jar, config) }
        SyntheticMethodTransformer(jar, config)
        ClassRenameTransformer(jar, config)
        MethodRenameTransformer(jar, config)
        FieldRenameTransformer(jar, config)
        LocalFieldRenameTransformer(jar, config)
    }
}