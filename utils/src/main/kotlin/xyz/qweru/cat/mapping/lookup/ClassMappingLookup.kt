package xyz.qweru.cat.mapping.lookup

class ClassMappingLookup(val source: String, var name: String = source) {
    val fields = StringMappingLookup()
    val methods = MethodMappingLookup()
}