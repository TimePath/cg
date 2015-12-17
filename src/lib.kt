import kotlin.reflect.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmName

class TypeMap(vararg pairs: Pair<KClass<*>, String>) : Map<String, String> by (pairs.map { it.first.jvmName to it.second }.toMap()) {
    operator fun get(t: KType) = get(t.javaType.typeName) ?: throw RuntimeException("missing $t")
}

fun unused(v: String) = "(void) $v"

val prelude = """
#define TRAIT(trait) template<class Self> struct trait##_traits
#define IMPL(T, trait) template<> struct trait##_traits<T> : withself<T>
template<class T> struct withself { using Self = T; };

#include <string>
using string = std::string;
"""

private fun KType.conv(): String = types[this]
val KClass<*>.companionOwnerName: String
    get() = qualifiedName!!.split(".").let { it[it.size - 2] }

abstract class Trait {
    open val defaults: Map<KCallable<*>, Pair<List<String>, String>>? = null
    abstract fun implement(node: KClass<*>, name: String)
}

inline fun <reified T : Trait> impl(inst: String, @Suppress("UNUSED_PARAMETER") t: T): String {
    return "IMPL($inst, ${T::class.companionOwnerName})"
}

enum class Implcontext {
    /** declaring a pure method */
    pure,
    /** declaring a trait */
    trait,
    /** implementing a trait */
    impl
}

fun impl(m: KCallable<*>, ctx: Implcontext = Implcontext.impl, f: (() -> String)? = null): String {
    val ret = m.returnType.conv() + if (m is KProperty<*>) "&" else ""
    val params = m.parameters.filter { it.kind == KParameter.Kind.VALUE }.map { it.name!! to it.type.conv() }.let {
        when (ctx) {
            Implcontext.pure -> it
            Implcontext.trait, Implcontext.impl -> listOf("self" to "Self&") + it
        }
    }.joinToString { "${it.second} ${it.first}" }
    val def = f?.let { " {${it()}}" } ?: "${if (ctx == Implcontext.pure) " = 0" else ""};"
    val prefix = when (ctx) {
        Implcontext.pure -> "virtual "
        Implcontext.trait -> "static "
        Implcontext.impl -> "static "
    }
    return prefix + "$ret ${m.name}($params)$def"
}

private fun traitImpls(traitsClasses: Collection<KClass<*>>): Map<String, Map<KCallable<*>, String?>> {
    return traitsClasses.map { trait ->
        val defs = trait.companionObjectInstance as Trait?
        assert((trait.nestedClasses - trait.companionObject).isEmpty())
        trait.simpleName!! to (trait.declaredMemberProperties.map { it to (defs?.defaults?.get(it)?.second) }.toMap<KCallable<*>, String?>())
    }.toMap()
}

fun deftraits(classes: Collection<KClass<*>>): List<String> {
    val keys = classes.map { it.simpleName!! }
    val props = traitImpls(classes).values
    val traits = keys.zip(props).toMap()
    return traits.map { it ->
        val (trait, _values) = it
        val values = _values.map {
            val m = it.key
            it.value?.let { impl(m, Implcontext.impl, { it }) } ?: impl(m, Implcontext.trait)
        }
        """TRAIT($trait) {
    ${values.joinToString("\n    ")}
};
"""
    }
}

fun defvirt(classes: Collection<KClass<*>>): List<String> {
    val traitProps = traitImpls(classes).flatMap { it.value.keys }
    return traitProps.map { impl(it, Implcontext.pure) }
}

/**
 * bind base to trait
 */
fun defglue(classes: Collection<KClass<*>>, virtual: Boolean = true) =
        traitImpls(classes).flatMap {
            val T = it.key
            it.value.toList().map { T to it.first }
        }.map {
            val T = it.first
            val m = it.second
            delegate(m, T, virtual)
        }

private fun delegate(m: KCallable<*>, trait: String, virtual: Boolean): String {
    val params = m.parameters.filter { it.kind == KParameter.Kind.VALUE }
    val ret = m.returnType.conv() + if (m is KProperty<*>) "&" else ""
    val method = m.name
    val args = params.joinToString { "${it.type.conv()} ${it.name!!}" }
    val pass = (if (params.isNotEmpty()) ", " else "") + params.joinToString { it.name!! }
    val prefix = (if (virtual) "virtual " else "")
    return prefix + "$ret $method($args) { return ${trait}_traits<Self>::$method(*static_cast<Self*>(this)$pass); }"
}
