import Dependencies._
import sbt.Keys._

name := "StreamProcessor"

version := "0.0.1"

scalaVersion := "2.13.2"

lazy val main = project
  .in(file("."))
  .settings(
    organization := "IgorAAA",
    description := "Stream Processor",
    name := "Stream Processor",
    normalizedName := "stream-processor",
    libraryDependencies ++= Seq(Fs2.fs2Kafka, chimney) ++ Http4s.all
  )
