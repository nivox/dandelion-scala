name := "dandelion-scala"

val _scalaVersion = "2.11.7"

scalaVersion := _scalaVersion

homepage := Some(url("https://github.com/nivox/dandelion-scala"))

lazy val commonSettings = Seq(
  organization := "io.github.nivox.dandelion",
  version := "0.1.2",
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

lazy val `datatxt-core` = project.settings(commonSettings: _*)
  .settings(
    description := "Common types and logic for interacting with Dandelion's DataTXT APIs"
  )

lazy val `datatxt-nex` = project.settings(commonSettings: _*)
  .dependsOn(`datatxt-core`)
  .settings(
    description := "Interface to Dandelion's DataTXT-NEX API"
  )

lazy val `datatxt-sent` = project.settings(commonSettings: _*)
  .dependsOn(`datatxt-core`)
  .settings(
    description := "Interface to Dandelion's DataTXT-SENT API"
  )

lazy val `datatxt-cli` = project.settings(commonSettings: _*)
  .settings(
    description := "Command line interface to Dandelion's DataTXT APIs",
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "3.4.0",
      "com.typesafe.akka" %% "akka-slf4j" % "2.4.4"
    ),
    packAutoSettings
  ).dependsOn(`datatxt-nex`, `datatxt-sent`)

lazy val root = (project in file(".")).
  aggregate(`datatxt-core`, `datatxt-nex`, `datatxt-sent`, `datatxt-cli`)
  .settings (publish := { }, publishLocal := { })
