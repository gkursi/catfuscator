package xyz.qweru.cat

import com.github.ajalt.clikt.core.main
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.pipeline.Pipeline
import xyz.qweru.cat.transform.encrypt.ArithmeticEncryptTransformer
import xyz.qweru.cat.transform.encrypt.MethodCallEncryptTransformer
import xyz.qweru.cat.transform.encrypt.NoConstantTransformer
import xyz.qweru.cat.transform.encrypt.NumberEncryptTransformer
import xyz.qweru.cat.transform.fake.AntiPatternTransformer
import xyz.qweru.cat.transform.fake.FakeMethodTransformer
import xyz.qweru.cat.transform.flow.ControlFlowFlattenTransformer
import xyz.qweru.cat.transform.flow.ControlFlowTransformer
import xyz.qweru.cat.transform.flow.ExcessiveLabelTransformer
import xyz.qweru.cat.transform.flow.PolymorphicFlowTransformer
import xyz.qweru.cat.transform.process.BlockAnalysisTransformer
import xyz.qweru.cat.transform.process.FieldValueDefinitionTransformer
import xyz.qweru.cat.transform.process.LineNumberTransformer
import xyz.qweru.cat.transform.rename.ClassRenameTransformer
import xyz.qweru.cat.transform.rename.FieldRenameTransformer
import xyz.qweru.cat.transform.rename.LocalFieldRenameTransformer
import xyz.qweru.cat.transform.rename.MethodRenameTransformer
import xyz.qweru.cat.transform.string.StringEncryptTransformer
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.jar.readJar
import xyz.qweru.cat.util.jar.remapJar
import xyz.qweru.cat.util.jar.writeJar
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
        val pipeline = Pipeline()

        pipeline.addPostTransformer(AntiPatternTransformer())

        pipeline.stage {
            transformer(LineNumberTransformer())
            transformer(FakeMethodTransformer())
            transformer(FieldValueDefinitionTransformer())
//            transformer(BlockAnalysisTransformer())
        }

        pipeline.stage {
//            transformer(ExcessiveLabelTransformer())
//            transformer(PolymorphicFlowTransformer())
            transformer(ControlFlowFlattenTransformer())
        }

        pipeline.stage {
            transformer(StringEncryptTransformer())
            transformer(NumberEncryptTransformer())
            transformer(NoConstantTransformer())
            transformer(MethodCallEncryptTransformer(pipeline))
            transformer(ArithmeticEncryptTransformer())
        }

        pipeline.stage {
            transformer(ClassRenameTransformer())
            transformer(MethodRenameTransformer())
            transformer(FieldRenameTransformer())
            transformer(LocalFieldRenameTransformer())
        }

        pipeline.apply(jar, config)
        logger.info { "Obfuscation took ${timer.time()}ms" }
    }
}