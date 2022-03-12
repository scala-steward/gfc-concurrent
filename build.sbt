import scoverage.ScoverageKeys

name := "gfc-concurrent"

organization := "org.gfccollective"

scalaVersion := "2.13.8"

crossScalaVersions := Seq(scalaVersion.value, "2.12.15")

scalacOptions ++= Seq("-target:jvm-1.8", "-unchecked", "-deprecation", "-feature")

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

fork := true

libraryDependencies ++= Seq(
  "org.gfccollective" %% "gfc-logging" % "1.0.0",
  "org.gfccollective" %% "gfc-time" % "1.0.0" % Test,
  "org.scalatest" %% "scalatest" % "3.2.11" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test,
  "org.mockito" % "mockito-core" % "4.4.0" % Test,
)

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

Test / publishArtifact := false

pomIncludeRepository := { _ => false }

ScoverageKeys.coverageFailOnMinimum := true

ScoverageKeys.coverageMinimum := 84.0

licenses := Seq("Apache-style" -> url("https://raw.githubusercontent.com/gfc-collective/gfc-concurrent/main/LICENSE"))

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
