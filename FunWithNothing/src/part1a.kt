/*-
---
title: Fun with Nothing?
layout: post
---
-*/
import A.Customer

object A {

/*-
Imagine, if you can, that you need to capture data about your Customers
-*/

    //`
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
    //`

/*-
Due to the power of Kotlin nullability, we can see that we are required to capture a `firstName`, `lastName`, `email` and `iso2CountryCode`, but that everything else is optional.

We dutifully write an HTML form with validation rules to ensure that we have what we need, post it off to some HTTP handler, and set to to write the code to persist our data class to some datastore -
-*/

    //`
    interface Customers {
        fun save(customer: Customer)
        fun findById(id: String): Customer?
        fun gdpr() = TODO("2018-05-25")
    }
    //`
}

/*-
If you're ahead of me here, or just a run of the mill pedant, you'll be saying, "But Duncan, what about that id?"

Ah yes. Identifiers are usually dished out by the datastore, so we don't have one to create a Customer to save. The Java approach would be to allow the id property to be nullable, but if we do that in Kotlin, every time we reference a Customer id we will have to explicitly allow for the null - it's going to get tedious pretty quickly.
-*/

object B {
/*-
An alternative is to have a separate class to model the unsaved customer.
-*/

    //`
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
    //`

/*-
That's quite nice, because it shows that act of saving a customer is to turn an UnsavedCustomer into a (Saved)Customer. But the duplication between Customer and UnsavedCustomer is going to be a source of eternal shame.
-*/
}