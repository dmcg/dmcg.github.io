
/*-
## Nothing? can save us

Let's make the type of the id generic - constrained to `String?`
-*/

//`
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
//`

/*-
So our id must be compatible with nullable String.

A saved Customer has an id, so we can tighten `String?` to `String` in this case.
-*/

//`
typealias Customer = CustomerData<String>

val existingCustomer = Customer("123", "Fred", "Flintstone", "fred@bedrock.org", iso2CountryCode = "US")
//`

/*-
Now a customer's id must be non-null, which makes our life simple
-*/

//`
val thisIsFine: String = existingCustomer.id

val doesntCompile = Customer(null, "Fred", "Flintstone", "fred@bedrock.org", iso2CountryCode = "US")
//`

/*-
We want our unsaved customers have a null id. The type of `null` is `Nothing?`, which is helpfully substitutable for `String?`
-*/

//`
typealias UnsavedCustomer = CustomerData<Nothing?>

val newCustomer = UnsavedCustomer(null, "Fred", "Flintstone", "fred@bedrock.org", iso2CountryCode = "US")
//`

/*-
You can't use a String where you expect a Nothing? though, so null is the only acceptable value of an UnsavedCustomer id
-*/

//`
val typeMismatch = UnsavedCustomer("123", "Fred", "Flintstone", "fred@bedrock.org", iso2CountryCode = "US")
    // Required: Nothing? Found: String

val typeMismatch2: String = newCustomer.id
    // Required: String Found: Nothing?
//`

/*-
Because of this you can't use an UnsavedCustomer where you expected a saved one, or vice versa
-*/

//`
val typeMismatch3: Customer = newCustomer
val typeMismatch4: UnsavedCustomer = existingCustomer
//`

/*-
although if you want to treat them alike you can define AnyCustomer
-*/

//`
typealias AnyCustomer = CustomerData<*>

val ok: AnyCustomer = newCustomer
val okToo: AnyCustomer = existingCustomer

val AnyCustomer.isSaved get() = id != null
//`

/*-
To convert an Unsaved to a saved customer, supply the missing id
-*/

//`
@Suppress("UNCHECKED_CAST")
fun UnsavedCustomer.saved(id: String) = (this as Customer).copy(id)
//`

/*-
Full-disclosure - I thought this trick up a while ago, but we've only just put it into some production code, so I don't know how it will turn out in practice. I'll report back if there turn out to be any wrinkles that I haven't spotted.
-*/

