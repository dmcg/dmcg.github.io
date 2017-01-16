---
title: Going Generic
layout: post
---

Does every programmer go through many Goldilocks Cycles when it comes to level of abstraction? I know I still do.

We start writing very overly-specific functions to solve just the problem at hand:

``` kotlin
val result = addTwoTo(number)
```

Then we learn to generalise, and now the porridge is too cold:

```kotlin
val calculator = Calculator()
calculator.input(number)
calculator.operation(ADD)
calculator.input(2)
calculator.opertion(EQUALS)
val result = calculator.result()
```

Until it we find just right:

```kotlin
val result = number + 2
```

We may stay there, or be seduced by another abstraction mechanism:

```kotlin
val operation: (Integer) -> Integer = Operations.create(Integer::add, 2)
val result = operation(number)
```

Of course 'just right' is context specific, but there is a general trend in developers to raise the level of abstraction before we have to. This was beaten out of me by XP, and in particular TDD. If you have to write all the tests for your generic calculator class you will be encouraged to focus on the actual task in hand rather than noodling about at a high level.

Abstractions mined from existing uses rather than created up front. The TDD book currency example.

Principle of least surprise -
reuse of abstractions,
using simple types rather than complex ones









