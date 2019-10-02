import scoverage.ScoverageKeys

name := "gfc-concurrent"

organization := "com.gilt"

scalaVersion := "2.12.10"

crossScalaVersions := Seq(scalaVersion.value)

scalacOptions ++= Seq("-target:jvm-1.8", "-unchecked", "-deprecation", "-feature")

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

fork := true

libraryDependencies ++= Seq(
  "com.gilt" %% "gfc-logging" % "0.0.8",
  "com.gilt" %% "gfc-time" % "0.0.7" % Test,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test,
  "org.mockito" % "mockito-core" % "3.1.0" % Test
)

releaseCrossBuild := true

releasePublishArtifactsAction := PgpKeys.publishSigned.value

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

ScoverageKeys.coverageFailOnMinimum := true

ScoverageKeys.coverageMinimum := 85.0

licenses := Seq("Apache-style" -> url("https://raw.githubusercontent.com/gfc-collective/gfc-concurrent/master/LICENSE"))

homepage := Some(url("https://github.com/gfc-collective/gfc-concurrent"))

pomExtra := (
  <scm>
    <url>https://github.com/gfc-collective/gfc-concurrent.git</url>
    <connection>scm:git:git@github.com:gfc-collective/gfc-concurrent.git</connection>
  </scm>
  <developers>
    <developer>
      <id>gheine</id>
      <name>Gregor Heine</name>
      <url>https://github.com/gheine</url>
    </developer>
    <developer>
      <id>ebowman</id>
      <name>Eric Bowman</name>
      <url>https://github.com/ebowman</url>
    </developer>
    <developer>
      <id>andreyk0</id>
      <name>Andrey Kartashov</name>
      <url>https://github.com/andreyk0</url>
    </developer>
  </developers>
)
