---
title: Kotlin Hacks - .printed()
layout: post
tags: [Kotlin, Kotlin Hacks]
---

Say you have a function

```kotlin
fun sum(x: Int, y: Int) = x + y 
```

and for some reason you suspect that it is going wrong. You'd like to print the return value to the console. So you extract the expression to a variable, print it, and return it.

```kotlin
fun sum(x: Int, y: Int): Int {
    val result =  x + y
    println(result)
    return result
}
```

Hmmm, for a temporary change, that escalated pretty quickly. We could do better with [also](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/also.html), which translates a receiver into a lambda parameter

```kotlin
fun sum(x: Int, y: Int) = (x + y).also { println(it) } 
```

This at least doesn't change the shape of our code and so is easily reversible.

We can do better though by extracting the also invocation into a function - `printed`

```kotlin
fun <T> T.printed(): T = this.also { println(it) }

fun sum(x: Int, y: Int) = (x + y).printed() 
```

If you want some choices about where and how the value is printed then default arguments ride to the rescue

```kotlin
fun <T> T.printed(format: String = "%s\n", stream: PrintStream = System.out): T =
    this.also { stream.format(format, this) }
```

which can be used as previously with no arguments, or, for example

```kotlin
fun sum(x: Int, y: Int) = (x + y).printed("sum is %s\n", System.err) 
```

Use of `printed` isn't restricted to return values

```kotlin
fun sum(x: Int, y: Int) = x.printed() + y.printed() 
```

and this use in the middle of expressions can be really useful to avoid having to tear apart chains of calls

```kotlin
fun foo(x: Int, y: Int) = bar(x).baz(y) 

fun foo(x: Int, y: Int) = bar(x).printed().baz(y) 
```

We can pull a similar stunt for printing collections on multiple lines

```kotlin
fun <T: Iterable<*>> T.printedAsList(format: String = "%s\n", stream: PrintStream = System.out): T =
    this.also { it.map { item -> item.printed(format, stream) } }
```

and even, although this is quite fragile, have overloads for `InputStream`s and `Reader`s

```kotlin
fun InputStream.printed(stream: PrintStream = System.out, charset: Charset = Charsets.UTF_8) =
    bufferedReader(charset).printed(stream)

fun Reader.printed(stream: PrintStream = System.out, charset: Charset = Charsets.UTF_8) =
    readText().printed(stream = stream).byteInputStream(charset)

```

I hope that you find these useful - I keep them in a file called `debugging.kt` and just copy it into every project. If you have any improvements please let me know in the comments below.

