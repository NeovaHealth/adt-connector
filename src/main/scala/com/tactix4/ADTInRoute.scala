package com.tactix4

import org.apache.camel.Exchange
import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * A Camel Router using the Scala DSL
 */
class ADTInRoute extends RouteBuilder {

  "mina:tcp://localhost:8888?sync=true&amp;codec=#hl7codec" ==> to("log:input")
}
