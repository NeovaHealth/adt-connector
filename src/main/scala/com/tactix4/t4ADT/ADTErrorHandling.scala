package com.tactix4.t4ADT

import ca.uhn.hl7v2.{AcknowledgmentCode, HL7Exception, ErrorCode}
import ca.uhn.hl7v2.model.Message
import org.apache.camel.Exchange
import java.util.concurrent.TimeoutException
import org.apache.camel.scala.dsl.DSL
import org.apache.camel.scala.Preamble
import java.net.ConnectException
import com.tactix4.t4ADT.exceptions._

/**
 * @author max@tactix4.com
 *         11/10/2013
 */
trait ADTErrorHandling extends  Preamble with DSL with ADTExceptions {

  val redeliveryDelay:Long
  val maximumRedeliveries:Int

  def getExceptionMessage(ex:Exception):String = {
    val c = ex.getCause
    if(c != null && c.getMessage != null) c.getMessage
    else ex.getMessage
  }

   //handle all exceptions for debugging
  handle[ConnectException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Connect Exception: "  + getExceptionMessage(exception), ErrorCode.APPLICATION_INTERNAL_ERROR))
     })
    to("failMsgHistory")
  }.maximumRedeliveries(0).handled

  //handle missing required fields
  handle[ADTFieldException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Validation Error: "  + getExceptionMessage(exception), ErrorCode.REQUIRED_FIELD_MISSING))
     })
    to("failMsgHistory")
  }.maximumRedeliveries(0).handled

  //handle internal errors
  handle[ADTApplicationException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Internal Application Error: " + getExceptionMessage(exception), ErrorCode.APPLICATION_INTERNAL_ERROR)
      )})
    to("failMsgHistory")
  }.maximumRedeliveries(0).handled


  //handle timeouts
  handle[TimeoutException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Timeout communicating with T4skr: " + getExceptionMessage(exception), ErrorCode.APPLICATION_INTERNAL_ERROR)
      )
    })
    to("failMsgHistory")
  }.maximumRedeliveries(maximumRedeliveries).redeliveryDelay(redeliveryDelay).handled


  handle[ADTUnsupportedWardException]{
    choice{
      when(_.getIn.getHeader("ignoreUnknownWards",classOf[Boolean])){
        log("Ignoring unknown ward")
        transform(_.in[Message].generateACK())
        to("msgHistory")
      }
      otherwise {
        transform(e => {
          val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
          e.getIn.setHeader("exception", getExceptionMessage(exception))
          e.in[Message].generateACK(AcknowledgmentCode.AR, new HL7Exception(getExceptionMessage(exception), ErrorCode.UNSUPPORTED_MESSAGE_TYPE))

        })
        to("failMsgHistory")
      }
    }
  }.handled


  handle[ADTUnsupportedMessageException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AR, new HL7Exception(getExceptionMessage(exception), ErrorCode.UNSUPPORTED_MESSAGE_TYPE))
    }
    )
    to("failMsgHistory")
  }.handled

  handle[ADTDuplicateMessageException] {
    transform(e =>{
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AR,new HL7Exception(getExceptionMessage(exception),ErrorCode.DUPLICATE_KEY_IDENTIFIER))
    })
    to("failMsgHistory")
  }.handled


  handle[ADTConsistencyException] {
    transform(e =>{
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AR,new HL7Exception(getExceptionMessage(exception),ErrorCode.APPLICATION_INTERNAL_ERROR))
    })
    to("failMsgHistory")
  }.handled
}
