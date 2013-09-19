name := "t4ADT"

organization := "com.tactix4"

version := "1.0.0"

scalaVersion := "2.10.2"

resolvers += "apache public repo" at "http://repository.apache.org/content/groups/ea/"

resolvers += "fusesource repo" at "http://repo.fusesource.com/nexus/content/groups/ea/"

libraryDependencies ++= Seq(
  "org.apache.camel" % "camel-scala" % "2.12.0.redhat-610057",
  "org.apache.camel" % "camel-hl7" % "2.12.0.redhat-610057",
  "org.apache.camel" % "camel-mina" % "2.12.0.redhat-610057",
  "ca.uhn.hapi" % "hapi-osgi-base" % "1.2",
  "com.tactix4" %% "wardwareconnector" % "0.0.1-SNAPSHOT",
  "org.scalaz" %% "scalaz-core" % "7.0.3",
  "com.typesafe" % "config" % "1.0.2",
  "com.github.nscala-time" %% "nscala-time" % "0.6.0"
)

osgiSettings

OsgiKeys.importPackage ++= Seq(
  "ca.uhn.hl7v2.validation.*",
  "ca.uhn.hl7v2.*",
  "org.apache.camel.component.hl7",
  "com.tactix4.xmlrpc.*",
  "com.tactix4.openerpconnector.*",
  "com.ning.http.client.*",
  "*"
)


publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/remoteMaven/repository")))