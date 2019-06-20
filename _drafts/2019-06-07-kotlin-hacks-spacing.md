---
title: Kotlin Hacks - .` `
layout: post
tags: [Kotlin, Kotlin Hacks]
---

IntelliJ's code formatting rules mostly just work for Kotlin code - Command-Alt-L and get on with your day.

There are times though that a little manual formatting would go a long way to making code more readable.

```kotlin
val testdata = arrayOf(
    "banana",       "yellow", "curved",
    "dragon-fruit", "red",    "spikey",
    "pear",         "green",  "pear-shaped",
    "kumquat",      "yellow", "round")
```

Command-Alt-L on that and things look a lot less understandable.

```kotlin
val testdata = arrayOf(
    "banana", "yellow", "curved",
    "dragon-fruit", "red", "spikey",
    "pear", "green", "pear-shaped",
    "kumquat", "yellow", "round"
)
```

I hope that you find these useful - I keep them in a file called `spacing.kt` and just copy it into every project. If you have any improvements please let me know in the comments below.

