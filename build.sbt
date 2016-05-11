name := "dandelion-scala"

val _scalaVersion = "2.11.7"

scalaVersion := _scalaVersion

lazy val commonSettings = Seq(
  organization := "io.github.nivox.dandelion",
  version := "0.1",
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),

  scalaVersion := _scalaVersion,
  scalacOptions ++= Seq("-deprecation", "-feature"),

  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.4.4",
    "com.typesafe.akka" %% "akka-http-experimental" % "2.4.4",
    "org.scalaz" %% "scalaz-core" % "7.1.1",
    "io.argonaut" %% "argonaut" % "6.1",
    "io.github.nivox" %% "akka-http-argonaut" % "0.2",
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
  .settings (publish := { }, publishLocal := { })
