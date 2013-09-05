package com.tactix4.t4ADT
import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * A Camel Router using the Scala DSL
 */
class ADTInRoute extends RouteBuilder {

  "hl7listener" ==> log("log:input")
}