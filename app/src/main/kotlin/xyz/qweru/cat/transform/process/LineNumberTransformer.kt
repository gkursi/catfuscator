package xyz.qweru.cat.transform.process

import org.objectweb.asm.tree.LineNumberNode
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.thread.createExecutorFrom

class LineNumberTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("NoFieldInit", "Moves class field initialization to clinit", target, opts) {
    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes) {
                if (!canTarget(entry)) continue
                val klass = entry.value
                parallel {
                    for (method in klass.methods) {
                        transformMethod(method) {
                            createPass().remove { it is LineNumberNode }
                        }
                    }
                }
            }
            parallel.await()
        }
    }
}