lazy val akkaHttpVersion = "10.1.11"
lazy val akkaVersion    = "2.6.1"
lazy val hazelcastVersion = "3.12.5"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.example",
      scalaVersion    := "2.13.1"
    )),
    name := "hatoy",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"                % akkaHttpVersion,
//      "com.typesafe.akka" %% "akka-http-spray-json"     % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"              % akkaVersion,
      "com.hazelcast"     %  "hazelcast"                % hazelcastVersion,
      "ch.qos.logback"    % "logback-classic"           % "1.2.3",
      "org.typelevel"     %% "cats-effect" % "2.0.0",
      "io.chrisdavenport" %% "log4cats-slf4j"   % "1.0.1",
      "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test,
      "org.scalatest"     %% "scalatest"                % "3.0.8"         % Test


    ),
    javaOptions ++= Seq("-Dhazelcast.shutdownhook.policy=GRACEFUL", "-Dhazelcast.graceful.shutdown.max.wait=600"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    mainClass in assembly := Some("com.example.MyApp")
  )
