package xyz.qweru.cat.transform.flow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.InsnBuilder
import xyz.qweru.cat.util.asm.analyseMethod
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.thread.createExecutorFrom

private val logger = KotlinLogging.logger {  }

class GotoControlTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("GotoControl", "Obfuscate goto instructions", target, opts) {
    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes.entries) {
                if (!canTarget(entry)) continue
                val klass = entry.value

                parallel {
                    for (method in klass.methods) {
                        transformMethod(method) {
                            // todo: cache FrameState instead of recomputing for every pass
                            var frames = analyseMethod(klass, method)
                            val controlGroups = hashMapOf<FrameState, FrameControl>()

                            createPass().find({ it is JumpInsnNode && it.opcode <= Opcodes.GOTO }) { _, _, i ->
                                val frame = frames[i] ?: return@find
                                controlGroups.computeIfAbsent(FrameState.of(frame)) {
                                    FrameControl(LabelNode(Label()))
                                }
                            }

                            val insns = instructions + instructionsFor(method) {
                                for (group in controlGroups) {
                                    val control = group.value
                                    control.switch = createLookup(control.label)
                                }
                            }

                            instructions.clear()
                            insns.forEach(instructions::add)

                            frames = analyseMethod(klass, method) // todo: ^^

                            createPass().wrap({ it is JumpInsnNode && it.opcode <= Opcodes.GOTO }) {
                                pre = { jmp, _, i ->
                                    instructionsFor(method) {
                                        jmp as JumpInsnNode
                                        val frame = frames[i] ?: return@instructionsFor
                                        val state = FrameState.of(frame)
                                        val control = controlGroups[state]!!
                                        val labels = control.switch!!.labels
                                        ldc(labels.size)
                                        if (jmp.opcode != Opcodes.GOTO) {

                                            if (jmp.opcode >= Opcodes.IFEQ && jmp.opcode <= Opcodes.IFLE) {
                                                dup_x1()
                                            } else {
                                                dup_x2()
                                            }

                                            pop()
                                        }
                                        labels.add(jmp.label)
                                        control.switch!!.max++
                                        jmp.label = control.label
                                    }
                                }
                                post = { jmp, _, i ->
                                    instructionsFor(method) {
                                        if (frames[i] == null || jmp.opcode == Opcodes.GOTO) {
                                            return@instructionsFor
                                        }
                                        pop()
                                    }
                                }
                            }

                            // also do the call hiding thing with methodhandles
                            // please thank you mrpp mrpp meow mreow mrow :3
                        }
                    }
                }
            }
        }
        parallel.await()
    }

    private fun InsnBuilder.createLookup(control: LabelNode): TableSwitchInsnNode {
        val post = label()
        jump(post)
        ldc(0)
        +control
        val default = label()
        val table = TableSwitchInsnNode(0, 0, default, default)
        instruction(table)
        +default
        newObject("java/lang/Exception", "()V") {}
        throwEx()
        +post
        return table
    }

    data class FrameControl(val label: LabelNode, var switch: TableSwitchInsnNode? = null)

    data class FrameState(val locals: Array<String>, val stack: Array<String>) {

        companion object {
            fun of(frame: Frame<BasicValue>): FrameState {
                val locals = arrayListOf<String>()
                val stack = arrayListOf<String>()

                for (loc in 0..<frame.locals) {
                    locals += frame.getLocal(loc).type?.descriptor ?: "uninitialized"
                }

                for (st in 0..<frame.stackSize) {
                    stack += frame.getStack(st).type?.descriptor ?: "uninitialized"
                }

                return FrameState(locals.toTypedArray(), stack.toTypedArray())
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FrameState

            if (!locals.contentEquals(other.locals)) return false
            if (!stack.contentEquals(other.stack)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = locals.contentHashCode()
            result = 31 * result + stack.contentHashCode()
            return result
        }
    }
}