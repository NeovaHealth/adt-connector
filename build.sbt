name := "t4ADT"

organization := "com.tactix4"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.10.2"

resolvers += "apache public repo" at "http://repository.apache.org/content/groups/ea/"

resolvers += "fusesource repo" at "http://repo.fusesource.com/nexus/content/groups/ea/"

resolvers += "springframework milestone repo" at "http://repo.springsource.org/milestone"

libraryDependencies ++= Seq(
  "org.apache.camel" % "camel-scala" % "2.12.0.redhat-610057",
  "org.apache.camel" % "camel-hl7" % "2.12.0.redhat-610057",
  "org.apache.camel" % "camel-mina2" % "2.12.0.redhat-610057",
  "ca.uhn.hapi" % "hapi-osgi-base" % "2.1",
  "com.tactix4" %% "t4wardware-connector" % "0.0.1-SNAPSHOT",
  "org.scalaz" %% "scalaz-core" % "7.0.3",
  "com.typesafe" % "config" % "1.0.2",
  "joda-time" % "joda-time" % "2.3",
  "org.joda" % "joda-convert" % "1.4",
  "org.springframework.scala" %% "spring-scala" % "1.0.0.RC1"
)

osgiSettings

OsgiKeys.bundleSymbolicName := "Tactix4 ADT"

OsgiKeys.importPackage ++= Seq(
  "ca.uhn.hl7v2.validation.*",
  "ca.uhn.hl7v2.*",
  "org.apache.camel.component.hl7",
  "com.tactix4.t4wardware.connector.*",
  "com.ning.http.client.*",
  "org.springframework.beans.factory.config.*",
  "*"
)


