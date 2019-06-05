---
title: Objects and Functions in Kotlin - Part 2
layout: post
tags: [Kotlin]
---

In [Part 1](objects-and-functions-in-kotlin-part1.html) we started looking at the issue of encapsulating configuration information in object and functional styles. In this second part I'll continue the exploration before looking at expressing differences in behaviour.

Let's first refactor the two previous solutions to look even more similar by adding a static `createEmailSender` function to invoke the OO constructor.



```kotlin
fun send(
    email: Email,
    serverAddress: InetAddress,
    username: String,
    password: String) {
    TODO()
}

object OOContext {

    interface ISendEmail {
        fun send(email: Email): Unit
    }

    class EmailSender(
        val serverAddress: InetAddress,
        val username: String,
        val password: String
    ) : ISendEmail {
        override fun send(email: Email) =
            send(email, serverAddress, username, password)
    }

    fun createEmailSender(
        serverAddress: InetAddress,
        username: String,
        password: String): ISendEmail = EmailSender(serverAddress, username, password)
}

object FPContext {
    fun createEmailSender(
        serverAddress: InetAddress,
        username: String,
        password: String): (Email) -> Unit =
        { message ->
            send(message, serverAddress, username, password)
        }
}
```

The OO solution is quite a bit more wordy than the functional one, but that is because it has defined two new types, whereas the functional version has just reused a function type. Both are implemented as manual [partial application](https://en.wikipedia.org/wiki/Partial_application) of the `send(email, serverAddress, username, password)` function. In use the two methods are virtually identical:

```kotlin
object OOCaller {

    val sender: ISendEmail = OOContext.createEmailSender(inetAddress("smtp.rebelalliance.org"), "username", "password")

    fun sendDistress(sender: ISendEmail) {
        sender.send(Email("kenobi@rebelalliance.org", "Help me", "..."))
    }

    fun onUnderAttack() {
        sendDistress(sender)
    }
}

object FPCaller {

    val sender: (Email) -> Unit = FPContext.createEmailSender(inetAddress("smtp.rebelalliance.org"), "username", "password")

    fun sendDistress(sender: (Email) -> Unit) {
        sender(Email("kenobi@rebelalliance.org", "Help me", "..."))
    }

    fun onUnderAttack() {
        sendDistress(sender)
    }
}
```

The only visible differences are the types of the senders and the fact that we have to call `ooSender.send(...)` where we just invoke `fpSender(...)`. The latter is in fact syntactic sugar for `fpSender.invoke(...)`, so this latter difference really just comes down to the name of the method called.

This points to a mapping between a function and an object that has a single method with the same signature. Or, put another way, between the function type and a single method interface. This similarity is at the heart of Java 8's functional programming facilities. Java doesn't have a syntax for function signatures, so it substitutes interfaces with a single method of the signature that it would like to express.

The same is true for Kotlin, which although it does have a function signature syntax, also defines interfaces that are the equivalent. So in Kotlin, where `Function1<P, R>` is magicked into existence by the compiler, we could write

```kotlin
    fun sendDistress(sender: Function1<Email, Unit>) {
        sender(Email("kenobi@rebelalliance.org", "Help me", "..."))
    }
```

I won't mention this again, as the `(Email) -> Unit` syntax prevails, but every now and again IntelliJ will introduce the Function types as part of a refactoring, so it's best to be prepared.

Can we bring our OO and FP solutions even closer together? Why yes we can. Because the function type is just an interface, `EmailSender` can implement it. How? By defining a method `invoke` with the signature of the function type.

```kotlin
object OOContext2 {

    class EmailSender(
        val serverAddress: InetAddress,
        val username: String,
        val password: String
    ) : ISendEmail, (Email) -> Unit {

        override fun send(email: Email) =
            send(email, serverAddress, username, password)

        override fun invoke(email: Email) = send(email)

    }
}
```

If we do this then we can use our class-based sender in place of our functional one.

```kotlin
object MixedCalling {

    val sender: (Email) -> Unit = OOContext2.EmailSender(inetAddress("smtp.rebelalliance.org"), "username", "password")

    fun onUnderAttack() {
        FPCaller.sendDistress(sender)
    }
}
```

Now our OO solution has become quite a bit more complicated in order to fit in with the FP approach. This calls into question the usefulness of our `ISendEmail` interface. We can see that it is equivalent to the function type `(Email) -> Unit` - all it does is give the name `send` to what happens when you invoke it. Maybe we could just use the type `(Email) -> Unit` everywhere in place of `ISendEmail`?

If you think that might not be expressive enough, then maybe you aren't a functional programmer. Luckily there is a middle ground - we can use a typealias to give a name to the functional type, thus communicating our intent.

```kotlin
typealias ISendEmailToo = (Email) -> Unit

object OOContext3 {

    class EmailSender(
        val serverAddress: InetAddress,
        val username: String,
        val password: String
    ) : ISendEmailToo {

        override fun invoke(email: Email) =
            send(email, serverAddress, username, password)
    }

    val sender: ISendEmailToo = EmailSender(inetAddress("smtp.rebelalliance.org"), "username", "password")

    fun onUnderAttack() {
        FPCaller.sendDistress(sender)
    }
}
```

Note that as the typealias is just an alias - it doesn't define a new type - you can write `ISendEmailToo` and `(Email) -> Unit` interchangeably, so that `FPCalling.sendDistress` doesn't have to be retrofitted with the typealias in order to be called.

There is another way of bridging the OO FP gap that doesn't involve making your classes implement function types - create a function at the point of invocation. Given our old class-based solution:

```kotlin
object OOContext4 {

    class EmailSender(
        val serverAddress: InetAddress,
        val username: String,
        val password: String
    ) {
        fun send(email: Email) =
            send(email, serverAddress, username, password)
    }
}
```

we could create a lambda to pass to our functional code

```kotlin
    val sender = OOContext4.EmailSender(inetAddress("smtp.rebelalliance.org"), "username", "password")

    fun onUnderAttack() {
        FPCaller.sendDistress { sender.send(it) }
    }
```

or just pass a method reference


```kotlin
    fun onUnderAttack() {
        FPCaller.sendDistress(sender::send)
    }
```


Can we do the opposite - pass our functional sender into something that expects an `ISendEmail`? Unless I've missed a trick; that requires more ceremony, at least in Kotlin, as we have to create an anonymous object implementing `ISendEmail` to perform the thunk.

```kotlin
    val sender: (Email) -> Unit = FPContext.createEmailSender(inetAddress("smtp.rebelalliance.org"), "username", "password")

    fun onUnderAttack() {
        OOCaller.sendDistress(object: ISendEmail {
            override fun send(email: Email) = sender(email)
        })
    }
```

Seeing an anonymous implementation of an interface reminds me that we could have implemented an OO solution without introducing a named class.

```kotlin
    fun createEmailSender(
        serverAddress: InetAddress,
        username: String,
        password: String): ISendEmail =
        object : ISendEmail {
            override fun send(email: Email) = send(email, serverAddress, username, password)
        }
```

In the unlikely event that you've made it this far, you may be wondering when to use which method. The fundamental question is, I suppose, should your primary interface for expressing email sending be the `ISendEmail` interface, which helpfully names its single method, or the `ISendEmailToo` typealias or its equivalent `(Email) -> Unit`, which is itself an alias for the generic interface `Function1<Email, Unit>`?

That last statement gives a clue that at the generated bytecode level things are going to be much the same - the compiler is going to be generating implementations of either interface if we don't.

If you're a Kotlin programmer and have to interoperate with Java, then I'd tend to define ISendEmail, as otherwise you'll be exposing Kotlin function types to your callers' Java code.

Otherwise my recent practice has been to define the type of parameters or properties as the function type where I can, providing a typealias where required to fully communicate intent. This allows me to provide implementations as either:

1. Classes that implement the function type
2. Functional partial application
3. Ad-hoc lambdas
4. References to methods with the same signature

I've come to consider the functional types as a kind of duck-typing - because they're built-in to the language you can substitute objects without having to rely on a specific shared type - passing a method reference where a Ruby programmer might pass an object with a known method name.

Of course this only applies where we are talking about interfaces that can be expressed by only one function. Where we want to delegate several operations we could pass multiple functions, but by this stage an actual interface with named methods is probably on the cards. It is surprising though just how often you can express a dependency with a function type, and how expressive and flexible they can make your code.

Stay tuned for Part 3, where I'll look at another way that objects and functions solve the same problem in different ways.




