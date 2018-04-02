---
title: Fun with Nothing?
layout: post
---
Imagine, if you can, that you need to capture data about your Customers

```kotlin
data class Customer(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val addressLine1: String?,
    val addressLine2: String?,
    val addressLine3: String?,
    val postcode: String?,
    val iso2CountryCode: String,
    val phone: String?
)
```

Due to the power of Kotlin nullability, we can see that we are required to capture a `firstName`, `lastName`, `email` and `iso2CountryCode`, but that everything else is optional.

We dutifully write an HTML form with validation rules to ensure that we have what we need, post it off to some HTTP handler, and set to to write the code to persist our data class to some datastore -

```kotlin
interface Customers {
    fun save(customer: Customer)
    fun findById(id: String): Customer?
    fun gdpr() = TODO("2018-05-25")
}
```

If you're ahead of me here, or just a run of the mill pedant, you'll be saying, "But Duncan, what about that id?"

Ah yes. Identifiers are usually dished out by the datastore, so we don't have one to create a Customer to save. The Java approach would be to allow the id property to be nullable, but if we do that in Kotlin, every time we reference a Customer id we will have to explicitly allow for the null - it's going to get tedious pretty quickly.
An alternative is to have a separate class to model the unsaved customer.

```kotlin
data class UnsavedCustomer(
    val firstName: String,
    val lastName: String,
    val email: String,
    val addressLine1: String?,
    val addressLine2: String?,
    val addressLine3: String?,
    val postcode: String?,
    val iso2CountryCode: String,
    val phone: String?
)

interface Customers {
    fun save(customer: UnsavedCustomer): Customer
    fun findById(id: String): Customer
}
```

That's quite nice, because it shows that act of saving a customer is to turn an UnsavedCustomer into a (Saved)Customer. But the duplication between Customer and UnsavedCustomer is going to be a source of eternal shame.

Given the title of this post, it won't surprise you to learn that generics can rescue us. Let's make the type of the id
generic - constrained to `String?`

```kotlin
data class CustomerData<out T : String?>(
    val id: T,
    val firstName: String,
    val lastName: String,
    val email: String,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val addressLine3: String? = null,
    val postcode: String? = null,
    val iso2CountryCode: String,
    val phone: String? = null
)
```

So our id must be at least a nullable String. A saved Customer has an id, so we can tighten `String?` to `String` in this case.

```kotlin
typealias Customer = CustomerData<String>

val existingCustomer = Customer("123", "Fred", "Flintstone", "fred@bedrock.org", iso2CountryCode = "US")
val thisIsFine: String = existingCustomer.id

val doesntCompile = Customer(null, "Fred", "Flintstone", "fred@bedrock.org", iso2CountryCode = "US")
```

Unsaved customers have a null id. The type of `null` is `Nothing?`, which is helpfully compatible with `String?` but not `String`

```kotlin
typealias UnsavedCustomer = CustomerData<Nothing?>

val newCustomer = UnsavedCustomer(null, "Fred", "Flintstone", "fred@bedrock.org", iso2CountryCode = "US")

val typeMismatch2: String = newCustomer.id
    // Required: String Found: Nothing?

val typeMismatch = UnsavedCustomer("123", "Fred", "Flintstone", "fred@bedrock.org", iso2CountryCode = "US")
    // Required: Nothing? Found: String

```

Because of this you can't use an UnsavedCustomer where you expected a saved one, or vice versa

```kotlin
val typeMismatch3: Customer = newCustomer
val typeMismatch4: UnsavedCustomer = existingCustomer
```

although if you want to treat them alike you can define AnyCustomer

```kotlin
typealias AnyCustomer = CustomerData<*>

val ok: AnyCustomer = newCustomer
val okToo: AnyCustomer = existingCustomer

val AnyCustomer.isSaved get() = id != null
```

To convert an Unsaved to a saved customer, supply the missing id

```kotlin
@Suppress("UNCHECKED_CAST")
fun UnsavedCustomer.saved(id: String) = (this as Customer).copy(id)
```

Full-disclosure - I thought this trick up a while ago, but we've only just put it into some production code, so I don't know how it will turn out in practice. I'll report back if there turn out to be any wrinkles that I haven't spotted.

