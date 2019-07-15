# puree
A Scala compiler plugin to warn about unused effects

[![CircleCI](https://circleci.com/gh/tkroman/puree.svg?style=svg)](https://circleci.com/gh/tkroman/puree)

# sbt setup

```scala
lazy val pureeV = "0.0.9"
libraryDependencies ++= Seq(
  compilerPlugin("com.tkroman" %% "puree" % pureeV),
  "com.tkroman" %% "puree-api" % pureeV % Provided
)

// very desirable
scalacOptions ++= Seq(
  "-Ywarn-unused:implicits",
  "-Ywarn-unused:imports",
  "-Ywarn-unused:locals",
  "-Ywarn-unused:params",
  "-Ywarn-unused:patvars",
  "-Ywarn-unused:privates",
  "-Werror",
  "-Ywarn-value-discard",
)
```

# Why

## Effects

In essence, we say that effect is everything that is not a simple value, so

```scala
val intIsNotAnEffect = 1
val dateIsNotAnEffect = LocalDate.now()
val stringIsNotAnEffect = "no, I'm not"

val optionIsAnEffect = Some(5)
val listIsAnEffect = List(1, 2, 3)
val taskIsAnEffect = Task(println("yes, I am"))
val ioIsAnEffect = IO("me too")
val programsAreEffectsOfSorts: IO[Unit] = completeAppInIO
// I don't want to mention Future, but...
```

In a pure FP setting, most effects are pure,
i.e. declaring or referring to and effect does not mean
any sort of computation being started.

Hence the idea that if somewhere in your code there is this:

```scala
def read: Task[String] = Task(in.read())
def write(str: String) = Task(out.write(str))

val prompt: Task[Int = {
    write("Enter a number")
    val number = read()
    number.flatMap(n => Task.fromTry(Try(n.toInt)))
    // use that int
}
```

you will be surprised by an absence of the prompt string,
which will happen because the `write(...)` expression
doesn't actually launch the printing routine.

More than that, in most of the cases a presence
of an unused effectful value alone means it's likely a bug, a typo
or an omission of sorts. Think of a trivial example:

```scala
someCode()
List(1,2,3) // What? Why?
somethingElse()
```

Normally, `scalac` will warn you if you use a pure expression in a useless context, e.g

```scala
val x = 5
1 // warning here
val y = 10
```

But it fails to see more complicated examples:

```scala
def f = 1
val x = 1
f // no warning
val y = 2
```

Scala can't help you out here because believing that all functions are pure
is not practical in general.
However, if you tru writing your code in a more or less principled way,
most of the time this will be true for almost all effectful values and functions.
Think of it as  "If I return an `F[_, _*]`", I probably wanted to use it.

This plugin is provided specifically as a way to help you with that.

# Notes
Works best if you also enable the following scalac flags:

```
-Ywarn-unused:implicits
-Ywarn-unused:imports
-Ywarn-unused:locals
-Ywarn-unused:params
-Ywarn-unused:patvars
-Ywarn-unused:privates
-Xfatal-warnings
-Ywarn-value-discard
```

This plugin does not make an attempt to be too smart, the rules are pretty simple:
if there is an `F[_, _*]`, it's not assigned to anythings,
is not composed with anythings, and is not a last expression in the block,
it's considered to be worthy a warning. Making warnings into errors via `Xfatal-warnings`
takes care of the rest.
We also don't try taking over other warings, so there are no additional rules.


A more comprehensive set of scalac flags one should almost always enable
if they want to maximize compiler's help can be found here:
https://tpolecat.github.io/2017/04/25/scalac-flags.html

# License
MIT
