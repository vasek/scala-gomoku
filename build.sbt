import sbt.Project.projectToRef

lazy val scalaV = "2.11.6"
lazy val clients = Seq(client)

lazy val server = (project in file("server")).settings(
  scalaVersion := scalaV,
  scalaJSProjects := clients,
  pipelineStages := Seq(scalaJSProd, gzip),
  resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  libraryDependencies ++= Seq(
    "com.vmunier" %% "play-scalajs-scripts" % "0.3.0",
    specs2 % Test
  )
).enablePlugins(PlayScala).
  aggregate(clients.map(projectToRef): _*)

lazy val client = (project in file("client")).settings(
  scalaVersion := scalaV,
  persistLauncher := true,
  persistLauncher in Test := false,
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.8.1",
    "com.lihaoyi" %%% "scalatags" % "0.5.2",
    "be.doeraene" %%% "scalajs-jquery" % "0.8.0"
  )
).enablePlugins(ScalaJSPlugin, ScalaJSPlay)

onLoad in Global := (Command.process("project server", _: State)) compose (onLoad in Global).value
