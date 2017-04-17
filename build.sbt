name := """play-blog"""

version := "0.0.1"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  evolutions,
  jdbc,
  "com.typesafe.play" %% "anorm" % "2.5.3",
  "org.pegdown" % "pegdown" % "1.6.0",
  "org.xerial" % "sqlite-jdbc" % "3.16.1"
)
