# Akka Wamp
It is a WAMP - [Web Application Messaging Protocol](http://wamp-proto.org/) implementation written in [Scala](http://scala-lang.org/) with [Akka](http://akka.io/)

It actually provides the following features:

|                       | Broker | Publisher | Subscriber | Dealer | Callee | Caller |
|:---------------------:|:------:|:---------:|:----------:|:------:|:------:|:------:|
|         Basic Profile |    X   |     X     |      X     |        |        |        |
|      Advanced Profile |        |           |            |        |        |        |
|    JSON Serialization |    X   |     X     |      X     |        |        |        |
| MsgPack Serialization |        |           |            |        |        |        |
|   WebSocket Transport |    X   |     X     |      X     |        |        |        |
|     Raw TCP Transport |        |           |            |        |        |        |


## Client APIs
Akka Wamp provides you with three alternative APIs in writing clients:

 * [Future based](client/future/)
 * Actor based
 * Stream based

Here it is a code snippet with what you could do with Akka Wamp:
 
```scala
for (session <- Client().connectAndHello("ws://host:8080/ws"))
  yield session.subscribe("myapp.topic") { event =>
    event.payload.map { p =>
      system.log.info(payload.arguments.toString)
    }
  }
```
 
## Router
Akka Wamp provides you with a router that can be either embedded into your application or launched as standalone server process.

## Limitations

 * It works with Scala 2.11 (no older Scala)
 * WebSocket transport without SSL/TLS encryption (no raw TCP yet)  
 * Router works as _broker_ (no _dealer_ yet).
 * Client works as _publisher_/_subscriber_ (no _callee_/_caller_ yet).
 * Provide WAMP Basic Profile (no Advanced Profile yet)
 * Provide JSON serialization (no MsgPack yet)