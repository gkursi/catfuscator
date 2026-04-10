package xyz.qweru.cat.transform.flow

import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.*
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

class ControlFlowFlattenTransformer : Transformer("ControlFlowFlatten", "Flatten control flow graphs") {

    val shuffle by value("Shuffle", "Shuffle case order", true)
    val onlyEmpty by value("Only Empty", "Only create labels when the stack is empty", false)

    override fun apply(target: JarContainer, opts: Configuration) {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes.entries) {
                if (!canTarget(entry)) continue
                val klass = entry.value

                parallel {
                    for (method in klass.methods) {
                        transformMethod(method) {
                            var frames = analyseMethodStack(method)
                            var frameHeight = analyseMethodStackHeight(method)
                            val controlGroups = hashMapOf<Long, ControlNode>()

                            createPassWithoutInit().find({ it is JumpInsnNode }) { _, _, i ->
                                val frame = frames[i]
                                val height = frameHeight[i]

                                if (height == -1 || height > 0 && onlyEmpty) {
                                    return@find
                                }

                                controlGroups.computeIfAbsent(frame) {
                                    ControlNode(LabelNode(Label()))
                                }
                            }

                            instructions.add(instructionsFor(method) {
                                for (group in controlGroups) {
                                    val control = group.value
                                    control.switch = createLookup(control.label)
                                }
                            })

                            frames = analyseMethodStack(method)
                            frameHeight = analyseMethodStackHeight(method)

                            createPassWithoutInit().wrap({ it is JumpInsnNode }) {
                                pre = { jmp, _, i ->
                                    instructionsFor(method) {
                                        jmp as JumpInsnNode

                                        val frame = frames[i]
                                        val control = controlGroups[frame]
                                            ?: return@instructionsFor

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
                                        if (frameHeight[i] == -1
                                            || jmp.opcode == Opcodes.GOTO
                                            || frameHeight[i] > 0 && onlyEmpty) {
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

    data class ControlNode(
        val label: LabelNode,
        var switch: LookupSwitchInsnNode? = null,
        val jumps: ArrayList<ControlJump> = arrayListOf()
    ) {
        val keyA = Random.nextInt(1, Int.MAX_VALUE / 1000)
        val keyB = Random.nextInt(1, 5)
    }

    data class ControlJump(val label: LabelNode, val ldc: LdcInsnNode)
}