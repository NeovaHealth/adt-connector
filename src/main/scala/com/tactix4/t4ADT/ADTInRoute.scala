package com.tactix4.t4ADT
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.model.dataformat.HL7DataFormat
import ca.uhn.hl7v2.validation.impl.DefaultValidation
import org.apache.camel.Exchange
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.HL7Exception

/**
 * A Camel Router using the Scala DSL
 */
class ADTInRoute extends RouteBuilder {

  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)


  val defaultContext = new DefaultValidation()
  "hl7listener" ==> {
    unmarshal(hl7).process((exchange: Exchange) => {
      exchange.getIn.getHeader("CamelHL7TriggerEvent").asInstanceOf[String] match {
        //register patient
        case "A01" =>  exchange.out = exchange.in[Message].generateACK()
//        //update patient
//        case "A08" =>
//        //add person info
//        case "A28" =>
//        //merge patient - patient identifier list
//        case "A40" =>
//        //merge patient info- patient ID only
//        case "A34" =>
//        //admit patient
//        case "A01" =>
//        //cancel admit
        case "A11" => exchange.out = exchange.in[Message].generateACK()
//        //transfer patient
//        case "A02" =>
//        //cancel transfer patient
//        case "A12" =>
//        //discharge patient
//        case "ADT_A03" =>
//        //cancel discharge patient
//        case "ADT_A13" =>
////        //unsupported message
        case x  => exchange.out = exchange.in[Message].generateACK("AR", new HL7Exception("unsupported message type: " + x, HL7Exception.UNSUPPORTED_MESSAGE_TYPE ))
      }})
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