package com.tactix4.t4ADT
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.component.hl7.HL7.ack
import ca.uhn.hl7v2.validation.impl.DefaultValidation
import org.apache.camel.component.hl7.HL7.messageConformsTo
import org.apache.camel.component.hl7.{AckExpression, AckCode}
import ca.uhn.hl7v2.model.v23.message.ACK
import org.apache.camel.Exchange
import ca.uhn.hl7v2.model.Message

/**
 * A Camel Router using the Scala DSL
 */
class ADTInRoute extends RouteBuilder {

  val hl7 = new HL7DataFormat();
  hl7.setValidate(false)


  val defaultContext = new DefaultValidation()
  "mina2:tcp://localhost:8889?sync=true&codec=#hl7codec" ==> {
    process((exchange: Exchange) => exchange.out = exchange.in[Message].generateACK())
    .to("log:out")
    .to("mock:out")
  }

//    handle[Exception]{
//    }.handled

//    .validate(messageConformsTo(defaultContext))
   // .unmarshal(hl7)
    // using choice as the content base router
//    choice {
//        where we choose that A19 queries invoke the handleA19 method on our hl7service bean
//        when(_.getIn.getHeader("CamelHL7TriggerEvent") == "A02")  to("log:a02")
        // and A01 should invoke the handleA01 method on our hl7service bean
//        when(_.getIn.getHeader("CamelHL7TriggerEvent") == "A01") to("log:a01")
        // other types should go to mock:unknown
//        otherwise to("log:unknown")
//    }
   // .marshal(hl7)
//    .transform(ack())
 // }
}