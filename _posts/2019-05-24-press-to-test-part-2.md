---
title: Press to Test - Test Driven Development in Android Part 2
layout: post
---

This is Part 2 in a series documenting my experiences learning Android development in Kotlin.

In [Part 1](press-to-test-part-1.html) I got a simple UI toy up and running, with tests running via [Espresso](https://developer.android.com/training/testing/espresso) in an emulator (and (I assume but haven't verified) on an actual device).

I left that episode impressed with how easy it was to run functional test code, but concerned that running all my UI tests this way would be very slow for a real project. Let's see if we can run the same sort of tests in a local JVM rather than on an Android device.   

## Just Too Easy

In my Googling around for how to test Android apps [Roboletric](http://robolectric.org/) keeps coming up, and I learn that it is a set of fake implementations of Android APIs to let you run Android code in a JVM. Running my Android code in a JVM is exactly what I want to do, so I search for "espresso robolectric testing" and find articles with reassuring titles like [Write Once, Run Everywhere Tests on Android](https://medium.com/androiddevelopers/write-once-run-everywhere-tests-on-android-88adb2ba20c5). They say that I can just run the Espresso tests as local JVM tests!

It seems that tests in the `androidTest` source root will run on an external device / emulator when the test libraries are referenced with `androidTestImplementation`, and the same code in the `test` source root will run in a local JVM with `testImplementation` libraries (including Robolectric). Oh, and a magic Gradle incantation, viz 

```groovy
android {
    testOptions.unitTests.includeAndroidResources = true
}
```

This seems too good to be true, but I go ahead and add the `testImplementation` declarations to my Gradle build file, giving me

```groovy
dependencies {
    // ...
    
    testImplementation 'junit:junit:4.12'

    // Core library
    testImplementation 'androidx.test:core:1.1.0'
    androidTestImplementation 'androidx.test:core:1.1.0'

    // AndroidJUnitRunner and JUnit Rules
    testImplementation 'androidx.test:runner:1.1.1'
    androidTestImplementation 'androidx.test:runner:1.1.1'
    testImplementation 'androidx.test.ext:junit:1.1.0'
    androidTestImplementation 'androidx.test.ext:junit:1.1.0'
    testImplementation 'androidx.test:rules:1.1.1'
    androidTestImplementation 'androidx.test:rules:1.1.1'

    // Espresso dependencies
    testImplementation 'androidx.test.espresso:espresso-core:3.1.1'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.1.1'

    testImplementation 'org.robolectric:robolectric:4.0'
}
```

By the way, I should say that this project is [available on GitHub](https://github.com/dmcg/PressToTest) if you want to see the full contents of files.

Now I just copy my [ExampleInstrumentedTest](https://github.com/dmcg/PressToTest/blob/1c92240b7a52d83c82102f760a07cb0ce9a82a74/app/src/androidTest/java/com/oneeeyedmen/presstotest/ExampleInstrumentedTest.kt) into `src/test/java` (along with its helper `Finger` object).

It's here that things get a little weird. Android Studio is convinced that there are now two clashing `ExampleInstrumentedTest`s, showing compilation errors in each, whereas Gradle is happy. As Android Studio just delegates to Gradle to run the tests these days, both versions of the tests can be run. When I do, I find that the emulator version (in `androidTest`) continues to run and pass, whereas the JVM version (in `test`) fails one of the two tests.

## Hmmm

In these situations my OCD doesn't know which way to turn - I now have a broken test and a broken IDE (or at least IDE configuration). I decide that the latter is more fundamental - I need to trust that wiggley error lines are real when I see them.

So I rename the `src/androidTest/java/com/oneeyedmen/presstotest/ExampleInstrumentedTest.kt` to `ExternalInstrumentedTest`, only to find that Android Studio renames `src/test/java/com/oneeyedmen/presstotest/ExampleInstrumentedTest.kt` to `externalInstrumentedTest.kt`. Yes, with a lower case `e`. The Android Studio bug tracker is software only its mother could love, so I don't know whether that's a known issue, but I resort to Finder and BBEdit to fix the names and contents the old way and then find that the `Finger` support object clashes even when I make it private in the test files.

I eventually end up with [lots of code duplicated under different names](https://github.com/dmcg/PressToTest/commit/732f14dd749f308e71fb31a3a7b690b67ba585c2), but at least all the squigglies have gone, so that I can relax into working out why the Robolectric test is failing.  

## That Failing Test

The test that is failing is

```kotlin
    @Test
    fun clicking_button_shows_temporary_BOOM_message() {

        onView(snackBarMatcher).check(doesNotExist())

        onView(buttonMatcher).perform(click())
        onView(snackBarMatcher).check(isDisplayed())

        Thread.sleep(3000)
        onView(snackBarMatcher).check(doesNotExist())
    }
``` 

on the line after the `Thread.sleep`, with the message `View is present in the hierarchy`.

Long and bitter experience with UI toolkits has taught me that they often need cycles to properly update their state. To be honest I was surprised that just sleeping was enough to make this test pass on the emulator, so I'm more disappointed than shocked that it doesn't work in Robolectric. Just in case I try a 6 second sleep, but that doesn't help, so it's off to Google again.

That search results are frustrating. It seems that the snackbar wasn't supported under Robolectric for quite a while, so that many of the results are people devising solutions to that problem. I *think* that snackbar is now supported, in as much as the first two assertions pass, and suspect that the support just doesn't extend to the auto-hide behaviour. Stepping through code in the debugger doesn't seem to help one way or the other - the hiding responsibility seems split between a `SnackbarManager` and a `Looper`.

After a couple of hours I'm about to give up when I try a last search for "robolectric looper" and come across a [Stack Overflow Answer](https://stackoverflow.com/questions/34086857/robolectric-run-looper-of-handler-in-my-case) that at least points the way towards

```kotlin
    @Test
    fun clicking_button_shows_temporary_BOOM_message() {

        onView(snackBarMatcher).check(doesNotExist())

        onView(buttonMatcher).perform(click())
        onView(snackBarMatcher).check(isDisplayed())

        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS)
        onView(snackBarMatcher).check(doesNotExist())
    }
```  

Success! In fact it looks like I could use `ShadowLooper.runMainLooperToNextTask()` without relying on a fixed time, but that would then cause internal and external test logic to diverge, which is undesirable. I'm pretty sure that if I understood what [this article](https://medium.com/azimolabs/wait-for-it-idlingresource-and-conditionwatcher-602055f32356) is saying I could find a common solution that does not have a fixed wait time - but I'm now worn out by the stress of having made so little progress for so long, so that's a subject for another post.

## That Duplication

Now I'm left with two structurally identical tests in two different source trees, where they can't share the same code but can't have the same names without confusing Android Studio.  I can't make the `androidTest` source depend on the `test` source, because that would lead to Robolectric being mixed in to our external tests. A little more Googling comes up with this [Stack Overflow Answer](https://stackoverflow.com/a/37882557/97777), and the following changes.

In `build.gradle`

```groovy
    sourceSets {
        String sharedTestDir = 'src/sharedTest/java'
        test {
            java.srcDir sharedTestDir
        }
        androidTest {
            java.srcDir sharedTestDir
        }
    }
```

In `sharedTest`

```kotlin
@RunWith(AndroidJUnit4::class)
abstract class AcceptanceTests {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun button_message_changes_on_pressing() {
        val button = onView(buttonMatcher)

        button.check(isDisplayed(withText("PRESS TO TEST")))

        button.perform(Finger.pressAndHold())
        button.check(isDisplayed(withText("RELEASE TO DETONATE")))

        button.perform(Finger.release())
        button.check(isDisplayed(withText("PRESS TO TEST")))
    }

    @Test
    fun clicking_button_shows_temporary_BOOM_message() {

        onView(snackBarMatcher).check(doesNotExist())

        onView(buttonMatcher).perform(click())
        onView(snackBarMatcher).check(isDisplayed())

        sleep(3000)
        onView(snackBarMatcher).check(doesNotExist())
    }

    protected abstract fun sleep(millis: Long)
}
```

In `test`

```kotlin
class InternalAcceptanceTests : AcceptanceTests() {
    override fun sleep(millis: Long) = ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
}
```

In `androidTest`

```kotlin
class InstrumentedAcceptanceTests : AcceptanceTests() {
    override fun sleep(millis: Long) = Thread.sleep(millis)
}
```

## Wrap Up

Today has been a lot more tiring than yesterday - lots of wallowing without any real idea if I can solve my problems. In the end though the result has been worth it - having the same tests running either in a local JVM or an emulator is a major productivity aid in real projects. The local JVM provides fast feedback when things are broken, while the instrumentation tests give confidence that app is actually in a state to ship.

Next time, I think I'll probably look at true unit testing of our UI, breaking the dependency on Android.