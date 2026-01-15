package xyz.qweru.cat.transform.crash

import org.objectweb.asm.Opcodes
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer

class SyntheticMethodTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("SyntheticMethods", "Add the synthetic and bridge flags to all methods to crash some decompilers", target, opts) {
    init {
        target.apply {
            for (node in classes.entries) {
                if (!canTarget(node)) continue
                val klass = node.value
                for (method in klass.methods) {
                    method.access = method.access or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE
                }
            }
        }
    }
}