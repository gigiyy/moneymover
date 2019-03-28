# Money Mover

coding interview assignment.

## how to run
`sbt` was used to build and run the application.

to test run `sbt test` in project folder. or `sbt run` to start the server.

### test with server
server will be started at `localhost:8080` and you can use `postman` or `curl` to interact with server like below.

#### credit
syntax: `POST credit?user=5&amount=1000&currency=jpy`

result: `Credited jpy 1000.0 successfully to user 5`

#### debit
syntax: `POST debit?user=4&amount=100&currency=jpy`

result: 
* `Account not found for user 4 of jpy` when account not found
* `not sufficient balance for user 4 of jpy` while balance is too low
* `Debited jpy 100.0 successfully from user 4` while no errors

#### transfer
syntax: `POST transfer?from=5&to=7&amount=100&currency=jpy`

result: `Transferred jpy 100.0 from user 5 to user 7`

for demo purpose, only one type of fund transfer was added to the server.
however `AccountService` is able to move funds between accounts with different currencies(exchange)

#### user 
syntax: `GET user?id=1` 

to retrieve user's information from the applications. result is like 
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
account hold by the User, which hold the money own by the user.
it can be dynamically created as needed(received money transfer)

### Record
Account activities
* Debit - money debit from account to external source
* Credit - money credited to account from external source

## implementation details
this application only cases about moving funds between users. 
to make things simpler `Account` is unique by user's `id` and it's `currency`.
and can be created as needed.

operations related to user itself(id, name) should be done by an external `UserService`
and Forex exchange related(rate, currency) etc should be take care of by `FxService`
and they are all out of scope for this application.

below is what `AccountService` does and the use of `akka` `actor` make it easier to implement.

`AccountService` will wait for request from server and initiate a `Worker` child to process each message.

to avoid race conditions where multiple updates to same group of user's accounts happens simultaneously,
`AccountService` will cache the users' ID in `working` set until their processes were done.

pending messages and their sender were cached in `waiting` queue
until `Done` message received. pending message will be retried one by one.

<b>in reality, working set should be backed by shared MemCache so that we can have multiple instance
of `AccountService` or `Server` itself.</b> *