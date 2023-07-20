import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import sbt.Keys.libraryDependencies
import sbt._

object Version {
  val akkaMgmtVersion = "1.1.0"
  val akkaHttpVersion = "10.5.2"
  val akkaVersion    = "2.8.3"
  val alpakkaVersion = "2.1.0"
  val postgresVersion = "42.3.4"
  val projectionVersion = "1.2.4"
  val r2dbcPostgresVersion = "0.9.1.RELEASE"
  val r2dbcPoolVersion = "0.9.0.RELEASE"
  val r2dbcSpiVersion = "0.9.1.RELEASE"
  val r2dbcVersion = "0.7.7"
}

object Dependencies {
  val dependencies = Seq(
    "com.typesafe.akka" %% "akka-persistence" % Version.akkaVersion,
    "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % Version.akkaMgmtVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % Version.akkaMgmtVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % Version.akkaMgmtVersion,
    "com.typesafe.akka" %% "akka-cluster" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-stream-kafka" % Version.alpakkaVersion,
    "org.postgresql" % "postgresql" % Version.postgresVersion,

    "com.typesafe.akka" %% "akka-actor-testkit-typed" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-tools" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-coordination" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-discovery" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-distributed-data" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % Version.akkaVersion,

    "com.typesafe.akka" %% "akka-persistence-query" % Version.akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-typed" % Version.akkaVersion,

    "com.lightbend.akka" %% "akka-persistence-r2dbc" % Version.r2dbcVersion,
    "com.lightbend.akka" %% "akka-projection-core" % Version.projectionVersion,
    "com.lightbend.akka" %% "akka-projection-r2dbc" % Version.r2dbcVersion,
    "com.lightbend.akka" %% "akka-projection-eventsourced" % Version.projectionVersion,
    "com.lightbend.akka" %% "akka-projection-kafka" % Version.projectionVersion,
    "com.lightbend.akka" %% "akka-projection-jdbc" % Version.projectionVersion,

    "io.r2dbc" % "r2dbc-pool" % Version.r2dbcPoolVersion,
    "io.r2dbc" % "r2dbc-spi" % Version.r2dbcSpiVersion,
    "org.postgresql" % "r2dbc-postgresql" % Version.r2dbcPostgresVersion

  )

  val kamonSettings = Seq(
    javaAgents += "io.kamon" % "kanela-agent" % "1.0.15",
    /*javaOptions in Universal += "-DKamon.auto-start=true",*/
    libraryDependencies ++= Seq(
      "io.kamon" %% "kamon-bundle" % "2.5.8",
      "io.kamon" %% "kamon-prometheus" % "2.5.8"
    ))

}
