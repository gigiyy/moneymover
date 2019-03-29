# Money Mover
coding interview assignment.

## how to run
This is a `sbt` project.

To test, run `sbt test` in project folder; or `sbt run` to start the server.

### the server
Server will be started at `localhost:8080`, `postman` or `curl` can be used to interact with it.

#### credit
syntax: `POST credit?user=5&amount=1000&currency=jpy`

result: `Credited jpy 1000.0 successfully to user 5`

Result is returned as json, same for below endpoints too.
```json
{
    "message": "Credited jpy 1000.0 successfully to user 5"
}
```

#### debit
syntax: `POST debit?user=4&amount=100&currency=jpy`

result: 
* `Account not found for user 4 of jpy` when account not found
* `not sufficient balance for user 4 of jpy` while balance is too low
* `Debited jpy 100.0 successfully from user 4` while no errors

#### transfer
For demo purpose, only one type of fund transfers was added.
However `AccountService` is able to move funds between accounts with different currencies too (exchange).

syntax: `POST transfer?from=5&to=7&amount=100&currency=jpy`

result: `Transferred jpy 100.0 from user 5 to user 7`

#### user 
Get user information from server, includes accounts list and transctions histories.

syntax: `GET user?id=1` 

result: 
```
{
  "accounts": [{
    "balance": {
      "amount": 100.0,
      "currency": "usd"
    },
    "id": 1,
    "records": [{
      "action": "Deposit",
      "id": 1,
      "money": 1105.0
...
    }]
  }, {
    "id": 2,
...
  }],
  "id": 1
}
```

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
Account hold by the User, which hold the money own by the user.
It can be dynamically created as needed(received money transfer)

### Record
Account activities
* Debit - money debit from account to external source or other account.
* Credit - money credited to account from external source or other account.

## implementation details
This application only cares about moving funds between users. 
to make things simpler `Account` is unique by user's `id` and it's `currency` and can be created as needed.

Operations related to user itself(id, name) should be done by an external `UserService`; Forex related(rate, currency) etc should be taken care of by an `FxService`. Both are out of scope for this simple application.

Below is what `AccountService` does; with `akka` `actor`, it's much easier to implement.

`AccountService` will wait for requests from server and initiate one `Worker` child for each message

To avoid race conditions where multiple updates to same group of user's accounts happens simultaneously, `AccountService` will cache the users' ID in `working` set until their processes were done.

Pending messages and their sender were cached in `waiting` queue
until `Done` message has received. And pending message will be retried one by one in order.

in real world, `working` set should be backed by shared memory cache so that we can have multiple instance of `AccountService` or `Server` itself. However there'll be another concerns related to scaling out.