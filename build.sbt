import Dependencies._

ThisBuild / scalaVersion     := "2.13.14"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "org.lamedh"
ThisBuild / organizationName := "Lamedh"

lazy val root     = (project in file("modules")).aggregate(z2io, examples)

lazy val examples = (project in file("modules/examples")).dependsOn(z2io)

lazy val z2io = (project in file("modules/z2io"))
  .settings(libraryDependencies += scalaTest % Test)
