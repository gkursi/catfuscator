package xyz.qweru.cat.config

import xyz.qweru.cat.transform.Transformer
import java.util.regex.Pattern

class ExcludeConfigValue(
    owner: Transformer,
    name: String,
    description: String
) : ConfigValue<MutableSet<Regex>>(owner, name, description, HashSet()) {
    fun exclude(klass: String): ExcludeConfigValue {
        value.add(Regex.fromLiteral(klass))
        return this
    }

    fun excludeRegex(regex: String): ExcludeConfigValue {
        value.add(Regex(regex))
        return this
    }
}