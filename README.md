----

Notice: the master branch is for development work on the **upcoming
2.0 release**, which is still unstable. Any changes to a previous
version of core should be developed against master and then
cherry-picked onto the [1.x
branch](https://github.com/drivergroup/driver-core/tree/1.x).

----

# Driver Core Library for Scala Services [![Build Status](https://travis-ci.com/drivergroup/driver-core.svg?token=S4oyfBY3YoEdLmckujJx&branch=master)](https://travis-ci.com/drivergroup/driver-core)

Multi-cloud utilities and application initialization framework.

This library offers many common utilities for building applications
that run on multiple environments, including Google Cloud, Ali Cloud,
and of course on development machines.


# Overview

*This section applies to the 1.x series of core. The current development master branch may include changes not described here.*

Core library is used to provide ways to implement practices established in [Driver service template](http://github.com/drivergroup/driver-template) (check its [README.md](https://github.com/drivergroup/driver-template/blob/master/README.md)).

## Components

 * `core package` provides `Id` and `Name` implementations (with equal and ordering), utils for ScalaZ `OptionT`, and also `make` and `using` functions and `@@` (tagged) type,
 * `tagging` Utilities for tagging primitive types for extra type safety, as well as some tags that involve extra transformation upon deserializing with spray,
 * `config` Contains method `loadDefaultConfig` with default way of providing config to the application,
 * `domain` Common generic domain objects, e.g., `Email` and `PhoneNumber`,
 * `messages` Localization messages supporting different locales and methods to read from config,
 * `database` Method for database initialization from config, `Id`, `Name`, `Time`, `Date` etc. mapping, schema lifecycle and base classes to implement and test `Repository` (data access objects),
 * `rest` Wrapper over call to external REST API, authorization, context headers, XSS protection, does logging and allows to add Swagger to a service,
 * `auth` Basic entities for authentication and authorization: `User`, `Role` `Permission` `AuthToken`, `AuthCredentials` etc.,
 * `swagger` Contains custom `AbstractModelConverter` to customize Swagger JSON with any Scala  JSON formats created by, for instance, Spray JSON,
 * `json` Json formats for `Id`, `Name`, `Time`, `Revision`, `Email`, `PhoneNumber`, `AuthCredentials` and converters for GADTs, enums and value classes,
 * `file` Interface `FileStorage` and file storage implementations with GCS, S3 and local FS,
 * `app` Base class for Driver service, which initializes swagger, app modules and its routes.
 * `generators` Set of functions to prototype APIs. Combines with `faker` package,
 * `stats` Methods to collect system stats: memory, cpu, gc, file system space usage,
 * `logging` Custom Driver logging layout (not finished yet).

Dependencies of core modules might be found in [Dependencies of the Modules diagram](https://github.com/drivergroup/driver-template/blob/master/Modules%20dependencies.pdf) file in driver-template repository in "Core component dependencies" section.

## Examples

### Functions `make` and `using`
Those functions are especially useful to make procedural legacy Java APIs more functional and make scope of its usage more explicit. Runnable examples of its usage might be found in [`CoreTest`](https://github.com/drivergroup/driver-core/blob/master/src/test/scala/xyz/driver/core/CoreTest.scala), e.g.,

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

### `OptionT` utils
Before

```
OptionT.optionT[Future](service.getRecords(id).map(Option.apply))
OptionT.optionT(service.doSomething(id).map(_ => Option(())))

// Do not want to stop and return `None`, if `produceEffect` returns `None`
for {
  x <- service.doSomething(id)
  _ <- service.produceEffect(id, x).map(_ => ()).orElse(OptionT.some[Future, Unit](())))
} yield x
```

after

```
service.getRecords(id).toOptionT
service.doSomething(id).toUnitOptionT

// Do not want to stop and return `None` if `produceEffect` returns `None`
for {
  x <- service.doSomething(id)
  _ <- service.produceEffect(id, x).continueIgnoringNone
} yield x
```

### `@@` or Tagged types

For type definitions, the only import required is

```scala
import xyz.driver.core.@@
```

which provides just the ability to tag types: `val value: String @@ Important`. Two `String`s with different tags will
be distinguished by the compiler, helping reduce the possibility of mixing values passed into methods with several
arguments of identical types.

To work with tags in actual values, use the following convenience methods:

```scala
import xyz.driver.core.tagging._

val str = "abc".tagged[Important]
```

or go back to plain (say, in case you have an implicit for untagged value)

```scala
// val trimmedExternalId: String @@ Trimmed = "123"

Trials.filter(_.externalId === trimmedExternalId.untag)
```

### `Time` and `TimeProvider`

**NOTE: The contents of this section has been deprecated - use java.time.Clock instead**

Usage examples for `Time` (also check [TimeTest](https://github.com/drivergroup/driver-core/blob/master/src/test/scala/xyz/driver/core/TimeTest.scala) for more examples).

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


For more examples check [project tests](https://github.com/drivergroup/driver-core/tree/master/src/test/scala/xyz/driver/core) or [service template](http://github.com/drivergroup/driver-template) repository.

### App

To start a new application using standard Driver application class, follow this pattern:

    object MyApp extends App {

      new DriverApp(BuildInfo.version,
                    BuildInfo.gitHeadCommit.getOrElse("None"),
                    modules = Seq(myModule1, myModule2),
                    time, log, config,
                    interface = "::0", baseUrl, scheme, port)
                   (servicesActorSystem, servicesExecutionContext).run()
    }

### REST
With REST utils, for instance, you can use the following directives in [akka-http](https://github.com/akka/akka-http) routes, as follows

    sanitizeRequestEntity { // Prevents XSS
      serviceContext { implicit ctx => // Extracts context headers from request
        authorize(CanSeeUser(userId)) { user => // Authorizes and extracts user
          // Your code using `ctx` and `user`
        }
      }
    }

### Swagger
Swagger JSON formats built using reflection can be overriden by using `CustomSwaggerJsonConverter` at the start of your application initialization in the following way:

    ModelConverters.getInstance()
      .addConverter(new CustomSwaggerJsonConverter(Json.mapper(),
         CustomSwaggerFormats.customProperties, CustomSwaggerFormats.customObjectsExamples))

### Locale
Locale messages can be initialized and used in the following way,

    val localeConfig = config.getConfig("locale")
    val log = com.typesafe.scalalogging.Logger(LoggerFactory.getLogger(classOf[MyClass]))

    val messages = xyz.driver.core.messages.Messages.messages(localeConfig, log, Locale.US)

    messages("my.locale.message")
    messages("my.locale.message.with.param", parameter)


### System stats
Stats it gives access to are,

    xyz.driver.core.stats.SystemStats.memoryUsage

    xyz.driver.core.stats.SystemStats.availableProcessors

    xyz.driver.core.stats.SystemStats.garbageCollectorStats

    xyz.driver.core.stats.SystemStats.fileSystemSpace

    xyz.driver.core.stats.SystemStats.operatingSystemStats


## Running

1. Compile everything and test:

        $ sbt test

2. Publish to local repository, to use changes in depending projects:

        $ sbt publishLocal

3. In order to release a new version of core, merge your PR, tag the HEAD master commit with the next version
   (don't forget the "v." prefix) and push tags - Travis will release the artifact automatically!
