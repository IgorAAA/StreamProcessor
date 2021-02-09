import Dependencies._
import sbt.Keys._

name := "StreamProcessor"

version := "0.0.1"

scalaVersion := "2.13.3"

lazy val main = project
  .in(file("."))
  .settings(
    organization := "IgorAAA",
    description := "Stream Processor",
    name := "Stream Processor",
    normalizedName := "stream-processor",
    libraryDependencies ++= Seq(Fs2.fs2Kafka, catsTime, chimney, pureConfig, epimetheus) ++ Http4s.all ++ Circe.all ++ Logging.all
  )

addCompilerPlugin(("org.typelevel" %% "kind-projector" % "0.11.0").cross(CrossVersion.full))
