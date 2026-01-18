package xyz.qweru.cat.transform.flow

import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

class ExcessiveLabelTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("TooManyLabels", "Adds a lot of labels, impacts performance a lot", target, opts) {
    val count by value("Count", "Amount of label per label", 2)
    val secondaryCount by value("Secondary Count", "Amount of extra label per label", 1)
    val jumps by value("Jumps", "Max amount of jumps to generate in the fake labels", 2)
    val jumpChance by value("Jump Chance", "Chance of jumping", 0.5)

    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes.entries) {
                if (!canTarget(entry)) continue

                val klass = entry.value
                val labelLookup = hashMapOf<Label, MutableList<LabelNode>>()

                parallel {
                    for (method in klass.methods) {
                        transformMethod(method) {
                            insertBefore({ it is LabelNode }) { ln, _, _ ->
                                ln as LabelNode
                                instructionsFor(method) {
                                    val emptyLabels = Array(secondaryCount + 1) { label() }
                                    var jumpCount = 0

                                    repeat(count) {
                                        labelLookup.computeIfAbsent(ln.label) { arrayListOf() }
                                            .add(label().also { +it })

                                        if (jumpCount < jumps && Random.nextDouble() < jumpChance) {
                                            jumpCount++
                                            instruction(ExcludedJumpInsnNode(Opcodes.GOTO, emptyLabels.random()))
                                        }
                                    }

                                    for (node in emptyLabels) {
                                        label(node)
                                        instruction(ExcludedJumpInsnNode(Opcodes.GOTO, ln))
                                    }
                                }
                            }

                            replace({ it is JumpInsnNode && it !is ExcludedJumpInsnNode && labelLookup.containsKey(it.label.label) }) { jmp, _, _ ->
                                jmp as JumpInsnNode
                                instructionsFor(method) {
                                    instruction(JumpInsnNode(jmp.opcode, labelLookup[jmp.label.label]!!.random()))
                                }
                            }
                        }
                    }
                }
            }
        }
        parallel.await()
    }

    private class ExcludedJumpInsnNode(opcode: Int, label: LabelNode) : JumpInsnNode(opcode, label)
}