package analysis

import xyz.qweru.cat.util.analysis.FastFrameStateAnalyzer
import xyz.qweru.cat.util.asm.instructions
import kotlin.test.Test

class FastFrameStateAnalyzerTest {

    /**
     * No forks
     */
    @Test
    fun linearTest() {
        val insns = instructions {
            // label()
            constant1()
            constant2()
            addInts()
            dup()
            invokeStatic("", "", "(II)Ljava/lang/Object;")
            returnInstance()
            // label()
        }

        val frames = FastFrameStateAnalyzer()
            .analyze(insns)
        val groups = frames.group()

        assert(groups[frames[0]]!!.contains(0))
    }

    @Test
    fun forkTest() {
        val insns = instructions {
            // label()
            constant1()
            constant2()
            val label = label()
            jumpIfIntSmaller(label)
            constantNull()
            invokeVirtual("", "", "()I")
            invokeStatic("", "", "(I)V")
            +label
            constantNull()
            jumpIfNonNull(startLabel)
            returnVoid()
        }

        val frames = FastFrameStateAnalyzer()
            .analyze(insns)
    }

}