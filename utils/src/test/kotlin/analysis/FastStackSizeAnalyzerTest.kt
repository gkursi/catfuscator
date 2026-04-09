package analysis

import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.util.analysis.StackSizeAnalyzer
import xyz.qweru.cat.util.asm.ClassBuilder
import xyz.qweru.cat.util.asm.PUBLIC_STATIC
import xyz.qweru.cat.util.asm.analyseMethod
import xyz.qweru.cat.util.asm.instructions
import kotlin.test.Test

class FastStackSizeAnalyzerTest {
    @Test
    fun noForkTest() {
        val stack = StackSizeAnalyzer()
        val insns = instructions {
            constant1()
            longConstant1()
        }

        stack.analyze(insns)
        assert(stack[0] == 0)
        assert(stack[2] == 1)
        assert(stack[3] == 3)
    }

    @Test
    fun forkTest() {
        val stack = StackSizeAnalyzer()
        val insns = instructions {
            val label = label()
            constant1() // s=1

            constant1(); constant2() // s=3
            jumpIfIntGreater(label) // s=1

            constant1() // s=2
            xorInts() // s=1

            +label // s=1 on both paths
            pop() // s=0
        }

        stack.analyze(insns)

        assert(stack[1] == 0)
        assert(stack[2] == 1)
        assert(stack[3] == 2)
        assert(stack[4] == 3)
        assert(stack[5] == 1)
        assert(stack[6] == 2)
        assert(stack[7] == 1)
        assert(stack[8] == 1)
        assert(stack[9] == 0)
    }

    @Test
    fun baselineTest() {
        val cl = ClassNode()

        ClassBuilder(cl).method("", PUBLIC_STATIC, "()V") {
            val label = label()
            constant1() // s=1

            constant1(); constant2() // s=3
            jumpIfIntGreater(label) // s=1

            constant1() // s=2
            xorInts() // s=1

            +label // s=1 on both paths
            pop() // s=0
            returnVoid()
        }

        val method = cl.methods[0]
        val frames = analyseMethod(cl, method)
        val stack = StackSizeAnalyzer()
            .also { it.analyze(method.instructions) }

        for ((i, frame) in frames.withIndex()) {
            println("$i : ${frame?.stackSize} == ${stack[i]}.")
            frame?.let {
                it.stackSize == stack[i]
            }
        }

    }
}