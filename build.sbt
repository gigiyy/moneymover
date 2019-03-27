name := "moneymover"

version := "0.1"

scalaVersion := "2.12.3"

val akkaHttpVersion = "10.1.8"
val akkaVersion = "2.5.21"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,

  "com.typesafe.slick" %% "slick" % "3.3.0",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.0",
  "org.slf4j" % "slf4j-nop" % "1.6.6",
  //"ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.h2database" % "h2" % "1.4.199",

  "org.scalactic" %% "scalactic" % "3.0.5",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,

  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test
)