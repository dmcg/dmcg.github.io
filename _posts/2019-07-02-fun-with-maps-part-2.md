---
title: Fun With Maps Part 2
layout: post
tags: [Kotlin, Fun With Maps]
---

## Recap

In the [previous episode](fun-with-maps-part-1.html), we looked at parsing a log file. We initially used data classes to represent the different types of events that we saw, but found that they could be clumsy when representing type hierarchies, and we had to fall back on reflection in order to select properties specified by strings.

In contrast using maps to represent the data was (in this instance) less awkward, and they made it easy to select properties by name. Whilst I at least missed the reassurance that properties would be present and a given type that strong typing gave us, introducing a phantom type and extension properties allowed me to regain some of that confidence in a gradual way. I called the technique Phantom-typed PropertySets, but I'm hoping that someone else has given the technique a better name.

So we have a way to alleviate some of the loose-typing problems of maps. What can we do to make data classes more convenient? In particular to allow us access to properties in a dynamic way like we can with maps?

## Accessing properties with reflection

We saw that we can use reflection access a the value of a Kotlin property by name. In short

```kotlin
@Suppress("UNCHECKED_CAST")
private fun Any.propertyValue(name: String): Any? =
    (this::class.memberProperties as Collection<KProperty1<Any, Any?>>).find {
        it.name == name
    }?.get(this)
```

That nasty cast is a result of the `out` variance of `KClass` - the less said about it the better. Let's see it working.

```kotlin
data class Person(val firstName: String, val lastName: String)

class PropertyValueTests() {

    val person = Person("Fred", "Flintstone")

    @Test fun tests() {
        assertEquals("Fred", person.propertyValue("firstName"))
        assertNull(person.propertyValue("dob"))
    }
}
```

Compared to Java reflection, that's pretty convenient already. Could we do better? Could we just treat our data object *as* a map?

We would be on the right lines if we can get the following to work.

```kotlin
class ObjectToMapTests() {

    val person = Person("Fred", "Flintstone")
    val map = person.asPropertyMap()

    @Test fun tests() {
        assertEquals("Fred", map["firstName"])
        assertNull(map["dob"])

        assertEquals(2, map.size)
        assertEquals(setOf("firstName", "lastName"), map.keys)
        assertEquals(listOf("Fred", "Flintstone"), map.values)

        // etc
    }
}
```

## SpaceBlanket

If you fancy a challenge, go ahead and write your own implementation of `asPropertyMap`. It's an interesting task, and you may well make different trade-offs than I did.

This was what I came up with.

```kotlin
fun Any.asPropertyMap(): Map<String, Any?> = SpaceBlanket(this)

internal class SpaceBlanket(private val thing: Any) : Map<String, Any?> {

    override val entries: Set<Map.Entry<String, Any?>>
        get() = keys.map { AbstractMap.SimpleImmutableEntry(it, get(it)) }.toSet()

    override val keys by lazy { properties.filter { it.visibility == KVisibility.PUBLIC }.map { it.name }.toSet() }

    override val size: Int get() = keys.size

    override val values: Collection<Any?>
        get() = keys.map { this[it] }

    override fun containsKey(key: String) = keys.contains(key)

    override fun containsValue(value: Any?) = values.contains(value)

    override fun get(key: String): Any? = properties.firstOrNull { it.name == key }?.get(thing)

    override fun isEmpty() = size == 0

    override fun equals(other: Any?) = when (other) {
        is SpaceBlanket -> this.entries == other.entries
        is Map<*, *> -> other == this
        else -> false
    }

    // copied from AbstractMap
    override fun hashCode(): Int {
        var h = 0
        val i = entries.iterator()
        while (i.hasNext())
            h += i.next().hashCode()
        return h
    }

    override fun toString() = HashMap(this).toString()

    @Suppress("UNCHECKED_CAST")
    private val properties by lazy {
        thing::class.memberProperties as Collection<KProperty1<Any, Any?>> // nasty cast due to out variance of thing::class
    }
}
```

I've published this on [GitHub](https://github.com/dmcg/spaceblanket) as a proof of concept. There are lots more features you could reasonably want, in particular recursively yielding maps for all non-primitive properties, but it is still pretty handy. We can go back to the `filterFile` example from Part 1 and write

```kotlin
fun filterFile(file: File, propertyName: String, propertyValue: String) = file.useLines { lines ->
    lines.map { it.toLogEntry() }
        .filter { it.asPropertyMap()[propertyName].toString() == propertyValue }
        .forEach(::println)
}
```

or to dump all the properties of a Kotlin object with their values

```kotlin
fun Any.dumped() = println(
    this
        .asPropertyMap()
        .entries
        .joinToString(",") { "${it.key} = ${it.value}" }
)
```

Or check a subset of the properties of an object in one go

```kotlin
fun check(entry: IPEntry) {
    val expected = mapOf("index" to "index #0", "interfaceName" to "VLINK1")
    val relevantProperties: Map<String, Any?> = entry
        .asPropertyMap()
        .entries
        .filter { it.key in expected.keys }
        .map { it.key to it.value }
        .toMap()
    assertEquals(expected, relevantProperties)
}
```

Converting to a map isn't the only way to solve these sorts of problems, but the chances are that you *can* work out how to solve them with a map, so it may well be a good start.

## Conclusion

Data classes are a really nice feature of Kotlin, and should be your default choice for representing data, but they have limitations.

If you have unknown data, a heavily subtyped data model, or need dynamic access to properties, maps may be a better choice. You can bring some gradual typing to your maps using Phantom-typed PropertySets.

If you choose data classes and find that you occasionally need map-like access, SpaceBlanket or something like it can retrofit a map interface to standard Kotlin objects.

Thanks to [Nat Pryce](http://natpryce.com), [Rob Fletcher](https://twitter.com/_fletchr) and [Robert Stoll](http://tutteli.ch) for providing feedback on this article.

