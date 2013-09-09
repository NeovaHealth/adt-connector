name := "t4ADT"

organization := "com.tactix4"

version := "1.0.0"

scalaVersion := "2.9.1"

resolvers += "apache public repo" at "http://repository.apache.org/content/groups/public/"

resolvers += "fusesource repo" at "http://repo.fusesource.com/nexus/content/groups/public/"

libraryDependencies ++= Seq(
  "org.apache.camel" % "camel-scala" % "2.10.0.redhat-60024",
  "org.apache.camel" % "camel-hl7" % "2.10.0.redhat-60024",
  "org.apache.camel" % "camel-mina2" % "2.10.0.redhat-60024"
)

defaultOsgiSettings

OsgiKeys.importPackage ++= Seq(
        "org.apache.camel.component.hl7",
        "org.apache.camel.component.hl7.*"
)

