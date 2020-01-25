import Dependencies._

ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.lamedh"
ThisBuild / organizationName := "trio"

lazy val root = (project in file("."))
  .settings(
    name := "simple-io",
    libraryDependencies += scalaTest % Test
  )
