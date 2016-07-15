# Driver Core Library

##Running

The database pre-configured is an h2, so you just have to:

1. Launch SBT:

        $ sbt

2. Compile everything and run:

        > run

#Testing

1. Launch SBT:

        $ sbt

2. Compile everything and test:

        > test

## Dependency injection

**Cake pattern** makes not clear which dependencies are missed if dependency lists are long and/or inheritance used to satisfy more that one dependency at once (probably just no tool support).
[Nice overview of different cake aspects and App as a component might be found here](http://scabl.blogspot.com/2013/02/cbdi.html). Popular description is by [Jonas Boner](http://jonasboner.com/real-world-scala-dependency-injection-di/) and formal by [Martin Odersky](http://lampwww.epfl.ch/~odersky/papers/ScalableComponent.pdf).

**Passing dependencies as parameters** requires passing all the dependencies through the dependency graph. Some optimizations and `wired` macro for automatic binding are described [here](http://di-in-scala.github.io).

**Implicits** ambiguates usage of implicits, increasing compilation times, harder to debug.

**Frameworks** introduce magic, limiting, invasive for codebase, need to maintain later versions.

**Inheritance** exposes transitive dependencies implementation details to the higher-level components. This and how self-types work for circular dependencies is explained in [this artice](http://www.andrewrollins.com/2014/08/07/scala-cake-pattern-self-type-annotations-vs-inheritance/) and also [this](http://www.warski.org/blog/2014/02/using-scala-traits-as-modules-or-the-thin-cake-pattern/), however they are using `lazy val`s a lot which may lead to deadlocks at the applications start-up (for circular dependencies).

**Reader monad** requires common `Modules` object with all dependencies, which introduces coupling, preventing easy refactoring (harder to figure out which dependencies are actually used) and makes tests run longer. Harder to differentiate function dependencies from parameters, constructors and self-types are not used, affecting readability. Not allowing scopes with `def`/`val`. [Implementation described here](http://blog.originate.com/blog/2013/10/21/reader-monad-for-dependency-injection/).

__Cake pattern seems the preferable option, due to absence of implicits, inheritance and `lazy val`s, correct handling of circular dependencies, and usage in Scala compiler (hence, hopefuly, no hidden issues).__
