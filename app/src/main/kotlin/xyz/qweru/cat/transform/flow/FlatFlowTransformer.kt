package xyz.qweru.cat.transform.flow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.InsnBuilder
import xyz.qweru.cat.util.asm.analyseMethod
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.thread.createExecutorFrom

private val logger = KotlinLogging.logger {  }

class FlatFlowTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("FlatFlow", "Flatten flow", target, opts) {
    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes.entries) {
                if (!canTarget(entry)) continue
                val klass = entry.value

                parallel {
                    for (method in klass.methods) {
                        transformMethod(method) {
                            var frames = analyseMethod(klass, method)
                            val controlGroups = hashMapOf<FrameState, FrameControl>()
                            val insns = InsnList()

                            createPass().insertBefore(::isValid) { _, _, i ->
                                val frame = frames[i] ?: return@insertBefore InsnList()
                                val group = controlGroups.computeIfAbsent(FrameState.of(frame)) {
                                    val group = FrameControl(LabelNode(Label()))
                                    insns.add(instructionsFor(method) {
                                        group.switch = createLookup(group.label)
                                    })
                                    return@computeIfAbsent group
                                }

                                instructionsFor(method) {
                                    ldc(group.switch!!.max)
                                    jump(group.label)
                                    +label().also {
                                        group.switch!!.labels.add(it)
                                        group.switch!!.max++
                                    }
                                }
                            }

                            insns.add(instructions)
                            instructions.clear()
                            insns.forEach(instructions::add)
                        }
                    }
                }
            }
        }
        parallel.await()
    }

    private fun isValid(it: AbstractInsnNode): Boolean =
        it !is LineNumberNode
                && it !is LabelNode
                && it !is JumpInsnNode
                && it !is FrameNode
                && (it.opcode < Opcodes.IRETURN || it.opcode > Opcodes.RETURN)

    private fun InsnBuilder.createLookup(control: LabelNode): TableSwitchInsnNode {
        val post = label()
        jump(post)
        +control
        val default = label()
        val table = TableSwitchInsnNode(0, -1, default)
        instruction(table)
        +default
        newObject("java/lang/Exception", "()V") {}
        throwException()
        +post
        return table
    }

    data class FrameControl(
        val label: LabelNode,
        val insns: ArrayList<AbstractInsnNode> = arrayListOf(),
        var switch: TableSwitchInsnNode? = null
    )

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