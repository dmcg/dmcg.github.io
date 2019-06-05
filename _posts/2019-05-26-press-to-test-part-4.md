---
title: Press to Test - Test Driven Development in Android Part 4
layout: post
tags: [PressToTest, Testing, Android, Kotlin]
---

This is Part 4 in a series documenting my experiences learning Android development in Kotlin. The code is available to follow along on [GitHub](https://github.com/dmcg/PressToTest).

In [Part 1](press-to-test-part-1.html) I got a simple UI toy up and running, with tests running via [Espresso](https://developer.android.com/training/testing/espresso) in an emulator.

In [Part 2](press-to-test-part-2.html) I used [Roboletric](http://robolectric.org/) to get (a lightly refactored version of) the same tests running in a local JVM rather than the emulator.

[Part 3](press-to-test-part-3.html) was spent working out how to wait for a condition (the snack bar disappearing) on both the emulator and Robolectric tests.

This time we're going to to capitalise on my new-found Android expertise to test-drive the development of the functionality from scratch. Let's remind ourselves what it is that we want to verify.

## The Requirement

The app should show a single button with the label `Press to Test`. When you press, but don't release, the button, its label should change to `Release to Detonate`. Releasing the button should result in some audiovisual extravaganza and reset the button for the next victim.

Unaccountably, a week after first publishing this idea, there is still no app in the Play Store for this.

## Test First

This is test-first, so the first thing to do is to create a test and check that we can run it.

```kotlin
class PressToTestTests {
    @Tests
    fun test() {
        fail()
    }
}
```

Check ✔︎. Or rather ✘, as the test obligingly fails.

Now we have to decide which of our two features - the audiovisual extravaganza when you release the button, or the changing button text - to (test and then) implement first. Let's start with the changing button text, as it comes first chronologically.

Our test wants to look like this

```kotlin
class PressToTestTests {
    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", buttonText)

        touchButton()
        assertEquals("Release to Detonate", buttonText)

        untouchButton()
        assertEquals("Press to Test", buttonText)
    }
}
``` 

where the `buttonText` property, `touchButton` and `untouchButton` are as yet undefined.

Let's try the simplest thing that could possibly work.

```kotlin
class PressToTestTests {
    val button = Button(null)
    val buttonText: String get() = button.text.toString()

    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", buttonText)
    }
}
```

## Not So Fast

Frankly I expected this to fail when I created the button, but instead I get "java.lang.RuntimeException: Method getText in android.widget.TextView not mocked. See http://g.co/androidstudio/not-mocked for details." when I access `button.text`. I like an error message that I can click, even if is it to a page that "is obsolete and not maintained." Anyway the gist is that there is only a stub for `Button` in the android.jar file that we develop against locally, and all its methods throw.

That confuses me for a while, as the test next to it in the source tree ([InternalAcceptanceTests](https://github.com/dmcg/PressToTest/blob/bf9fea38d064f46b255a1eaa4bffd90189b1ef4a/app/src/test/java/com/oneeyedmen/presstotest/InternalAcceptanceTests.kt)) is working with a button, albeit via Robolectric. Let's try a variant of that test as a unit test.

```kotlin
class PressToTestTests {
    
    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)
    
    val buttonText: String get() = activityRule.activity.button.text.toString()
    
    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", buttonText)
    }
}
```

This throws `java.lang.IllegalStateException: No instrumentation registered! Must run under a registering instrumentation` in the `activityRule` constructor. I ponder, and then realise that `InternalAcceptanceTests` are running with a special runner - `@RunWith(AndroidJUnit4::class)`. I add that to my current tests 

```kotlin
@RunWith(AndroidJUnit4::class)
class PressToTestTests {
    ...
}
```

and this now works, which is to say - I can check the button's initial text without any exceptions. Success - except that in place of the exceptions there is a ten second wait while my test starts up - the Robolectric tax.

Now while I only pay that tax once per test run, it's hardly conducive to a rapid TDD cycle. In fact, ironically, if the emulator is already running I can run my two acceptance tests on it, including installation, in about 5 seconds; whereas the 'local' Robolectric tests take at least 10 seconds. As an old Swing developer, used to just firing up my UI in my test VM and throwing events at it, this makes me a bit sad. It looks like if I want fast feedback from my unit tests I'm going to have to avoid real Android views. 

## Decoupling

So it's off to Google again. In some of my previous reading I've come across the MVVM [(Model-View-ViewModel)](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel) pattern, and that seems to promise decoupling of my view logic from actual Android view components. But oh my goodness it's difficult to find a simple example unencumbered by combinations of Architecture Components and Dagger and RxJava and Data Binding and \[Click to Add Favourite Framework\].

While I thrash around the Internet trying to work out what a simple solution would look like using none or more of these parts, I can at least sketch out what a viewmodel might look like. It is supposed to sit between the UI and model (the application state), and translate between them - I guess Model-ViewModel-View was a less snappy title. Anyway, as we have no application state, only UI state, this should be quite simple. I recast the tests like this.

```kotlin
class PressToTestTests {

    val viewModel = ViewModel()

    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", viewModel.buttonText)

        viewModel.onButtonTouched()
        assertEquals("Release to Detonate", viewModel.buttonText)

        viewModel.onButtonUntouched()
        assertEquals("Press to Test", viewModel.buttonText)
    }
}
```

which lets me write a very simple implementation

```kotlin
class ViewModel {

    private val defaultText = "Press to Test"
    private val pressedText = "Release to Detonate"

    var buttonText = defaultText

    fun onButtonTouched() {
        buttonText = pressedText
    }

    fun onButtonUntouched() {
        buttonText = defaultText
    }
}
```

which passes the tests! At least now I have something I can check in to appear to be making progress.

Having checked it in though, it seems that `ViewModel` may not be trying hard enough. We know that the UI does not have separate events for `onButtonTouched` and `onButtonUntouched`. Instead I choose to let `ViewModel` expose an `OnTouchListener` which will take the logic that is currently sitting in [MainActivity.kt](https://github.com/dmcg/PressToTest/blob/4aa0002ce5b6328dbf742494a9ef593ecb93df38/app/src/main/java/com/oneeyedmen/presstotest/MainActivity.kt) so that we can test that.

```kotlin
class ViewModel {

    private val defaultText = "Press to Test"
    private val pressedText = "Release to Detonate"

    var buttonText = defaultText

    val onTouchListener = View.OnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> buttonText = pressedText
            MotionEvent.ACTION_UP -> buttonText = defaultText
        }
        false
    }
}
```

Now the tests just simulate the events that will come from a button

```kotlin
    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", viewModel.buttonText)

        viewModel.onTouchListener.onTouch(null, motionEventWithAction(MotionEvent.ACTION_DOWN))
        assertEquals("Release to Detonate", viewModel.buttonText)

        viewModel.onTouchListener.onTouch(null, motionEventWithAction(MotionEvent.ACTION_UP))
        assertEquals("Press to Test", viewModel.buttonText)
    }

    private fun motionEventWithAction(action: Int) = MotionEvent.obtain(0, 0, action, 0.0F, 0.0F, 0)
```

except that, sigh, `java.lang.RuntimeException: Method obtain in android.view.MotionEvent not mocked. See http://g.co/androidstudio/not-mocked for details.` I've been here before and I know that I can solve this problem with Robolectric, but Robolectric is the problem that I'm trying to solve.

I can't be the first person to have this problem, so back to Google. I'm definitely not the first person to have this problem! But both cures (Robolectric or PowerMock) are worse than the disease.  The problem is that `MotionEvent` is a final class, and evidently intimately intertwingled with the native workings of Android. This is what happens when frameworks are not created hand-in-hand with testing, and is a real shame. This one problem must be wasting hours of developer time and certainly seems to be forcing Google to develop over-complicated workarounds. 

But I need to stop moaning and make some progress - it's especially hard to justify this testing wheelspin when the app is already working and already has two other sets of tests to prove it! So I move the problem by adding a method `onTouchAction` which I can test, and leaving `onTouchListener` untested for now. When everything is wired up it will be tested by our acceptance tests in any case.

```kotlin
class ViewModel {

    private val defaultText = "Press to Test"
    private val pressedText = "Release to Detonate"

    var buttonText = defaultText

    fun onTouchAction(actionCode:Int) {
        when (actionCode) {
            MotionEvent.ACTION_DOWN -> buttonText = pressedText
            MotionEvent.ACTION_UP -> buttonText = defaultText
        }
    }

    val onTouchListener = View.OnTouchListener { v, event ->
        onTouchAction(event.actionMasked)
        false
    }
}
```

so now the tests just say

```kotlin
    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", viewModel.buttonText)

        viewModel.onTouchAction(MotionEvent.ACTION_DOWN)
        assertEquals("Release to Detonate", viewModel.buttonText)

        viewModel.onTouchAction(MotionEvent.ACTION_UP)
        assertEquals("Press to Test", viewModel.buttonText)
    }
```

and I can wire in my view model (at least for this interaction)

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewModel = ViewModel()

        button.setOnTouchListener(viewModel.onTouchListener)

        button.setOnClickListener { view ->
            Snackbar.make(view, getString(R.string.explosion), Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show()
        }
    }
}
```

## But I Digress

You know, I've been faffing around for so long now that I actually ran the acceptance tests expecting them to pass. And when they didn't, I actually ran the app and played with it, thinking that I must have broken the tests rather than that my cunning plan didn't work. This one of the under-appreciated problems with development - when 'easy' things aren't we still feel the need to make progress, and that need expresses itself in blind optimism, which then results in bugs.

Finally the evidence of my own clicking and eyes helps me realise that there is no linkage between our `ViewModel` and the button text. My previous Googling suggests that there is some data binding library that will help out, but for now I'm going to just hack it in manually.

## More Noncoupling

What we want is to make changes to `ViewModel.buttonText` change the text of a button, without having the `ViewModel` know about the `Button` type, because we can't create a `Button` in our unit tests without booting up Robolectric. If `Button` implemented an interface we could use that, but it doesn't because, well it just doesn't. The simplest way to implement this decoupling in Kotlin is just to use a function type to hide the actual code being run. So I add a function property to `ViewModel`s constructor

```kotlin
class ViewModel(
    private val onButtonTextChanged: (String) -> Unit
) {
```
   
and arrange for its implementation to set the text on a button

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewModel = ViewModel(onButtonTextChanged = button::setText)
        button.setOnTouchListener(viewModel.onTouchListener)

        button.setOnClickListener { view ->
            Snackbar.make(view, getString(R.string.explosion), Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show()
        }
    }
}
```

Now the tests can work in terms of the last set text

```kotlin
class PressToTestTests {

    var buttonText = "DEFAULT"
    val viewModel = ViewModel { buttonText = it }

    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", buttonText)

        viewModel.onTouchAction(MotionEvent.ACTION_DOWN)
        assertEquals("Release to Detonate", buttonText)

        viewModel.onTouchAction(MotionEvent.ACTION_UP)
        assertEquals("Press to Test", buttonText)
    }
}
```

and I can implement functionality in `ViewModel` with an `observable`

```kotlin
class ViewModel(
    private val onButtonTextChanged: (String) -> Unit
) {

    private val defaultText = "Press to Test"
    private val pressedText = "Release to Detonate"

    var buttonText: String by Delegates.observable(defaultText) { _, _, newValue ->
        onButtonTextChanged(newValue)
    }

    init {
        // required to sync the view on creation
        buttonText = defaultText
    }

    fun onTouchAction(actionCode:Int) {
        when (actionCode) {
            MotionEvent.ACTION_DOWN -> buttonText = pressedText
            MotionEvent.ACTION_UP -> buttonText = defaultText
        }
    }

    val onTouchListener = View.OnTouchListener { v, event ->
        onTouchAction(event.actionMasked)
        false
    }
}
```

## Wrap Up

Finally (7 hours including research and typing this article) I have a true unit test of the first interaction, and the emulator and Robolectric versions of the acceptance tests still pass. I don't know if what I have counts as MVVM or not, perhaps only MVM, or maybe it's just a mongrel that will become more refined as I learn more about the official support for testing. At least I now know some of the constraints on Android TDD and may appreciate why some things are as they are.

Looking back at the code I see a hidden advantage with the `ViewModel` - it is now a single point of truth about the default button text, which was previously duplicated between `activity_main.xml` and `MainActivity.kt`. Generally though things have gotten a bunch more complicated in the name of testability. I'm trusting that in the [next episode](press-to-test-part-5.html) we will be able to remove some code as I learn more about Android UI patterns.

