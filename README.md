# dandelion-scala

Scala library for [Dandelion.eu](http://dandelion.eu) APIs

## Supported APIs

The library provide support for Dandelion's _entity extraction_ (NEX) and _sentiment analysis_ (SENT) APIs.

## Add it to your project

In order to add *dandelion-scala* to your project simply include the following dependency:

```
libraryDependencies ++= Seq(
  "io.github.nivox.dandelion" %% "datatxt-nex" % "0.1",
  "io.github.nivox.dandelion" %% "datatxt-sent" % "0.1"
)
```

In order for sbt to resolve the dependency add the following resolver:

```"Dandelion-Scala Bintray Repo" at "http://dl.bintray.com/nivox/maven"```

or

```Resolver.bintrayRepo("nivox", "maven")```

## Usage example

The minimal setup required in order to use the library is the following:

```scala
implicit val system = ActorSystem("dandelion")
implicit val materializer = ActorMaterializer()
import scala.concurrent.ExecutionContext.Implicits.global

implicit val dandelionApi = DandelionAPI()
val credentialsKeys = DandelionAppKeysAuthCredentials("appId", "appKey")
val credentialsToken = DandelionTokenAuthCredentials("token")
```

The library internally uses Akka for managing the HTTP flow and to provide a streaming API. Hence it is required to initialize an `ActorSystem`.

The `dandelionApi` val initializes the library setting the endpoint to use for _Dandelion_: by default it uses `api.dandelion.eu`:443.

## Single NEX request

To perform a single `NEX` request you can use the following:

```scala
NexAPI.extractEntities(credentials, DandelionSource.Text("The Mona Lisa is a 16th century oil painting created by Leonardo. It's held at the Louvre in Paris."))
```

### Single SENT request

To perform a single `SENT` request you can use the following:

```scala
SentAPI.sentiment(credentials, DandelionSource.Text("Hey cool let's extract some sentiment out of this text!"))
```

### Responses

Each API call returns as a result an object of this kind: `Future[EndpointError \/ EndpointResult[T]]`.

The left branch describes the erorr reported by the _Dandelion_ server.

The right branch instead contains an object describing the response: it gives you access to _units_ information and the actual response.

### Stream support

Both `NEX` and `SENT` request can be performed in a streaming fashion using the `*Stream` variant methods:

```scala
val nexFlow = NexAPI.extractEntitiesStream[K](credentials)
val sentFlow = SentAPI.sentimentStream[K](credentials)
```

The flow expects input of kind `(DandelionSource, K)` and emits output of kind `(Future[EndpointError \/ EndpointResult[T]], K)`. The `K` generic type is provided in order to allow mapping a specific response to an input.
