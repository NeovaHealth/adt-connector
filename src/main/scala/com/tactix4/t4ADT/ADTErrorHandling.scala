package com.tactix4.t4ADT

import ca.uhn.hl7v2.{AcknowledgmentCode, HL7Exception, ErrorCode}
import ca.uhn.hl7v2.model.Message
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.{LoggingLevel, Exchange}
import java.util.concurrent.TimeoutException
import org.apache.camel.scala.dsl.DSL
import org.apache.camel.scala.Preamble
import java.net.ConnectException
import com.tactix4.t4ADT.exceptions._
import com.typesafe.scalalogging.slf4j.LazyLogging

/**
 * @author max@tactix4.com
 *         11/10/2013
 */
trait ADTErrorHandling extends  Preamble with DSL with ADTExceptions with LazyLogging{

  val redeliveryDelay:Long
  val maximumRedeliveries:Int
  val hl7:HL7DataFormat = new HL7DataFormat()
  hl7.setValidate(false)

  def getExceptionMessage(ex:Exception):String = {
    val c = ex.getCause
    if(c != null && c.getMessage != null) c.getMessage
    else ex.getMessage
  }

   //handle all exceptions for debugging
  handle[ConnectException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      logger.info(exception.getMessage,exception)
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Connect Exception: "  + getExceptionMessage(exception), ErrorCode.APPLICATION_INTERNAL_ERROR))
     })
    to("failMsgHistory")
    marshal(hl7)
  }.maximumRedeliveries(0).handled

  //handle missing required fields
  handle[ADTFieldException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      logger.info(exception.getMessage,exception)
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Validation Error: "  + getExceptionMessage(exception), ErrorCode.REQUIRED_FIELD_MISSING))
     })
    to("failMsgHistory")
    marshal(hl7)
  }.maximumRedeliveries(0).handled

  //handle internal errors
  handle[ADTApplicationException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      logger.info(exception.getMessage,exception)
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AE, new HL7Exception("Internal Application Error: " + getExceptionMessage(exception), ErrorCode.APPLICATION_INTERNAL_ERROR)
      )})
    to("failMsgHistory")
    marshal(hl7)
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
    marshal(hl7)
  }.maximumRedeliveries(maximumRedeliveries).redeliveryDelay(redeliveryDelay).handled


  handle[ADTUnsupportedWardException]{
    choice{
      when(_.getIn.getHeader("ignoreUnknownWards",classOf[Boolean])){
        log(LoggingLevel.INFO,"Ignoring unknown ward")
        transform(_.in[Message].generateACK())
        to("msgHistory")
      }
      otherwise {
        log(LoggingLevel.ERROR,"Unknown ward")
        transform(e => {
          val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
          logger.error(exception.getMessage,exception)
          e.getIn.setHeader("exception", getExceptionMessage(exception))
          e.in[Message].generateACK(AcknowledgmentCode.AR, new HL7Exception(getExceptionMessage(exception), ErrorCode.UNSUPPORTED_MESSAGE_TYPE))

        })
        to("failMsgHistory")
      }
    }
    marshal(hl7)
  }.handled.maximumRedeliveries(0)

  handle[ADTUnsupportedMessageException] {
    transform(e => {
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      logger.info(exception.getMessage,exception)
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      e.in[Message].generateACK(AcknowledgmentCode.AR, new HL7Exception(getExceptionMessage(exception), ErrorCode.UNSUPPORTED_MESSAGE_TYPE))
    })
    to("failMsgHistory")
    marshal(hl7)
  }.handled.maximumRedeliveries(0)

  handle[ADTHistoricalMessageException] {
    log(LoggingLevel.INFO,"Ignoring historical data")
    transform(_.in[Message].generateACK())
    to("msgHistory")
    marshal(hl7)
  }.handled.maximumRedeliveries(0)

  handle[ADTDuplicateMessageException] {
    transform(e =>{
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      logger.info(exception.getMessage,exception)
      e.in[Message].generateACK(AcknowledgmentCode.AR,new HL7Exception(getExceptionMessage(exception),ErrorCode.DUPLICATE_KEY_IDENTIFIER))
    })
    to("failMsgHistory")
    marshal(hl7)
  }.handled.maximumRedeliveries(0)


  handle[ADTConsistencyException] {
    transform(e =>{
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      logger.info(exception.getMessage,exception)
      e.in[Message].generateACK(AcknowledgmentCode.AR,new HL7Exception(getExceptionMessage(exception),ErrorCode.APPLICATION_INTERNAL_ERROR))
    })
    to("failMsgHistory")
    marshal(hl7)
  }.handled.maximumRedeliveries(0)
}
