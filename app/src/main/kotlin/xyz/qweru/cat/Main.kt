package xyz.qweru.cat

import com.github.ajalt.clikt.core.main
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.jar.readJar
import xyz.qweru.cat.util.jar.remapJar
import xyz.qweru.cat.util.jar.writeJar
import xyz.qweru.cat.transform.flow.AntiDisassembleTransformer
import xyz.qweru.cat.util.profile.Timer

fun main(args : Array<String>) =
    Configuration { Main.main(this) }
        .main(args)

object Main {
    private val logger = KotlinLogging.logger {}

    fun main(config: Configuration) {
        logger.info { "Input: ${config.input}" }
        val jar = readJar(config)

        transform(jar, config)
        remapJar(jar, config)

        logger.info { "Output: ${config.output}" }
        writeJar(jar, config)
    }

    private fun transform(jar: JarContainer, config: Configuration) {
        val timer = Timer()
//        FakeClassTransformer(jar, config)
//        FakeMethodTransformer(jar, config)

//        StringEncryptTransformer(jar, config)
//        ExcessiveLabelTransformer(jar, config)
//        GotoControlTransformer(jar, config)
//        GotoReplaceTransformer(jar, config)
//        FieldValueDefinitionTransformer(jar, config)

//        MethodCallEncryptTransformer(jar, config)

        AntiDisassembleTransformer(jar, config)

//        repeat(1) { NumberEncryptTransformer(jar, config) }
//        ArithmeticEncryptTransformer(jar, config)
//        SyntheticMethodTransformer(jar, config)
//        ClassRenameTransformer(jar, config)
//        MethodRenameTransformer(jar, config)
//        FieldRenameTransformer(jar, config)
//        LocalFieldRenameTransformer(jar, config)

//        MethodCallEncryptTransformer.Post(jar, config)
        logger.info { "Obfuscation took ${timer.time()}ms" }
    }
}