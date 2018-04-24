---
title: Failure is not an Option - Functional Error Handling in Kotlin. Part 1 - Exceptions
layout: post
---
This is Part 1 in a series looking at functional error handling in Kotlin. The parts are 

* [Part 1 - Exceptions](failure-is-not-an-option-part-1.html)
* [Part 2 - Either](failure-is-not-an-option-part-2.html)
* [Part 3 - Result and Fold](failure-is-not-an-option-part-3.html)
* [Part 4 - Either v Exception](failure-is-not-an-option-part-4.html)

In this first post I'll look at why Kotlin did not follow Java's example and distinguish between checked and unchecked exceptions.

Java has a simple and effective way of signalling and recovering from errors - exceptions. When the language was designed these were in fashion, for good reason - they are a simple and effective way of signalling and recovering from errors.

Where Java went out on a limb was to introduce checked exceptions. Most languages treat exception types equally - code either succeeds and returns or fails and throws. Java distinguished between those conditions that you had to handle (checked exceptions) and those that you didn’t (all other exceptions, errors and throwables). Personally I like the scheme, but many didn’t, and for good reasons. 

Checked exceptions lead to a situation where the exceptions from lower level code would routinely be caught in order to rethrow them as another type to conform to the caller’s interface. Most other languages never bothered with this malarkey - unless intervening code was going to release resources, log or retry, exceptions generally propagated directly from the thrower to some top level generic handler that said, in essence, “Something went wrong somewhere.” In practice that was what most Java exception handling boiled down to as well, except that the report could now say that the you had a RuntimeException caused by a MyAPIException caused by a TheirAPIException caused by an ICustomerWcfServiceGetCustomerDetailBusinessFaultDetailFaultFaultMessage caused by an IOException. Sigh. Maybe the detractors had a point. 

One thing that isn’t in doubt is that the need to declare checked exceptions in method signatures made Java more verbose. More functional languages than Java have notation to describe a function type, so it is easy to see why the Kotlin language designers balked at `(URI, Int) -> Status throws IOException, YourAPIException` and just decided to do away with the distinction between checked and unchecked exceptions. In practice when it came to its own functional abstractions - lambdas and streams - Java did the same thing. The functional interfaces invoked by stream methods don’t declare a checked exception; so you have to deal with any such issues inside your lambdas, even if all you do is convert to a runtime exception. This is for a good reason - if a lambda throws a checked exception, then anything that invokes the lambda can throw that exception, but now `Stream.map` needs to be able to declare the throwing of the lambda's exception somehow. (I think that this would have been possible in Java, at least for one exception per lambda, but it would led to more complication than the already rococo generics could bear.)  

The de-facto position in Kotlin and (some of) Java 8 is therefore that errors are signified by raising an exception, and that code does not declare what exceptions it may raise. This leaves any code liable to failure in any way at any time, which in practice means that we revert to the situation in other languages with exceptions.

So were checked exceptions just a bad idea? Well no. We’ve lost important information when we discard the concept - the way we expect code might fail, and how defensive we need to be about that. In practice it is usually far more likely that IO will fail than that our process will run out of memory - hence IOException is checked and OutOfMemoryError is not. When a Java method says `throws FileNotFoundException` we can reason that if the user picked that file from a list it is probably still there and the exception is unlikely; if they typed-in a file name then the chances of it being right are about 10% and we’d better sit in a loop until it is. Either way we’re forced to think about the possibility and consider what to do locally, rather than abdicating responsibility to a generic handler after aborting the whole operation.

In the [next part of this series](failure-is-not-an-option-part-2.html) I’ll look at why functional languages tend to avoid exceptions, and what they use instead.

