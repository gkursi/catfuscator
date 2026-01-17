package xyz.qweru.cat.transform.process

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.ClassBuilder
import xyz.qweru.cat.util.asm.instructions
import xyz.qweru.cat.util.asm.transformClass
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.thread.createExecutorFrom

class FieldValueDefinitionTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("NoFieldInit", "Moves class field initialization to clinit", target, opts) {
    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes) {
                if (!canTarget(entry)) continue
                val classNode = entry.value
                parallel {
                    transformClass(classNode) {
                        val _this = classNode.name

                        val staticInsns = instructions(0) {
                            for (fieldNode in classNode.fields) {
                                if (fieldNode.value == null || fieldNode.access and Opcodes.ACC_STATIC != Opcodes.ACC_STATIC) continue
                                val value = fieldNode.value
                                fieldNode.value = null
                                ldc(value)
                                storeStaticField(_this, fieldNode.name, fieldNode.desc)
                            }
                        }

                        val nonStaticInsns = instructions(0) {
                            for (fieldNode in classNode.fields) {
                                if (fieldNode.value == null || fieldNode.access and Opcodes.ACC_STATIC == Opcodes.ACC_STATIC) continue
                                val value = fieldNode.value
                                fieldNode.value = null
                                loadLocalObject(0)
                                ldc(value)
                                storeField(_this, fieldNode.name, fieldNode.desc)
                            }
                        }

                        createClInit(staticInsns)
                        createInit(nonStaticInsns)
                    }
                }
            }
            parallel.await()
        }
    }

    private fun ClassBuilder.createInit(insns: InsnList) {
        if (!insns.any()) return
        val klass = classNode

        if (klass.methods.any { it.name == "<init>" }) {
            val node = classNode.methods.first { it.name == "<init>" }!!
            transformMethod(node) {
                findFirst({ it is MethodInsnNode }) { _, _, _ ->
                    insns
                }
            }
        } else {
            method("<init>", Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "()V") {
                loadLocalObject(0)
                invokeSpecial(klass.superName ?: "java/lang/Object", "<init>", "()V")

                instructions.add(insns)
                returnVoid()
            }
        }
    }

    private fun ClassBuilder.createClInit(staticInsns: InsnList) {
        if (!staticInsns.any()) return

        if (classNode.methods.any { it.name == "<clinit>" }) {
            val node = classNode.methods.first { it.name == "<clinit>" }!!
            node.instructions = staticInsns.also {
                it.add(node.instructions)
            }
        } else {
            method("<clinit>", Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "()V") {
                instructions.add(staticInsns)
                returnVoid()
            }
        }
    }
}