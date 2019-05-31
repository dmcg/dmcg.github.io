---
title: Press to Test - Test Driven Development in Android Part 6
layout: post
---

This is Part 6 in a series documenting my experiences learning Android development in Kotlin. The code is available to follow along on [GitHub](https://github.com/dmcg/PressToTest).

In [Part 1](press-to-test-part-1.html) I got a simple UI toy up and running, with tests running via [Espresso](https://developer.android.com/training/testing/espresso) in an emulator.

In [Part 2](press-to-test-part-2.html) I used [Roboletric](http://robolectric.org/) to get (a lightly refactored version of) the same tests running in a local JVM rather than the emulator.

[Part 3](press-to-test-part-3.html) was spent working out how to wait for a condition (the snack bar disappearing) on both the emulator and Robolectric tests.

In [Part 4](press-to-test-part-4.html) and [Part 5](press-to-test-part-5.html) I added unit tests that can run without the ten-second Robolectric tax by introducing something that I *think* is a ViewModel, but doesn't look anything like Google's examples.

In those last two episodes I spent my time avoiding the [Android Architecture Components](https://developer.android.com/topic/libraries/architecture), which include a ViewModel and LiveData and other things that are designed to help in this sort of situation. This was because every time I read the documentation I wanted to give up programming and live on a remote island with only 1950's technology - maybe the Isle of Wight.

The time has now come to bite the bullet, because at some point I have to find some work, and I don't suppose most potential gigs will be hand-rolling their own ViewModels. So today I shall mostly be wearing Architecture Components.

## The Warmup

I start by working my way through the [Data Binding Codelab](https://codelabs.developers.google.com/codelabs/android-databinding). It all kind of makes sense, although for my taste too many important details are buried in XML files, and in code embedded in XML attributes in particular. One thing that I do become aware of is that lifecyle is important - as activities can be restarted we want continuity, but not at the expense of memory leaks.

OK, I can't put it off any longer. 

## Migration

Migration of PressToTest starts in Gradle

```groovy
android {
    ...
    dataBinding {
        enabled true
    }
}
```

and then I need to add a `data` element to `activity_main.xml`. Unfortunately I seem to need to add it into a root element that doesn't exist in my (Android Studio generated) layout file. Ho hum. I add the missing root element and the data element and [this version](https://github.com/dmcg/PressToTest/commit/543d4a7cdec6b1ee73e8c6696579ba26f0aad22d) still builds and runs.

Now, to cut a long story short, there is an unedifying hour of faff when I discover that my project (created only a week ago using Android Studio's new project command) is using APIs in `com.android.support` and the data binding examples are all using APIs from `androidx`. I try to fix Gradle library references by hand and end up in all sorts of trouble trying to find compatible versions of things. I go to bed with a messed up build.
 
With early morning clarity I think to Google for "androidx migration". The results point me to an Android Studio command - `Migrate to AndroidX` - and then warn about all the ways that it will fail to go a good job. I've nothing to loose, so I rollback, [invoke the command](https://github.com/dmcg/PressToTest/commit/7d6b455484914872246d7d88b18664964b1d673d), and everything builds and runs just as before!

\[Edit - looks like I spoke too soon. It did run, but later on the tests failed to build and [had to be fixed](https://github.com/dmcg/PressToTest/commit/5d5302a95132bf3b8f4a3de85850fd1393dfd7df).\]

Making the button label in `ViewModel` a `LiveData` seems like the next logical step. 

```kotlin
class ViewModel(
    private val defaultText: String,
    private val pressedText: String,
    private val onButtonTextChanged: (String) -> Unit,
    private var goBoom: () -> Unit
) {
//    private var buttonText: String by Delegates.observable(defaultText) { _, _, newValue ->
//        onButtonTextChanged(newValue)
//    }

    var buttonText = MutableLiveData<String>(defaultText)
```

Now I find that `MutableLiveData` doesn't have a constructor taking the default value; except it does in the code lab that I've just completed. I sigh, look for the differences between the codelab build and my own and add `implementation "androidx.lifecycle:lifecycle-extensions:2.1.0-alpha04"` which seems to do the trick.

Apart from that, the binding of the button text to a `LiveData` is remarkably painless. Add a data section to the layout and reference our `ViewModel`

```xml
    <data>
        <variable
            name="viewmodel"
            type="com.oneeyedmen.presstotest.ViewModel"/>
    </data>
```

Tell the layout that the button's text should use `viewmodel.buttonText`

```xml
    <Button
            android:text="@{viewmodel.buttonText}"
```
Have the `ViewModel` update the `MutableLiveData`

```kotlin
class ViewModel(
    // ...
) {

    var buttonText = MutableLiveData<String>(defaultText)

    @VisibleForTesting
    internal fun onTouchAction(actionCode:Int) {
        when (actionCode) {
            MotionEvent.ACTION_DOWN -> buttonText.value = pressedText
            MotionEvent.ACTION_UP -> buttonText.value = defaultText
        }
    }
}
```

and use `DataBindingUril.setContentView` rather then `AppCompatActivity.setContentView` to wire up the databinding in `MainActivity`

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main).let {
            it.lifecycleOwner = this
            it.viewmodel = ViewModel(
                button = button,
                defaultText = getString(R.string.default_button_label),
                pressedText = getString(R.string.pressed_button_label),
                goBoom = this::boom
            )
        }
    }

    private fun boom() {
        Snackbar.make(button, getString(R.string.explosion), Snackbar.LENGTH_SHORT)
            .setAction("Action", null).show()
    }
}
```

Awesomely this runs, and works, and passes the `InstrumentedAcceptanceTests`. Less awesomely, Android Studio lets me do all those things without warning me that my unit tests no longer compile. No matter, the test can be simpler now because it can read directly from the `buttonText` `LiveModel` safe in the knowledge that data binding has its back.

```kotlin
class PressToTestTests {
    // ...

    val viewModel = ViewModel(
        defaultText = "Press to Test",
        pressedText = "Release to Detonate",
        goBoom = { boomCount++ }
    )

    private val buttonText get() = viewModel.buttonText.value

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

Run that and - oh no not again `java.lang.RuntimeException: Method getMainLooper in android.os.Looper not mocked. See http://g.co/androidstudio/not-mocked for details.` The top hit on Google provides the answer - add an `InstantTaskExecutorRule`, as had been previously [predicted on Reddit](https://www.reddit.com/r/Kotlin/comments/buqqt1/press_to_test_parts_4_and_5_cover_unit_testing/epg3cw3).

Now I run the last remaining test - the Robolectric `InternalAcceptanceTest`, which fails - apparently Espresso says that the button's label hasn't changed after the touch event. I try adding the `InstantExecutorRule` - no dice. I try waiting for the label change - no dice. I break out the debugger and find that when the 'LiveData' changes it sees its observer as inactive, so does nothing. 

I return to Google, ending up searching for '"livedata" "robolectric" "inactive"', and the top hit of [LiveData seems to not notify observers](https://github.com/robolectric/robolectric/issues/3234) looks promising. It's an unfixed bug in Robolectric, with a [workaround](https://github.com/robolectric/robolectric/issues/3234#issuecomment-319514954) of `activityController.get().getLifecycle().handleLifecycleEvent(Lifecycle.Event.ON_START)`.

Unfortunately there's no clue as to where to put this code, but I figure that before every test would be a good bet, so I add an `@Before` to the `InternalAcceptanceTests`

```kotlin
class InternalAcceptanceTests : AcceptanceTests(robolectricWaiter) {
    @Before fun hack() {
        activityController.get().getLifecycle().handleLifecycleEvent(Lifecycle.Event.ON_START)
    }
}
```

which of course doesn't compile, and I have no idea what the `activityController` is. I find that [handleLifecycleEvent](https://developer.android.com/reference/android/arch/lifecycle/LifecycleRegistry.html#handleLifecycleEvent(android.arch.lifecycle.Lifecycle.Event)) is a method on `LifecyleRegistry`, and `LifeCycleRegistry` is a subtype of `LifeCycle`, so in the end just guess and try

```kotlin
class InternalAcceptanceTests : AcceptanceTests(robolectricWaiter) {
    @Before fun hack() {
        (activityRule.activity.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_START)
    }
}
```

which works, at least for this test! Quick, check it in.

## Events Dear Boy

Now we have used Data Binding to observe `ViewModel.buttonText` and update the, erm, button text, but we still have manual wiring of `ViewModel` to listen to the `Button`'s events.

Now it [looks like](https://stackoverflow.com/a/49785261/97777) I can associate the button's `OnTouchListener` with the `ViewModel` if I revert to exporting a listener property, viz

```kotlin
class ViewModel(
    //...
) {
    val onTouchListener = OnTouchListener { _, event ->
        onTouchAction(event.action)
        false
    }
}
```
and do the binding in the XML

```xml
    <Button
        android:id="@+id/button"
        android:text="@{viewmodel.buttonText}"
        app:onTouchListener="@{viewmodel.onTouchListener}"
        ...
    />
```

That passes the tests, even when I remove the code to add the `OnTouchListener` to the `Button` in the `ViewModel` constructor. So I do the same with the `onClickListener` so that in the end the `ViewModel` secondary constructor disappears and we are left with

```kotlin
class PressToTestTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private var boomCount = 0

    val viewModel = ViewModel(
        defaultText = "Press to Test",
        pressedText = "Release to Detonate",
        goBoom = { boomCount++ }
    )

    private val buttonText get() = viewModel.buttonText.value

    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", buttonText)

        viewModel.onTouchAction(MotionEvent.ACTION_DOWN)
        assertEquals("Release to Detonate", buttonText)

        viewModel.onTouchAction(MotionEvent.ACTION_UP)
        assertEquals("Press to Test", buttonText)
    }

    @Test
    fun `clicking button sets off the explosion`() {
        assertEquals(0, boomCount)

        viewModel.onClick()
        assertEquals(1, boomCount)

        viewModel.onClick()
        assertEquals(2, boomCount)
    }
}
```

```kotlin
class ViewModel(
    private val defaultText: String,
    private val pressedText: String,
    private var goBoom: () -> Unit
) {
    var buttonText = MutableLiveData<String>(defaultText)

    val onTouchListener = OnTouchListener { _, event ->
        onTouchAction(event.action)
        false
    }

    val onClickListener = View.OnClickListener { onClick() }

    @VisibleForTesting
    internal fun onTouchAction(actionCode: Int) {
        when (actionCode) {
            MotionEvent.ACTION_DOWN -> buttonText.value = pressedText
            MotionEvent.ACTION_UP -> buttonText.value = defaultText
        }
    }

    @VisibleForTesting
    internal fun onClick() {
        goBoom()
    }
}
```

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main).let {
            it.lifecycleOwner = this
            it.viewmodel = ViewModel(
                defaultText = getString(R.string.default_button_label),
                pressedText = getString(R.string.pressed_button_label),
                goBoom = this::boom
            )
        }
    }

    private fun boom() {
        Snackbar.make(button, getString(R.string.explosion), Snackbar.LENGTH_SHORT)
            .setAction("Action", null).show()
    }
}
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools">

    <data>
        <variable
            name="viewmodel"
            type="com.oneeyedmen.presstotest.ViewModel"/>
    </data>
    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        tools:context="com.oneeyedmen.presstotest.MainActivity">

        <Button
            android:id="@+id/button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@{viewmodel.buttonText}"
            android:onClickListener="@{viewmodel.onClickListener}"
            app:onTouchListener="@{viewmodel.onTouchListener}"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

    </androidx.constraintlayout.widget.ConstraintLayout>
</layout>
```

## Punt

That just leaves the `goBoom` action not translated to the new newness. Googling for "livedata snackbar" gives a lot of hits about `SingleLiveEvent`, but this isn't part of the standard API and there seem to be competing implementations. I'm reasonably convinced that I don't have to care about lifecycles with the current `goBoom: () -> Unit` implementation, and it has been a tiring day. so I punt and leave the snackbar invocation as it is. Philosophically I think that there is a difference between the databinding used for the label and the boom - the former is just data synchronisation whereas the latter is an effect (both in the audiovisual and functional programming sense) - so I'm happy to leave them looking different.
 
## Wrap Up

Mid way through this post I would have bet that I would be rejecting the data binding approach now and rolling back the code. Actually, when I [compare the two solutions](
https://github.com/dmcg/PressToTest/compare/38517c913eb68f5a4b1c2fc7d266a0cfbf9f5bab..bdf11ff75c12a598cf4c517967b6ec75aa05cd39) I think that the data-binding wins out. The details hidden in the XML aren't too bad and `ViewModel` is simplified by the removal of that ugly constructor. It's a shame that it requires code-generation behind the scenes to work though, and that I had to bodge the Robolectric Espresso test to get it working.

I think that I have one more installment in me. I plan to return to my [happy place](https://docs.oracle.com/javase/7/docs/api/javax/swing/package-summary.html) and reimplement this example to give some perspective on the Android solution.

