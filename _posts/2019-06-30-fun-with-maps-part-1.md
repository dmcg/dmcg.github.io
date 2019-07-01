---
title: Fun With Maps Part 1
layout: post
tags: [Kotlin, Fun With Maps]
---

I've been listening to [an](https://clojuredesign.club/) (and [another](https://lispcast.com/category/podcast/)) excellent podcast by Clojure programmers recently and learned that nobody builds classes in Clojure; and certainly not to represent data. Instead they populate maps for all the uses where Kotlin developers would reach for a data class.

I've got very used to data classes and like them a lot, but there are certainly situations in which they don't shine. Today I'm going to look using maps instead.

## The Brief

Let's say that we want to parse a log file into semantic representations of different sorts of errors. Here's one I borrowed from [IBM](https://www.ibm.com/support/knowledgecenter/en/SSLTBW_2.3.0/com.ibm.zos.v2r3.hald001/exmlogfile.htm)

```
03/22 08:51:01 INFO   :.main: *************** RSVP Agent started ***************
03/22 08:51:01 INFO   :...locate_configFile: Specified configuration file: /u/user10/rsvpd1.conf
03/22 08:51:01 INFO   :.main: Using log level 511
03/22 08:51:01 INFO   :..settcpimage: Get TCP images rc - EDC8112I Operation not supported on socket.
03/22 08:51:01 INFO   :..settcpimage: Associate with TCP/IP image name = TCPCS
03/22 08:51:02 INFO   :..reg_process: registering process with the system
03/22 08:51:02 INFO   :..reg_process: attempt OS/390 registration
03/22 08:51:02 INFO   :..reg_process: return from registration rc=0
03/22 08:51:06 TRACE  :...read_physical_netif: Home list entries¢ returned = 7
03/22 08:51:06 INFO   :...read_physical_netif: index #0, interface VLINK1 has address 129.1.1.1, ifidx 0
03/22 08:51:06 INFO   :...read_physical_netif: index #1, interface TR1 has address 9.37.65.139, ifidx 1
03/22 08:51:06 INFO   :...read_physical_netif: index #2, interface LINK11 has address 9.67.100.1, ifidx 2
03/22 08:51:06 INFO   :...read_physical_netif: index #6, interface LOOPBACK has address 127.0.0.1, ifidx 0
03/22 08:51:06 INFO   :....mailslot_create: creating mailslot for timer
03/22 08:51:06 INFO   :...mailbox_register: mailbox allocated for timer
03/22 08:51:06 INFO   :.....mailslot_create: creating mailslot for RSVP
03/22 08:51:06 INFO   :....mailbox_register: mailbox allocated for rsvp
03/22 08:51:06 INFO   :.....mailslot_create: creating mailslot for RSVP via UDP
```

## Data Classes

If we can get past wondering why some developer decided that it was worth outputting the day and month but not the year of each entry, we would probably decide to represent each entry with a data class

```kotlin
data class LogEntry(val timestamp: Instant, val level: String, val message: String)

fun String.toLogEntry() = LogEntry(
    substring(0, 15).toInstantSomehow(),
    substring(16, 23).trim(),
    substring(24).trim()
)

fun showRecentAddresses(file: File) = file.useLines { lines ->
    lines.map { it.toLogEntry() }
        .filter { it.timestamp.isAfter(lastTuesday) }
        .filter { it.message.contains("interface .*? has address".toRegex()) }
        .map { it.message.split(",")[1] }
        .forEach(::println)
}
```

That's fine for a one-off analysis, but no good deed goes unpunished. Soon we're asked to write a suite of analysis software so that the rest of the team can get on with not writing the year into timestamps. We end up with data classes for the different types of log lines that we know how to parse, with an interface for the common data in every line.

```kotlin
interface LogEntry {
    val timestamp: Instant
    val level: String
    val loggerName: String
    val message: String
}

data class BasicEntry(
    override val timestamp: Instant,
    override val level: String,
    override val loggerName: String,
    override val message: String
) : LogEntry

private val lineParser =
    """^(?<timestamp>.{14}) (?<level>.*?) *?:\.+(?<name>.*?): (?<message>.*)$""".toRegex()

fun String.toBasicEntry() = lineParser.find(this)?.groups?.let { groups ->
    BasicEntry(
        timestamp = groups["timestamp"]?.value?.toInstantSomehow()
            ?: error("no timestamp in line $this"),
        level = groups["level"]?.value
            ?: error("no level in line $this"),
        loggerName = groups["name"]?.value
            ?: error("no logger name in line $this"),
        message = groups["message"]?.value
            ?: error("no messagein line $this")
    )
} ?: error("Could not parse as BasicEntry $this")

data class IPEntry(
    override val timestamp: Instant,
    override val level: String,
    override val loggerName: String,
    override val message: String,
    val index: Int,
    val interfaceName: String,
    val address: String
) : LogEntry

fun BasicEntry.toIPEntry(): IPEntry? =
    if (loggerName == "read_physical_netif")
        IPEntry(timestamp, level, loggerName, message, SOMECODE(), SOMECODE(), SOMECODE())
    else
        null

data class RegProcessEntry(
    override val timestamp: Instant,
    override val level: String,
    override val loggerName: String,
    override val message: String
) : LogEntry

fun BasicEntry.toRegProcessEntry(): RegProcessEntry? =
    if (loggerName == "reg_process") SOMECODE() else null

data class MailslotCreateEntry(
    override val timestamp: Instant,
    override val level: String,
    override val loggerName: String,
    override val message: String,
    val createdFor: String,
    val createdVia: String?
) : LogEntry

fun BasicEntry.toMailSlotCreateEntry(): MailslotCreateEntry? =
    if (loggerName == "mailslot_create") SOMECODE() else null

val converters = listOf<(BasicEntry) -> LogEntry?>(
    BasicEntry::toIPEntry,
    BasicEntry::toRegProcessEntry,
    BasicEntry::toMailSlotCreateEntry
    //...
)

fun String.toLogEntry(): LogEntry {
    val rawEntry = this.toBasicEntry()
    return converters.asSequence()
        .mapNotNull { it(rawEntry) }
        .firstOrNull()
        ?: rawEntry
}

fun showRecentAddresses(file: File) = file.useLines { lines ->
    lines.map { it.toLogEntry() }
        .filterIsInstance<IPEntry>()
        .filter { it.timestamp.isAfter(lastTuesday) }
        .map { "${it.interfaceName} : ${it.address}" }
        .forEach(::println)
}

// etc
```

This isn't the only formulation of this data with data classes, and I'm not sure that it's the best, but it is the sort of thing that we end up with in these situations. And it is, well, horrible, largely because you can't inherit from a data classes. We could have made `BasicEntry` the only property of `LogEntry` to remove some duplication, or make `BasicEntry` the envelope containing a typed contents of the parsed payloads, but it's all rather icky because of the inheritance limitation of data classes.

There is another problem with data classes - we don't have (easy) runtime access to their properties. Which is a problem because now the ops people would like us to use our technology to filter lines that contain a given property value. They are happy to look in our source for the names of the properties, but don't want to be recompiling Kotlin every time they want a change, so we need to take the property name and value on the command-line.

```bash
java -jar filter-log.jar file.log interfaceName=VLINK1
```

So we set to and write

```kotlin
fun filterFile(file: File, propertyName: String, propertyValue: String) =
    file.useLines { lines ->
        lines.map { it.toLogEntry() }
            .filter { it.hasProperty(propertyName, propertyValue) }
            .forEach(::println)
    }

```

How do we write `LogEntry.hasProperty` without lots of tables or trying to keep code up to date? Reflection is the answer, but it has just taken me 30 minutes to get the following to compile

```kotlin
@Suppress("UNCHECKED_CAST")
private fun Any.hasProperty(name: String, value: String): Boolean =
    (this::class as KClass<Any>).memberProperties.any {
        it.name == name && it.get(this).toString() == value
    }
```

and even now it does it is relatively slow at runtime. Once we start mixing runtime selection with data classes we find all sorts of things that we have to use reflection to solve - we can't even ask a data class for the number of its properties without reflection ([or a compiler plugin](https://github.com/LunarWatcher/KClassUnpacker/)). Serialising to and from JSON? We have clever libraries that hide it but it's all refection underneath.

Now compared to Java, data classes are a real boon. In Kotlin they should certainly be your default choice for representing data as objects. But even when I offered data classes as a solution to a Clojure developer, he said that he would generally stick with maps. Why is this?

## Maps

Let's have a look at solving the same problem with maps.

First the simple case


```kotlin
fun String.toLogMap(): Map<String, Any?> = mapOf(
    "timestamp" to substring(0, 15).toInstantSomehow(),
    "level" to substring(16, 23).trim(),
    "rawMessage" to substring(24).trim()
)

fun showRecentAddresses(file: File) = file.useLines { lines ->
    lines.map { it.toLogMap() }
        .filter { (it["timestamp"] as Instant).isAfter(lastTuesday) }
        .filter { (it["rawMessage"] as String).contains("interface .*? has address".toRegex()) }
        .map { (it["rawMessage"] as String).split(",")[1] }
        .forEach(::println)
}
```

Hmmm, those casts are a bit ugly. They wouldn't be there in Clojure of course, and we can do better in Kotlin as we've given up type safety anyway

```kotlin
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified R> Map<String, Any?>.get(key: String) = get(key) as R

fun showRecentAddresses(file: File) = file.useLines { lines ->
    lines.map { it.toLogMap() }
        .filter { it.get<Instant>("timestamp").isAfter(lastTuesday) }
        .filter { it.get<String>("rawMessage").contains("interface .*? has address".toRegex()) }
        .map { it.get<String>("rawMessage").split(",")[1] }
        .forEach(::println)
}
```

This is a bit worse than the data class version, why would we give up the type safety that we had? Well the equivalent of the more developed example is quite a bit simpler because we don't have to try to bend data classes to our will.

```kotlin
typealias LogEntryProperties = Map<String, Any?>

val lineParser = """^(?<timestamp>.{14}) (?<level>.*?) *?:\.+(?<name>.*?): (?<message>.*)$""".toRegex()

fun String.extractBasicProperties(): LogEntryProperties =
    lineParser.find(this)?.groups?.let { groups ->
        mapOf(
            "timestamp" to (groups["timestamp"]?.value?.toInstantSomehow() ?: error("no timestamp in line $this")),
            "level" to (groups["level"]?.value ?: error("no level in line $this")),
            "loggerName" to (groups["name"]?.value ?: error("no logger name in line $this")),
            "message" to (groups["message"]?.value ?: error("no messagein line $this"))
        )
    } ?: error("Could not extract properties from $this")


fun LogEntryProperties.withIPProperties(): LogEntryProperties =
    if (get<String>("loggerName") == "read_physical_netif")
        this + mapOf(
            "interface/index" to SOMECODE(),
            "interface/name" to SOMECODE(),
            "interface/address" to SOMECODE()
        )
    else
        this

fun LogEntryProperties.withRegProcessProperties(): LogEntryProperties = SOMECODE()

fun LogEntryProperties.withMailSlotCreateProperties(): LogEntryProperties = SOMECODE()

val extractors = listOf(
    LogEntryProperties::withIPProperties,
    LogEntryProperties::withRegProcessProperties,
    LogEntryProperties::withMailSlotCreateProperties
)

fun String.toLogEntryProperties(): LogEntryProperties =
    extractors.fold(this.extractBasicProperties()) { acc, f -> f(acc) }

fun showRecentAddresses(file: File) = file.useLines { lines ->
    lines.map { it.toLogEntryProperties() }
        .filter { it.containsKey("interface/index") }
        .filter { it.get<Instant>("timestamp").isAfter(lastTuesday) }
        .map { "${it["interface/name"]} : ${it["interface/address"]}" }
        .forEach(::println)
}
```

(Notice that we have added a namespace prefix to the property names - this isn't strictly necessary but helps to avoid collisions in the event that more than one extractor matches a line.)

Because data is just stored in a map we don't have to define data classes to match its shape, but we have lost the type safety that the properties of the data class were giving us, both with respect to their names and types. The data class implementation will almost certainly be faster, because reading a property is a single method call and field access, and more space efficient, because we won't be creating sparse map structures.  In short, data classes should probably be your default representation, even though they can be clumsy.

But what about that additional requirement of filtering by properties on the command line? That's where the maps shine

```kotlin
fun filterFile(file: File, propertyName: String, propertyValue: String) = file.useLines { lines ->
    lines.map { it.toLogEntryProperties() }
        .filter { it[propertyName].toString() == propertyValue } // this is now just a map lookup - no reflection
        .forEach(::println)
}
```

Not having to deal with reflection is really nice here, the map really is first class data, whereas the data class makes you jump through reflective hoops.

So, data classes are good for some things, maps are good for others. Can we meet in the middle?

We can, but you have to decide whether your fundamental representation is going to be data classes, and you just wish that they behaved more like maps sometimes, or you are going to start from maps, and wouldn't it be nice if they could be more type safe? As the maps version is still fresh, let's look at making maps more type safe.

## PropertySet

I have a technique for making maps more type-safe that I haven't tried in anger, but I think has promise. I'm tentatively calling it the Phantom-typed PropertySet.

The problem it (partially) solves is that of passing information about what properties a map contains at compile time. Without this we run into the partial function problem described in [Avoiding Failure](failure-is-not-an-option-part-7.html) - functions that take a `Map<String, Any?>` can fail at runtime because the actual map passed does not have the required keys (or they are the wrong type).

My plan is to parameterise `Map<String, Any?>` with another type that prevents them from all looking alike. Let's call that type `PropertySet`.

```kotlin
interface PropertySet<out T> : Map<String, Any?>
```

We can implement `PropertySet`s by delegating to another map

```kotlin
class MapPropertySet<out T>(private val delegate: Map<String, Any?>)
    : PropertySet<T>,
      Map<String, Any?> by delegate {
    constructor(vararg properties: Pair<String, Any>) : this(mapOf(*properties))
}

fun <T> propertySetOf(vararg properties: Pair<String, Any>): PropertySet<T> =
    MapPropertySet(*properties)
```

and provide a function to combine them like maps

```kotlin
operator fun <T> PropertySet<T>.plus(other: PropertySet<T>): PropertySet<T> =
    MapPropertySet(
        (this.entries + other.entries).map { entry -> entry.key to entry.value }
            .toMap())

```

Now let's look at our log parsing example. A `PropertySet` needs a type, but the type doesn't have to do anything. An empty interface will do fine.

```kotlin
interface Base
```

We can parse the basic log information into a `PropertySet<Base>` just like we did for plain maps

```kotlin
fun String.extractBasicProperties(): PropertySet<Base> =
    lineParser.find(this)?.groups?.let { groups ->
        propertySetOf<Base>(
            "timestamp" to (groups["timestamp"]?.value?.toInstantSomehow()
                ?: error("no timestamp in line $this")),
            "level" to (groups["level"]?.value
                ?: error("no level in line $this")),
            "loggerName" to (groups["name"]?.value
                ?: error("no logger name in line $this")),
            "message" to (groups["message"]?.value
                ?: error("no messagein line $this"))
        )
    } ?: error("Could not extract properties from $this")

```

Now let's say that we need semantic access to the timestamp. We can provide that with an extension property on `PropertySet<Base>`.

```kotlin
val PropertySet<Base>.timeStamp get() = get<Instant>("timestamp")
```

That property allows access to, and knowledge of the type of, timestamp only when we have the right type of `PropertySet`.

```kotlin
val aPropertySet: PropertySet<Base> = SOMECODE()
val timestamp: Instant = aPropertySet.timeStamp

val aDifferentPropertySet: PropertySet<Any> = SOMECODE()
// doesn't compile
// val nogo = aDifferentPropertySet.timeStamp
```

and we still have access to other properties with the unsafe-but-usefully-dynamic map interface

```kotlin
val level = aPropertySet.get<String>("level")

```

Now we can do the same for our other 'types'

```kotlin
interface IP : Base

val PropertySet<Base>.loggerName get() = get<String>("loggerName")

fun PropertySet<Base>.withIPProperties(): PropertySet<Base> =
    if (loggerName == "read_physical_netif")
        this + propertySetOf(
            "interface/index" to SOMECODE(),
            "interface/name" to SOMECODE(),
            "interface/address" to SOMECODE()
        )
    else
        this

val PropertySet<IP>.name get() = get<String>("interface/name")
val PropertySet<IP>.address get() = get<String>("interface/address")

interface RegProcess

fun PropertySet<Base>.withRegProcessProperties(): PropertySet<Base> = SOMECODE()

interface MailSlotCreate

fun PropertySet<Base>.withMailSlotCreateProperties(): PropertySet<Base> = SOMECODE()
```

Parsing is the same as for the maps example

```kotlin
val extractors = listOf(
    PropertySet<Base>::withIPProperties,
    PropertySet<Base>::withRegProcessProperties,
    PropertySet<Base>::withMailSlotCreateProperties
)

fun String.toLogEntryProperties(): PropertySet<Base> =
    extractors.fold(this.extractBasicProperties()) { acc, f -> f(acc) }
```

Now the only thing missing to build `showRecentAddresses` is the downcast from `PropertySet<Base>` to `PropertySet<IP>`. In order to do this we need to know that the map actually does have the properties that the `PropertySet<IP>` extension properties imply. We can get away here with just checking that one of them is there.

```kotlin
@Suppress("UNCHECKED_CAST")
fun PropertySet<Base>.asSetOfIP(): PropertySet<IP>? =
    if (containsKey("interface/index")) this as PropertySet<IP> else null
```

and now `mapNotNull` will conveniently cast and filter

```kotlin
fun showRecentAddresses(file: File) = file.useLines { lines ->
    lines.map { it.toLogEntryProperties() }
        .mapNotNull { it.asSetOfIP() }
        .filter { it.timeStamp.isAfter(lastTuesday) } // it is PropertySet<IP> now
        .map { "${it.name} : ${it.address}" }
        .forEach(::println)
}
```

and because we really are just dealing with maps, our previous `filterFile` continues to work fine.

```kotlin
fun filterFile(file: File, propertyName: String, propertyValue: String) = file.useLines { lines ->
    lines.map { it.toLogEntryProperties() }
        .filter { it[propertyName].toString() == propertyValue }
        .forEach(::println)
}
```

This technique of taking a map and tagging it with a type-erased but reconstructable marker, and synthesising properties based on the marker type, is a nice way to add gradual typing to a map-based data model. What if we want to go the other way round - represent a statically-typed model as a map? We'll look at that in the next episode.

Thanks to [Nat Pryce](http://natpryce.com), [Rob Fletcher](https://twitter.com/_fletchr) and [Robert Stoll](http://tutteli.ch) for providing feedback on this article.

