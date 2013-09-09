name := "t4ADT"

organization := "com.tactix4"

version := "1.0.0"

scalaVersion := "2.9.1"

resolvers += "apache public repo" at "http://repository.apache.org/content/groups/public/"

resolvers += "fusesource repo" at "http://repo.fusesource.com/nexus/content/groups/public/"

libraryDependencies ++= Seq(
  "org.apache.camel" % "camel-scala" % "2.10.0.redhat-60024",
  "org.apache.camel" % "camel-hl7" % "2.10.0.redhat-60024",
  "org.apache.camel" % "camel-mina" % "2.10.0.redhat-60024",
  "ca.uhn.hapi" % "hapi-osgi-base" % "1.2",
  "com.tactix4" % "OpenERPConnector" % "1.0.0-SNAPSHOT"
)

osgiSettings

OsgiKeys.importPackage ++= Seq(
  "org.apache.camel",
  "org.apache.camel.scala.dsl",
  "org.apache.camel.scala.dsl.builder",
  "org.apache.camel.model.dataformat",
  "org.apache.camel.model",
  "ca.uhn.hl7v2.validation.*",
  "ca.uhn.hl7v2.*",
  "scala",
  "org.apache.camel.scala",
  "scala.reflect.*",
  "scala.runtime",
  "scala.collection.*",
  "org.apache.camel.component.hl7",
  "*"
)

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))