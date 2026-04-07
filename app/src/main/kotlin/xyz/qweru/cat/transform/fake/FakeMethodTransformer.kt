package xyz.qweru.cat.transform.fake

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.CMethodNode
import xyz.qweru.cat.util.thread.createExecutorFrom
import xyz.qweru.cat.util.asm.transformClass
import kotlin.random.Random

class FakeMethodTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("CloneMethod", "Generates fake method copies", target, opts) {
    private val prefix by value("Prefix", "Method prefix", "fakeMethod")
    private val chance by value("Chance", "Chance of a duplicate method", 0.75)
    private val count by value("Count", "Amount of duplicate methods to create", 3)

    init {
        target.apply {
            val parallel = createExecutorFrom(opts)
            for (entry in classes) {
                if (!canTarget(entry) || entry.value.methods.isEmpty()) continue
                parallel {
                    val node = entry.value
                    val methods = node.methods
                    node.methods = arrayListOf()

                    for (m in methods) {
                        val methodNode = CMethodNode()
                        node.methods.add(methodNode)

                        m.accept(methodNode)
                        methodNode.access = m.access
                        methodNode.name = m.name
                        methodNode.desc = m.desc
                        methodNode.signature = m.signature
                        methodNode.exceptions = m.exceptions
                        methodNode.tryCatchBlocks = m.tryCatchBlocks
                        methodNode.localVariables = m.localVariables

                        if (m.name == "<clinit>" || m.name == "<init>" || Random.nextDouble() > chance) {
                            continue
                        }

                        repeat(count) {
                            val copy = MethodNode()
                            methodNode.accept(copy)

                            copy.access = methodNode.access
                            copy.name = "$prefix$$it$${methodNode.name}"
                            copy.desc = methodNode.desc
                            copy.signature = methodNode.signature

                            copy.exceptions = methodNode.exceptions
                                .toTypedArray()
                                .toMutableList()

                            copy.tryCatchBlocks = methodNode.tryCatchBlocks
                                .toTypedArray()
                                .toMutableList()

                            copy.localVariables = methodNode.localVariables
                                .toTypedArray()
                                .toMutableList()

                            methodNode.duplicates.add(copy)
                            node.methods.add(copy)
                        }
                    }
                }
            }

            parallel.await()
        }
    }
}