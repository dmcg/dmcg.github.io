---
title: Objects and Functions in Kotlin - Part 1
layout: post
tags: [Kotlin]
---


Imagine, if you can, that you need to send email from some code that you are writing. Just that - not receive mail, or list sent messages - just fire and forget.

As a user of this facility, I'd like to call a function, passing it the information in the email. So given a representation of an email

```kotlin
data class Email(
    val to: String,
    val subject: String,
    val body: String
)
```

then I'd like to invoke a function like

```kotlin
fun send(email: Email) {
    TODO()
}
```

Of course when I come to implement this function, I discover that all sorts of other information is required to *actually* send email. This isn't information about the email itself, but rather configuration about how to send it. Things like the sending email server's IP address, login details, other security credentials, timeouts, senders email address - all the things that most people don't know but you need to set up their new computer. Given the multitude of different email systems, in the end API's like [JavaMail](https://javamail.java.net/nonav/docs/api/) basically punt and pass a whole dictionary, but for now, let's let 3 parameters stand in for the lot.

```kotlin
fun send(
    email: Email,
    serverAddress: InetAddress,
    username: String,
    password: String) {
    TODO()
}
```

As a client things have just gotten a lot less convenient. Everywhere I want to send email has to know about this petty configuration - I'll be passing it around from the top to the bottom of my codebase. I solve that problem by putting it into global variables which works fine until I discover that every run of my unit test suite now ends up sending 5 emails! There must be a better way of hiding these petty details.

You're probably an object-oriented programmer, so you have a ready-made solution to this problem.

```kotlin
    class EmailSender(
        val serverAddress: InetAddress,
        val username: String,
        val password: String
    ) {
        fun send(email: Email) {
            TODO()
        }
    }
```

Now when I want to send email I need access to an EmailSender (which I also have to pass around). Instead of calling a function, we invoke a method. We tell it to send, and we don't have to tell it the petty details it needs because it already knows.


```kotlin
    val sender = EmailSender(inetAddress("smtp.rebelalliance.org"), "username", "password")

    fun sendDistress(sender: EmailSender) {
        sender.send(Email("kenobi@rebelalliance.org", "Help me", "..."))
    }

    fun onUnderAttack() {
        sendDistress(sender)
    }
```

As a bonus, if I extract an interface, I can configure my tests to use a dummy and not actually send emails.

```kotlin
    interface ISendEmail {
        fun send(email: Email): Unit
    }

    class EmailSender(
        val serverAddress: InetAddress,
        val username: String,
        val password: String
    ) : ISendEmail {
        override fun send(email: Email) {
            TODO()
        }
    }
```

Note that this is not implementation hiding. The static function hides the details of how to connect to a server and send email. Here we are just hiding configuration. Granted we can see that the OO approach could lead to `SmtpEmailSender` and `X400EmailSender` implementations, but we aren't at that stage yet.

How would a functional programmer solve the same problem?

Remember that we're trying to get to a function with this signature

```kotlin
    fun sendMessage(email: Email) {
        TODO()
    }
```

given an implementation that looks like this

```kotlin
    fun sendMessage(
        email: Email,
        serverAddress: InetAddress,
        username: String,
        password: String) {
        TODO()
    }
```

This is [partial application](https://en.wikipedia.org/wiki/Partial_application) - fixing a number of arguments to a function to give a function with fewer arguments. While some languages provide built-in support for this sort of thing, in Kotlin the easiest approach is to write a function to partially apply our configuration.

What we want is a function that takes the configuration, and returns a function that knows how to send a message.


```kotlin
    fun createEmailSender(
        serverAddress: InetAddress,
        username: String,
        password: String): (Email) -> Unit {
        TODO()
    }
```

This is the functional analog of the OO case, where the `EmailSender` constructor is a function that 'returns' an object that knows how to send a message.

In longhand, our function can return an inner function that captures the arguments it requires from the parent

```kotlin
    fun createEmailSender(
        serverAddress: InetAddress,
        username: String,
        password: String): (Email) -> Unit {

        fun result(email: Email) {
            send(email, serverAddress, username, password)
        }

        return ::result
    }
```

We can then make the return a lamba expression

```kotlin
    fun createEmailSender(
        serverAddress: InetAddress,
        username: String,
        password: String): (Email) -> Unit {

        val result: (Email) -> Unit = { message -> send(message, serverAddress, username, password) }

        return result
    }
```

before inlining all the stuff to leave this functional definition

```kotlin
    fun createEmailSender(
        serverAddress: InetAddress,
        username: String,
        password: String): (Email) -> Unit =
        { message ->
            send(message, serverAddress, username, password)
        }
```

ie `createMessageSender` is a function that returns a lamba that calls `sendMessage` combining the lambda's single message argument with the configuration from `createMessageSender`. This lambda is also known as a *closure*, as it closes-over the values that it requires from it's enclosing context, capturing them for use later.

To use this function, we can create it in one place and invoke it in another, very much as we did with the object solution.

```kotlin
    val sender: (Email) -> Unit = createEmailSender(inetAddress("smtp.rebelalliance.org"), "username", "password")

    fun sendDistress(sender: (Email) -> Unit) {
        sender(Email("kenobi@rebelalliance.org", "Help me", "..."))
    }

    fun onUnderAttack() {
        sendDistress(sender)
    }
```

That concludes Part I. If you're an OO programmer who hasn't been exposed to functional techniques, I suspect that it has been hard to follow. If you can though, stick with it, as you do get used to the style, and it does allow us to solve problems and express our intent in new and interesting ways.

In [Part 2](objects-and-functions-in-kotlin-part2.html) I'll look at ways in which we can interoperate between the object and functional worlds.


