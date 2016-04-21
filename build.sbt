

name := "dandelion-scala"

lazy val commonSettings = Seq(
  organization := "io.github.nivox",
  version := "0.1-beta",

  scalaVersion := "2.11.7",
  scalacOptions ++= Seq("-deprecation", "-feature"),

  crossScalaVersions := Seq("2.11.7", "2.10.6"),

  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % "2.4.3",
    "org.scalaz" %% "scalaz-core" % "7.0.6",
    "io.github.nivox" %% "akka-http-argonaut" % "0.1",
    "org.scalatest" %% "scalatest" % "2.2.6" % "test"
  ),

  resolvers ++= Seq(
    Resolver.bintrayRepo("scalaz", "releases"),
    Resolver.bintrayRepo("nivox", "maven")
  )
)

lazy val core = project.settings(commonSettings: _*)

lazy val `datatxt-nex` = project.settings(commonSettings: _*)
  .dependsOn(core)

lazy val `datatxt-sent` = project.settings(commonSettings: _*)
  .dependsOn(core)

lazy val cli = project.settings(commonSettings: _*)
  .settings(
    libraryDependencies += "com.github.scopt" %% "scopt" % "3.4.0",
    packAutoSettings
  ).dependsOn(`datatxt-nex`, `datatxt-sent`)

lazy val root = (project in file(".")).
  aggregate(core, `datatxt-nex`, `datatxt-sent`, cli)
