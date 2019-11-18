---
title: Failure is not an Option - Functional Error Handling in Kotlin. Part 4 - Either v Exception
layout: post
tags: [Kotlin, Failure is not an Option]
---
This is Part 4 in a series looking at functional error handling in Kotlin. The parts are

* [Part 1 - Exceptions](failure-is-not-an-option-part-1.html)
* [Part 2 - Either](failure-is-not-an-option-part-2.html)
* [Part 3 - Result and Fold](failure-is-not-an-option-part-3.html)
* [Part 4 - Either v Exception](failure-is-not-an-option-part-4.html)
* [Part 5 - Embracing Either](failure-is-not-an-option-part-5.html)
* [Part 6 - What Should You Do While Waiting for the Standard Result Type](failure-is-not-an-option-part-6.html)
* [Part 7 - Avoiding Failure](failure-is-not-an-option-part-7.html)

In this fourth episode I’ll compare the pros and cons of using an Either type compared to Exceptions. This is hard-won information - there are plenty of functional programmers who will tell you the advantages of Either - I haven't yet found one who will tell you the disadvantages.

That said, from here on in you should know that I'm less sure of myself. There may be solutions to problems that I haven't been exposed to, or better ways of achieving the same ends. Part of my motivation for writing this series is to get things straight in my head and to start a discussion, so please let me know when I'm wrong. The last post started an interesting [discussion on Reddit](https://www.reddit.com/r/Kotlin/comments/8dmc5i/functional_error_handling_in_kotlin_part_3_result/). [Discuss this post](https://www.reddit.com/r/Kotlin/comments/8ej18c/functional_error_handling_in_kotlin_part_4_either/)

So, *I may be wrong*, but I believe that even in Haskell, our goto source for FP patterns, Either does not replace exceptions. They can still be raised by those conditions that Java would signal with Errors or unchecked Exceptions - out of memory, integer divide by 0, out of bounds array access etc. So, referring back to [Part 1](failure-is-not-an-option-part-1.html), we should consider using Either only for those cases where checked exceptions would be appropriate, and returning null to signal a failure isn't good enough.

One easy-to-overlook feature of checked exceptions is that they obey laws of type-substitutability. So, as FileNotFoundException extends IOException, given

```java
class FileInputStream {
    public FileInputStream(String name) throws FileNotFoundException {...}
    public int read() throws IOException {...}
}
```

I can invoke methods and pass on the common exception superclass

```java
public int readFirstByte(String filename) throw IOException {
    return FileInputStream(name).read(); // TODO - close stream
}
```

We *can* do the same thing with Either.

```kotlin
fun openFile(name: String): Either<FileNotFoundException, InputStream> = TODO()

fun InputStream.eitherRead(): Either<IOException, Int> = TODO()

fun readFirstByte(filename: String): Either<IOException, Int> =
    openFile(filename).flatMap { it.use { it.eitherRead() } }
```

So the types work, now we just have to implement the functions.

```kotlin
@Suppress("UNCHECKED_CAST")
fun openFile(name: String): Either<FileNotFoundException, InputStream> =
    resultOf {
        FileInputStream(name)
    } as Either<FileNotFoundException, InputStream>

@Suppress("UNCHECKED_CAST")
fun InputStream.eitherRead(): Either<IOException, Int> =
    resultOf {
        this.read()
    } as Either<IOException, Int>
```

Hmmm, those casts look mighty suspicious. If, say, `this.read()` ended up throwing an IndexOutOfBoundsException, then the runtime has no way of knowing that the type of the result is wrong, which will potentially probably lead to really nasty debugging sessions. And while the signature of `this.read()` is unlikely to change, if we were calling a more volatile API, it could add a type of thrown exception and our code would continue to compile but be subtly broken.

I suppose I could change resultOf to check the type of exception seen is as expected

```kotlin
inline fun <reified L, R> fastidiousResultOf(f: () -> R): Either<L, R> =
    try {
        Right(f())
    } catch (e: Exception) {
        if (e is L)
            Left(e)
        else
            throw e // or throw IllegalStateException(e)?
    }
```

but that seems asking for trouble unless everyone really internalises its behaviour. It all seems really complicated compared to those nice checked exceptions that everyone complained about, but maybe I was just used to making those work for me.

Even if we dig ourselves deeper into our Exception type hole, Either can't express another common checked exceptions idiom - a list of exceptions.

```java
public int doSomething(String aParam) throws MyApiException, IOException { ... }
```

In practice therefore, when using Either the lowest actual subtype of the exception that may be returned does not seem to be specified in function signatures; Exception seems the norm -

```kotlin
fun openFile(name: String): Either<Exception, InputStream> =
    resultOf {
        FileInputStream(name)
    }
```

which is a shame, because despite all this functional cleverness we seem to be back to the de-facto situation of  - here is a function that can either succeed, or fail with pretty much any Throwable.

Actually it isn't quite that bleak. We've divided our error handling into

* Errors, which we probably can't do anything about at any level, and are allowed to propagate as Throwables, and
* Exceptions, that are returned in the Either, albeit with their actual type usually lost.

Furthermore, reviewing my current code-base's use of Either for error handling, I find that we only return Either for functions that we reasonably *expect* to fail for one of a few reasons

1. The function is only defined for a subset of its inputs (partial functions like parseInt).
2. External systems don't behave as we expect (usually expressed as IOException).
3. Some other condition not related to our inputs is is not met (eg timeouts).
4. The function calls another function that we expect to fail.

Our convention is that if a public function can reasonably expect to fail, it should return an Either, usually with Exception as the Left type. In practice then we have the equivalent of a single checked exception type (we don't necessarily know what can go wrong, but we know that *something* can) that plays nicely with higher-order functions such as List.map. If calling code needs to do different things with different failures then it is going to have to check the runtime type of Left, and if you want to know what types you need to check for you're going to have to examine the whole expression tree, but this is what usually happens in practice even with checked exceptions - we handle a few predictable special cases and then see what else occurs in production.

One situation that this convention doesn't help with is functions that don't return any result - things like `Writer.writeln(s: String)`. In Java this throws IOException, which is checked, so you are forced to consider the failure. We could return Either<IOException, Unit> in these cases, but as the caller is not processing a return value, it is easy to ignore the error as well. Real functional programmers don't have to worry about these cases, as functions called just for their side-effects will only be invoked in the context of special error handling (the IO Monad). This hasn't actually come up in our real-world codebase so far so I don't know what to advise - maybe throwing the exception is the least worst thing here.

I think it may be time to the recap where we are with the whole exceptions v error results.

1. Early APIs returned error codes or set global error flags, but it was easy to forget to check these.
2. Exceptions were introduced to force explicit error handling, but it was still hard to know if a function could fail in practice.
3. Checked exceptions were introduced to make expected failure conditions more explicit.
4. Checked exceptions prove hard to reconcile with higher-order functions, go back 2 spaces.
5. Either types give many of the advantages of unchecked exceptions and knowing that a function can fail in practice, but only for functions where the caller is relying on using the result.

So, you're now asking, should I be using exceptions or Either in my code base?

Well you won't be fired for using exceptions. Just claim that you haven't read these posts, point out that there is not a standard result type in the standard library, and continue business as usual. You can document, log and debug your way out of the problems that will occur in the same way as you always have. If you'd like to raise your game though, then I'd definitely experiment with the functional style. In my last two Kotlin projects we have used a specialised Result variant of Either, similar to the one presented here, as our default strategy, and I'd say that it has been easier to work with than the alternative exception free-for-all.

If you're a library writer and want to return Either from your functions, the lack of a functional error type in the standard library is going to cause you a problem. My advice would be to define a minimal Result type in your own package and return that - your clients can always write their own extension functions to convert to other representations.

I think that I'm done for this episode. I'm aware that this series is a little disjointed and that the parts are individually too short. It's a consequence I'm afraid of my working out what I think by writing, and needing to publish to get some feedback to inform future posts ([Discuss this post](https://www.reddit.com/r/Kotlin/comments/8ej18c/functional_error_handling_in_kotlin_part_4_either/)). Maybe I'll be able to pull it all together into a single coherent article in the future. In the meantime, I think that there are two more posts to come - a review of existing Kotlin result types, and a post on making functional error handling work for you.

