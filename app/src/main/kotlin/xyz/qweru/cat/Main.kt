package xyz.qweru.cat

import com.github.ajalt.clikt.core.main
import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicVerifier
import org.objectweb.asm.tree.analysis.SimpleVerifier
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.hierarchy.HierarchyVerifier
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.jar.readJar
import xyz.qweru.cat.util.jar.remapJar
import xyz.qweru.cat.util.jar.writeJar
import xyz.qweru.cat.transform.flow.FlatFlowTransformer

fun main(args : Array<String>) =
    _root_ide_package_.xyz.qweru.cat.util.config.Configuration { Main.main(this) }
        .main(args)

object Main {
    private val logger = KotlinLogging.logger {}

    fun main(config: xyz.qweru.cat.util.config.Configuration) {
        logger.info { "Input: ${config.input}" }
        val jar = _root_ide_package_.xyz.qweru.cat.util.jar.readJar(config)

        transform(jar, config)
        _root_ide_package_.xyz.qweru.cat.util.jar.remapJar(jar, config)

        logger.info { "Output: ${config.output}" }
        _root_ide_package_.xyz.qweru.cat.util.jar.writeJar(jar, config)
    }

    private fun transform(jar: xyz.qweru.cat.util.jar.JarContainer, config: xyz.qweru.cat.util.config.Configuration) {
//        FakeClassTransformer(jar, config)
//        FakeMethodTransformer(jar, config)

//        StringEncryptTransformer(jar, config)
//        ExcessiveLabelTransformer(jar, config)
//        GotoControlTransformer(jar, config)
//        GotoReplaceTransformer(jar, config)
//        FieldValueDefinitionTransformer(jar, config)

//        MethodCallEncryptTransformer(jar, config)

        FlatFlowTransformer(jar, config)

//        repeat(1) { NumberEncryptTransformer(jar, config) }
//        ArithmeticEncryptTransformer(jar, config)
//        SyntheticMethodTransformer(jar, config)
//        ClassRenameTransformer(jar, config)
//        MethodRenameTransformer(jar, config)
//        FieldRenameTransformer(jar, config)
//        LocalFieldRenameTransformer(jar, config)

//        MethodCallEncryptTransformer.Post(jar, config)

        for (kl in jar.classes) {
            for (method in kl.value.methods) {
                Analyzer(HierarchyVerifier(jar.hierarchy)).analyze(kl.value.name, method)
            }
        }
    }
}