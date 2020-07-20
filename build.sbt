lazy val akkaHttpVersion   = "10.1.11"
lazy val akkaVersion       = "2.6.1"
lazy val igniteVersion     = "2.8.1"
lazy val enumeratumVersion = "1.6.1"

lazy val root = (project in file(".")).settings(
  inThisBuild(List(
    organization := "com.example",
    scalaVersion := "2.13.3",
  )),
  name := "hatoy",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http"         % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream"       % akkaVersion,
    "org.apache.ignite"  % "ignite-core"       % igniteVersion,
    "ch.qos.logback"     % "logback-classic"   % "1.2.3",
    "org.typelevel"     %% "cats-effect"       % "2.1.3",
    "com.beachape"      %% "enumeratum"        % enumeratumVersion,
    "io.chrisdavenport" %% "log4cats-slf4j"    % "1.0.1",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
    "org.scalatest"     %% "scalatest"         % "3.0.8"         % Test,
  ),
//  javaOptions ++= Seq("-Dhazelcast.shutdownhook.policy=GRACEFUL", "-Dhazelcast.graceful.shutdown.max.wait=600"),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  mainClass in assembly := Some("com.example.imperative.Main"),
)
