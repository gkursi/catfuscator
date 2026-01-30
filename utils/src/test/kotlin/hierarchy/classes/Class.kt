package hierarchy.classes

import java.lang.RuntimeException

open class ClassA
open class ClassB : ClassA(), InterfaceB
open class ClassC : ClassB(), InterfaceA
open class ClassD : InterfaceA
open class ClassE : ClassA(), InterfaceA
open class ClassF

open class ExceptionClassA : RuntimeException()