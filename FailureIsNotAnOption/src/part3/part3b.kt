package part3a

import kotlin.test.assertEquals

//`
sealed class Either<out L, out R>

data class Left<out L>(val l: L) : Either<L, Nothing>()

data class Right<out R>(val r: R) : Either<Nothing, R>()

inline fun <L, R1, R2> Either<L, R1>.map(f: (R1) -> R2): Either<L, R2> =
    when (this) {
        is Right -> Right(f(this.r))
        is Left -> this
    }

inline fun <L, R1, R2> Either<L, R1>.flatMap(f: (R1) -> Either<L, R2>): Either<L, R2> =
    when (this) {
        is Right -> f(this.r)
        is Left -> this
    }

inline fun <L, R, T> Either<L, R>.fold(fl: (L) -> T, fr: (R) -> T): T =
    when (this) {
        is Right -> fr(this.r)
        is Left -> fl(this.l)
    }

inline fun <R> resultOf(f: () -> R): Either<Exception, R> =
    try {
        Right(f())
    } catch (e: Exception) {
        Left(e)
    }
//`

/*-
There's an awful lot of usefulness packed into a few lines of code there - a testament to Kotlin. The nice thing about using data classes and extension methods is that I don't have to provide everything that you could want to do with Either as a method - you can add your own.

If you're beginning to experiment with functional error handling, I'd start with the definitions up above. If you find yourself doing something the same way more than twice, look to see if you add an extension method that expresses that operation. For example, I have
-*/

//`
inline fun <L, R, T> Either<L, R>.assertSuccess(f: (R) -> T) =
    when (this) {
        is Right -> f(this.r)
        is Left -> error("Value was not a Right, but a Left of ${this.l}")
    }
//`

/*-
which I use in test code in one of two ways
-*/

fun someOperation(): Either<Exception, String> = TODO()
fun dummy() {
//`
val result: Either<Exception, String> = someOperation()

result.assertSuccess {
    assertEquals("banana", it)
}

val rightValue: String = result.assertSuccess { it }
//`
}

/*-
Once you've internalised some of the idioms, I'd then introduce a specific Result type, it can be it's own sealed class or a typealias for Either. Or you can import someone else's library and experiment with it.

This series isn't done yet. As you'll find out if you do go down this path, there are plenty of pain points still to be addressed!
-*/

