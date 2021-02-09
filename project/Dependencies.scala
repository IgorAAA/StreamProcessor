import sbt._

object Dependencies {
  object Circe {
    val version = "0.13.0"
    val core    = "io.circe" %% "circe-core" % version
    val generic = "io.circe" %% "circe-generic" % version
    val parser  = "io.circe" %% "circe-parser" % version
    val refined = "io.circe" %% "circe-refined" % version
    val all     = Seq(core, generic, parser, refined)
  }

  object Fs2 {
    private val version = "1.3.1"
    val fs2Kafka        = "com.github.fd4s" %% "fs2-kafka" % version
  }

  object Http4s {
    private val version = "0.21.18"
    val dsl             = "org.http4s" %% "http4s-dsl" % version
    val server          = "org.http4s" %% "http4s-blaze-server" % version
    val all             = Seq(dsl, server)
  }

  object Logging {
    private val slf4jVersion    = "1.7.30"
    private val logbackVersion  = "1.2.3"
    private val log4catsVersion = "1.0.1"

    val sl4j    = "org.slf4j"      % "slf4j-api"        % slf4jVersion
    val log4j   = "org.slf4j"      % "log4j-over-slf4j" % slf4jVersion
    val logback = "ch.qos.logback" % "logback-classic"  % logbackVersion
    val safeLoggingLibs = Seq(
      "io.chrisdavenport" %% "log4cats-core"  % log4catsVersion,
      "io.chrisdavenport" %% "log4cats-slf4j" % log4catsVersion
    )
    val all = Seq(sl4j, log4j, logback) ++ safeLoggingLibs
  }

  val catsTime   = "io.chrisdavenport"     %% "cats-effect-time" % "0.1.2"
  val chimney    = "io.scalaland"          %% "chimney"          % "0.5.0"
  val pureConfig = "com.github.pureconfig" %% "pureconfig"       % "0.12.1"
  val epimetheus = "io.chrisdavenport"     %% "epimetheus"       % "0.4.0"

}
