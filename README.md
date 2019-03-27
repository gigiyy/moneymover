# Money Mover

coding interview assignment.

## requirements

* RESTful API for money transfers between accounts
* used by multiple systems or services
* keep it simple and without heavy frameworks
* in-memory data store for simplicity sake
* stand alone executable with tests

## data model

### AccountService
Account management service fulfill the money transfer functions

### Account
account hold by the User, which hold the money own by the user.
it can be dynamically created as needed(received money transfer)

### Record
Account activities
* Debit - money debit from account to external source
* Credit - money credited to account from external source

