import Dependencies._

ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "org.lamedh"
ThisBuild / organizationName := "Lamedh"

lazy val root = (project in file("."))
  .settings(
    name := "Z2IO",
    libraryDependencies += scalaTest % Test
  )
