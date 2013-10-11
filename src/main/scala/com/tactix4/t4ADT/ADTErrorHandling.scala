package com.tactix4.t4ADT

import ca.uhn.hl7v2.{AcknowledgmentCode, HL7Exception, ErrorCode}
import ca.uhn.hl7v2.model.Message
import org.apache.camel.Exchange
import java.util.concurrent.TimeoutException
import com.tactix4.wardware.WardwareException
import org.apache.camel.scala.dsl.DSL
import org.apache.camel.scala.Preamble

/**
 * @author max@tactix4.com
 *         11/10/2013
 */
trait ADTErrorHandling extends  Preamble with DSL{

  var redeliveryDelay:Int
  var maximumRedeliveries:Int

  //handle missing required fields
  handle[ADTFieldException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Validation Error: "  + exception.getCause.getMessage, ErrorCode.REQUIRED_FIELD_MISSING))
     })
    to("rabbitMQFail")
  }.maximumRedeliveries(0).handled

  //handle internal errors
  handle[ADTApplicationException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Internal Application Error: " + exception.getCause.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR)
      )})
    to("rabbitMQFail")
  }.maximumRedeliveries(0).handled

  //handle errors from wardware
  handle[WardwareException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Wardware Exception: " + exception.getCause.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR)
      )})
    to("rabbitMQFail")
  }.maximumRedeliveries(maximumRedeliveries).redeliveryDelay(redeliveryDelay).handled

  //handle timeouts
  handle[TimeoutException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Timeout communicating with Wardware: " + exception.getCause.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR)
      )
    })
    to("rabbitMQFail")
  }.maximumRedeliveries(maximumRedeliveries).redeliveryDelay(redeliveryDelay).handled


}
