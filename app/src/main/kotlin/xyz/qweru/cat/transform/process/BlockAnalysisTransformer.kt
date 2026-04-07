package xyz.qweru.cat.transform.process

import org.objectweb.asm.Label
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LabelNode
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.analyseMethodStackHeight
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom

class BlockAnalysisTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("BlockAnalysis", "Attempts to split blocks where possible", target, opts) {
    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes) {
                if (!canTarget(entry)) continue
                val klass = entry.value

                parallel {
                    for (method in klass.methods) {
                        transformMethod(method) {
                            val frames = analyseMethodStackHeight(method)

                            createPass().insertBeforeIndexed({ ins, i ->
                                ins !is LabelNode && frames[i] == 0
                            }) { _, _, _ ->
                                InsnList().apply { add(LabelNode(Label())) }
                            }
                        }
                    }
                }
            }

            parallel.await()
        }
    }
}