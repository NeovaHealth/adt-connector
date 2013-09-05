name := "t4ADT"

organization := "com.tactix4"

version := "1.0.0"

scalaVersion := "2.9.1"

resolvers += "apache public repo" at "http://repository.apache.org/content/groups/public/"

resolvers += "fusesource repo" at "http://repo.fusesource.com/nexus/content/groups/public/"

libraryDependencies ++= Seq(
  "org.apache.camel" % "camel-scala" % "2.10.0.redhat-60024",
  "org.apache.camel" % "camel-hl7" % "2.10.0.redhat-60024",
  "org.apache.camel" % "camel-mina2" % "2.10.0.redhat-60024",
  "org.apache.camel" % "camel-core" % "2.10.0.redhat-60024",
  "org.apache.activemq" % "activemq-camel" % "5.8.0.redhat-60024",
  "org.apache.camel" % "camel-jms" % "2.10.0.redhat-60024",
  "ch.qos.logback" % "logback-core" % "1.0.9",
  "ch.qos.logback" % "logback-classic" % "1.0.9",
  "ca.uhn.hapi" % "hapi-osgi-base" % "1.2",
  "ca.uhn.hapi" % "hapi-structures-v23" % "1.2"
)

osgiSettings

OsgiKeys.importPackage := Seq("org.apache.camel.component.hl7.HL7MLLPCodec")

OsgiKeys.importPackage := Seq("org.apache.camel.component.hl7")

