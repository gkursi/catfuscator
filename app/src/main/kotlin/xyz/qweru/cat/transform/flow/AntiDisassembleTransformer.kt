package xyz.qweru.cat.transform.flow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.InsnBuilder
import xyz.qweru.cat.util.asm.analyseMethod
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.generate.pickRandom
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

private val logger = KotlinLogging.logger {  }

class AntiDisassembleTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("AntiDisassemble", "who even uses the assembler in 2026, CFR is all you need", target, opts) {
    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes.entries) {
                if (!canTarget(entry)) continue
                val klass = entry.value

                parallel {
                    for (method in klass.methods) {
                        transformMethod(method) {
                            val frames = analyseMethod(klass, method)
                            val fakeLabels = hashMapOf<FrameState, LabelNode>()
                            val generatedEnd = LabelNode(Label())
                            val first = LabelNode(Label())
                            val last = LabelNode(Label())

                            val generatedInsns = instructionsFor(method) {
                                +first
                                jump(generatedEnd)
                            }

                            val pass = createPass().insertBefore(::isValid) { _, _, i ->
                                val frame = frames[i] ?: return@insertBefore InsnList()
                                val labelA = LabelNode(Label())
                                val labelB = LabelNode(Label())

                                generatedInsns.add(instructionsFor(method) {
                                    +labelA
                                    val fakeLabel = fakeLabels.computeIfAbsent(FrameState.of(frame)) {
                                        LabelNode(Label())
                                    }
                                    pickRandom(
                                        {
                                            ldc(Random.nextInt(Int.MAX_VALUE / 2, Int.MAX_VALUE))
                                            jumpIfIntGreaterEq(fakeLabel)
                                        },
                                        {
                                            ldc(Random.nextInt(Int.MIN_VALUE, Int.MIN_VALUE / 2))
                                            jumpIfIntSmallerEq(fakeLabel)
                                        }
                                    )
                                    jump(labelB)
                                })

                                instructionsFor(method) {
                                    ldc(Random.nextInt(Int.MIN_VALUE / 2 + 1, Int.MAX_VALUE / 2))
                                    jump(labelA)
                                    +labelB
                                }
                            }

                            pass.insertBeforeIndexed({ _, insnIndex -> insnIndex == 0 }) { _, _, _ ->
                                instructionsFor(method) {
                                    +generatedInsns
                                    +generatedEnd
                                }
                            }

                            instructions.add(instructionsFor(method) {
                                jump(endLabel)
                                for (label in fakeLabels.values) {
                                    +label
                                    newObject("java/lang/NullPointerException", "()V") {}
                                    throwException()
                                }
                                +last
                            })
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
//                && it !is JumpInsnNode
                && it !is FrameNode
                && (it.opcode < Opcodes.IRETURN || it.opcode > Opcodes.RETURN)

    private fun InsnBuilder.createLookup(control: LabelNode): TableSwitchInsnNode {
        val post = label()
        jump(post)
        +control
        val default = label()
        val table = TableSwitchInsnNode(0, 0, default)
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
            fun of(vararg frames: Frame<BasicValue>): FrameState {
                val locals = arrayListOf<String>()
                val stack = arrayListOf<String>()

                for (frame in frames) {
                    for (loc in 0..<frame.locals) {
                        locals += frame.getLocal(loc).type?.descriptor ?: "uninitialized"
                    }

                    for (st in 0..<frame.stackSize) {
                        stack += frame.getStack(st).type?.descriptor ?: "uninitialized"
                    }

                    locals += " ;;; "
                    stack += " ;;; "
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