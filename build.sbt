name := "dandelion-scala"

lazy val commonSettings = Seq(
  organization := "io.github.nivox",
  version := "0.1-beta",

  scalaVersion := "2.11.7",
  scalacOptions ++= Seq("-deprecation", "-feature"),

  crossScalaVersions := Seq("2.11.7", "2.10.6"),

  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % "2.0.2",
    "org.scalaz" %% "scalaz-core" % "7.0.6",
    "io.github.nivox" %% "akka-http-argonaut" % "0.1"
  ),

  resolvers ++= Seq(
    Resolver.bintrayRepo("scalaz", "releases"),
    Resolver.bintrayRepo("nivox", "maven")
  )
)

lazy val core = project.settings(commonSettings: _*)

lazy val root = (project in file(".")).
  aggregate(core)
