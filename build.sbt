name := "aws-lambda-graalvm-native-scala-example"

version := "0.1"

scalaVersion := "2.13.5"

enablePlugins(GraalVMNativeImagePlugin, DockerPlugin)

libraryDependencies ++=
  List(
    "org.http4s" %% "http4s-jdk-http-client" % "0.3.5",
    "org.http4s" %% "http4s-circe" % "0.21.20",
    "org.typelevel" %% "cats-effect" % "2.3.3",
    "org.slf4j" % "slf4j-simple" % "1.7.30"
  ) ++
    List(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-literal"
    ).map(_ % "0.13.0")

addCompilerPlugin(
  "org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full
)

graalVMNativeImageOptions ++= List(
  "--no-fallback",
  "--no-server",
  "--enable-http",
  "--enable-https",
  "--initialize-at-build-time",
  "--initialize-at-build-time=scala.runtime.Statics$VM",
  "-H:+ReportExceptionStackTraces"
)

dockerBaseImage := "public.ecr.aws/lambda/provided:al2"

licenses := List(
  "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
)

developers := List(
  Developer(
    "azolotko",
    "Alex Zolotko",
    "azolotko@gmail.com",
    url("https://github.com/azolotko")
  )
)
