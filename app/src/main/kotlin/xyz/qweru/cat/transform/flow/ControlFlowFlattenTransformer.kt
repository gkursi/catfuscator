package xyz.qweru.cat.transform.flow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
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
import kotlin.random.Random

class ControlFlowFlattenTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("ControlFlowFlatten", "Flatten control flow graphs", target, opts) {

    val shuffle by value("Shuffle", "Shuffle case order", true)
    val onlyEmpty by value("Only Empty", "Only create labels when the stack is empty", false)

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

                            createPassWithoutInit().find({ it is JumpInsnNode }) { _, _, i ->
                                val frame = frames[i]

                                if (frame == null || frame.stackSize > 0 && onlyEmpty) {
                                    return@find
                                }

                                controlGroups.computeIfAbsent(FrameState.of(frame)) {
                                    FrameControl(LabelNode(Label()))
                                }
                            }

                            instructions.add(instructionsFor(method) {
                                for (group in controlGroups) {
                                    val control = group.value
                                    control.switch = createLookup(control.label)
                                }
                            })

                            frames = analyseMethod(klass, method)

                            createPassWithoutInit().wrap({ it is JumpInsnNode }) {
                                pre = { jmp, _, i ->
                                    instructionsFor(method) {
                                        jmp as JumpInsnNode
                                        val frame = frames[i] ?: return@instructionsFor
                                        val state = FrameState.of(frame)
                                        val control = controlGroups[state] ?: return@instructionsFor

                                        val label = jmp.label
                                        val ldc = ldc(null) as LdcInsnNode
                                        control.jumps.add(ControlJump(label, ldc))

                                        if (jmp.opcode != Opcodes.GOTO) {

                                            if (jmp.opcode >= Opcodes.IFEQ && jmp.opcode <= Opcodes.IFLE) {
                                                dup_x1()
                                            } else {
                                                dup_x2()
                                            }

                                            pop()
                                        }

                                        jmp.label = control.label
                                    }
                                }
                                post = { jmp, _, i ->
                                    instructionsFor(method) {
                                        if (frames[i] == null
                                            || jmp.opcode == Opcodes.GOTO
                                            || frames[i]!!.stackSize > 0 && onlyEmpty) {
                                            return@instructionsFor
                                        }
                                        pop()
                                    }
                                }
                            }

                            for (control in controlGroups.values) {
                                val switch = control.switch!!

                                if (shuffle) {
                                    control.jumps.shuffle()
                                }

                                for ((index, jump) in control.jumps.withIndex()) {
                                    val key = (index + index / control.keyB + 1) * control.keyA
                                    jump.ldc.cst = key
                                    switch.keys.add(key)
                                    switch.labels.add(jump.label)
                                }
                            }
                        }
                    }
                }
            }
        }
        parallel.await()
    }

    private fun InsnBuilder.createLookup(control: LabelNode): LookupSwitchInsnNode {
        val post = label()
        jump(post)
        ldc(0)
        +control
        val default = label()
        val table = LookupSwitchInsnNode(default, intArrayOf(), arrayOf())
        instruction(table)
        +default
        constantNull()
        throwException()
        +post
        return table
    }

    data class FrameControl(
        val label: LabelNode,
        var switch: LookupSwitchInsnNode? = null,
        val jumps: ArrayList<ControlJump> = arrayListOf()
    ) {
        val keyA = Random.nextInt(1, Int.MAX_VALUE / 1000)
        val keyB = Random.nextInt(1, 5)
    }

    data class ControlJump(val label: LabelNode, val ldc: LdcInsnNode)

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