package xyz.qweru.cat

import com.github.ajalt.clikt.core.main
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.jar.JarParser
import xyz.qweru.cat.jar.JarRemapper
import xyz.qweru.cat.transform.crash.SyntheticMethodTransformer
import xyz.qweru.cat.transform.encrypt.ArithmeticEncryptTransformer
import xyz.qweru.cat.transform.encrypt.MethodCallEncryptTransformer
import xyz.qweru.cat.transform.encrypt.NumberEncryptTransformer
import xyz.qweru.cat.transform.encrypt.StringEncryptTransformer
import xyz.qweru.cat.transform.flow.ExcessiveLabelTransformer
import xyz.qweru.cat.transform.flow.GotoControlTransformer
import xyz.qweru.cat.transform.flow.GotoReplaceTransformer
import xyz.qweru.cat.transform.flow.GotoSwitchTransformer
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

//        GotoSwitchTransformer(jar, config)
        StringEncryptTransformer(jar, config)
        GotoControlTransformer(jar, config)
//        GotoReplaceTransformer(jar, config)
        FieldValueDefinitionTransformer(jar, config)
        ExcessiveLabelTransformer(jar, config)

        MethodCallEncryptTransformer(jar, config)

        repeat(1) { NumberEncryptTransformer(jar, config) }
        ArithmeticEncryptTransformer(jar, config)
        SyntheticMethodTransformer(jar, config)
        ClassRenameTransformer(jar, config)
        MethodRenameTransformer(jar, config)
        FieldRenameTransformer(jar, config)
        LocalFieldRenameTransformer(jar, config)

        MethodCallEncryptTransformer.Post(jar, config)
    }
}