# Driver Core Library

Core library is used to provide ways to implement practices established in [Driver service template](http://github.com/drivergroup/driver-template) (check its [README.md](https://github.com/drivergroup/driver-template/blob/master/README.md)). Also it provides implementations for interaction with [Driver Infrastructure](https://drive.google.com/a/drivergrp.com/folderview?id=0BxKLZIfIpO8SOUdaRWtINzMxcE0&usp=sharing_eid&ts=578fbc14).

## Components

 * `core package` provides `Id` and `Name` implementations and also `make` and `using` functions,
 * `time` Primitives to deal with time and receive current times in code,
 * `config` Contains method `loadDefaultConfig` with default way of providing config to the application,
 * `messages` Localization messages supporting different locales and methods to read from config,
 * `database` Method for database initialization from config, `Id` and `Name` mapping and schema lifecycle,
 * `rest` Wrapper over call to external REST API, does logging and stats call,
 * `app` Base class for Driver service, which initializes swagger, app modules and its routes.
 * `generators` Set of functions to prototype APIs. Combine with `faker` package,
 * `stats` Interface to the infrastructure statistics service, currently just logs events,
 * `logging` Facade to the infrastructure logging service. Gives ways to report events supported by the infrastructure namely: fatal (for application-level events), error (for user errors), audit (for auditable events that might be needed for compliance), debug (for finding bugs and easier investigations).

Dependencies of core modules might be found in [Dependencies of the Modules diagram](https://github.com/drivergroup/driver-template/blob/master/Modules%20dependencies.pdf) file in driver-template repository in "Core component dependencies" section.

## Examples

### Functions `make` and `using`
Those functions are especially useful to make procedural legacy Java APIs more functional and make scope of its usage more explicit. Runnable examples of its usage might be found in [`CoreTest`](https://github.com/drivergroup/driver-core/blob/master/src/test/scala/com/drivergrp/core/CoreTest.scala), e.g.,

    useObject(make(new ObjectWithProceduralInitialization) { o =>
      o.setSetting1(...) // returns Unit
      o.setSetting2(...) // returns Unit
      o.setSetting3(...) // returns Unit
    })

    // instead of
    val o = new ObjectWithProceduralInitialization
    o.setSetting1(...)
    o.setSetting2(...)
    o.setSetting3(...)

    useObject(o)

and

    using(... open output stream ...) { os =>
      os.write(...)
    }

    // it will be close automatically


### `Time` and `TimeProvider`

Usage examples for `Time` [TimeTest](https://github.com/drivergroup/driver-core/blob/master/src/test/scala/com/drivergrp/core/TimeTest.scala)

    Time(234L).isAfter(Time(123L))

    Time(123L).isBefore(Time(234L))

    Time(123L).advanceBy(4 days)

    Seq(Time(321L), Time(123L), Time(231L)).sorted

    startOfMonth(Time(1468937089834L)) should be (Time(1467381889834L))

    textualDate(Time(1468937089834L)) should be ("July 19, 2016")

    textualTime(Time(1468937089834L)) should be ("Jul 19, 2016 10:04:49 AM")

    TimeRange(Time(321L), Time(432L)).duration should be(111.milliseconds)


### Generators
Example of how to generate a case class instance,

    import com.drivergrp.core._

    Consumer(
         generators.nextId[Consumer],
         Name[Consumer](faker.Name.name),
         faker.Lorem.sentence(word_count = 10))


For more examples check [project tests](https://github.com/drivergroup/driver-core/blob/master/src/test/scala/com/drivergrp/core/) or [service template](http://github.com/drivergroup/driver-template) repository.

## Running

1. Compile everything and test:

        $ sbt test

2. Publish to local repository, to use changes in depending projects:

        $ sbt publish-local

3. TODO: Release new version of core:

        $ sbt release

## Things to do

 * Bring sbt-release plugin to core

 * Logging:

    * Custom akka-http directive to log and record stats for all incoming requests, use akka-http built-in `logRequestResult("log")` as example and probably extend that,

    * Custom akka-http directive to extract tracking/correlation id [linkerd](https://linkerd.io) adds to the requests (perhaps, to header, [perhaps with zipkin](https://linkerd.io/doc/0.7.1/linkerd/tracer/)) and puts to MDC (diagnostic context). Also custom MDC needs to be implemented (or found on github/stackoverflow) to pass context among different threads in asynchronous environment when next compuatations may occur in other thread,

    * Custom log appender with its own format, using diagnostic context with request tracking/correlation id from linkerd,

 * Custom akka-http `ExceptionHandler` for standard reactions to errors. Return tracking/correlation id, either from request, or if it wasn't there, generated from scratch, to corresond log records with erroneous response,

 * Custom akka-http directive to extract authentication credentials from request, and in case no valid token (macaroon) is presented, or it has no permissions (caveats) for this API method, to redirect user to [authentication service](https://docs.google.com/a/drivergrp.com/document/d/19a0BkZIlvYTpc9BKsf3oiAyHDmBQuKwCoTEzk9O9bUo/edit?usp=sharing_eid&ts=578824c7) (or respond with http-error code saying to present user authentication form). Macaroons documentation: [paper](http://static.googleusercontent.com/media/research.google.com/en//pubs/archive/41892.pdf), [hmac](https://en.wikipedia.org/wiki/Hash-based_message_authentication_code), [jmacaroons](https://github.com/nitram509/jmacaroons) (JVM implementation),

 * Wrap routes that modules provide to the `com.drivergrp.core.app.DriverApp` class with authentication, reading linkerd correlation id, logging, stats, and custom error handling,

 * Store possible common typesafe config parts in core library.
