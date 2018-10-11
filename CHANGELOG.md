# UNRELEASED

This release represents a major version bump: many new features have
been added and large parts of the codebase have been refactored.
Although a lot of effort has gone into keeping API changes minimal,
this release is unfortunately not source or binary compatible with the
1.x versions.

The following is a list of major changes. Please see the git commit
log `git log 1.x..v2.0.0` for details.

## Structural Changes

The core project has been split into many modules, as described in the
README, each having distinct functionality.

In a nutshell, packages in `xyz.driver.core` have been moved to
separate sbt projects. For example, all classes contained in
`xyz.driver.core.messaging` have been moved to the project
`core-messaging`, and `xyz.driver.core.storage` to
`core-storage`. Thus, the modules providing utilities from the latest
1.x version are:

- `core-database`
- `core-rest`
- `core-messaging`
- `core-storage`

Along those functional modules, there are the supporting modules:

- `core-types`, which contains common types, such as `time`, `date`
  and IDs
- `core`, which contains the `app` initialization system and also
  depends on all other modules, acting as an indirection for
  backwards-compatibility.

Furthermore, some new modules have been added which will be explained
later on:

- `core-reporting` (tracing and logging utilities)
- `core-init` (new initialization framework)
- `core-testkit` (core-specific parts of driver-test-utils)

The split was done in order to reduce coupling between components. Our
internal domain-model now only depends on `core-rest` and
`core-messaging`. Services can choose if and what other parts of core
to use, although it is recommended they use the `core-init` module.

Although the split was mostly seemless, some cyclic dependencies
between packages were discovered. Since these are no longer possible
in a multi-project architecture, some source-breaking changes also had
to made. These will be listed later and marked as such.

## New Features

### Reporting Utilities

A suite of tracing and logging utilities, based on the OpenTrace
specification, have been added as the `core-reporting` module. This
module plays a central role because it greatly simplifies debugging
distributed applications, and as such is depended upon by all other
modules.

It embraces the "implicit context" pattern, and requires a
`SpanContext` for all logging actions. This context can be thought of
as a more type-safe "Message Diagnostic Context" and also replaces the
slf4j-based MDC utilities in previous versions of core.

### Typeclass-based service discovery

A type-class based service discovery has been added in
`xyz.driver.rest.DnsDiscovery`. It allows seemless instantiation of
client implementations that provide a "Service Descriptor" type
class. It also does not require any configuration (except for
overrides) and uses the DNS to resolve service hosts.

### Initialization Framework

A trait-based initialization framework has been added in
`core-init`. It implements common application startup procedures and
initializes common utilities for cloud-based web services. It
integrates all other core modules in an idiomatic way.

## API-Breaking Changes

Due to the structural changes mentioned previously, some API-breaking
changes were made:

- `xyz.driver.rest.errors.DatabaseException` has been moved to
  `xyz.driver.core.DatabaseException`, into the project
  `core-types`. This move happened because both the `rest` and
  `database` module require this exception.

- `xyz.driver.core.FutureExtensions` has been moved to
  `xyz.driver.core.rest` as it only contained logic that dealt with
  service exceptions, something that belongs into `core-rest` and must
  not be depended upon by `core-types`.

- all classes but `AuthStub` in `xyz.driver.test` have been moved to
  `xyz.driver.core.testkit` and are available in th
  `core-testkit`. *(`AuthStub` has been moved to
  `xyz.driver.users.testkit` in the project `users-testkit` in the
  users service repo)*

## Deprecation Notices

Several major features have been deprecated and will be removed in the future.

- `xyz.driver.core.app` will stop receiving updates as `xyz.driver.core.init` matures

- `xyz.driver.core.time` and `xyz.driver.core.date` will be replaced
  by their java 8 counterparts

- the initial object-storage utilities in `xyz.driver.core.file` will
  be removed in favor of the ones provided in `core-storage`

- the first version of the message bus api `xyz.driver.core.pubsub`
  will be replaced by `core-messaging`

- `xyz.driver.driver.logging` will be removed as it is made obsolete
  by `core-reporting`
  
Deprecation of other classes in the [`core` support
module](src/main/scala/xyz/driver/core) is yet to be
determined. Please open an issue if you have any comments on them.

# Version 1.x

Please see git commit logs for changes prior to version 2.
