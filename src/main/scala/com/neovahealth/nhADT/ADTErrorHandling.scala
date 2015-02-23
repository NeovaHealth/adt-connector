package com.neovahealth.nhADT

import java.net.ConnectException
import java.util.concurrent.TimeoutException

import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.{AcknowledgmentCode, ErrorCode, HL7Exception}
import com.neovahealth.nhADT.exceptions.ADTExceptions
import com.neovahealth.nhADT.utils.ConfigHelper
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.scala.Preamble
import org.apache.camel.scala.dsl.DSL
import org.apache.camel.scala.dsl.languages.Languages
import org.apache.camel.{Exchange, LoggingLevel}

/**
 * @author max@tactix4.com
 *         11/10/2013
 */
trait ADTErrorHandling extends  Preamble with DSL with ADTExceptions with LazyLogging with Languages{

  def getExceptionMessage(ex:Exception):String = {
    val c = ex.getCause
    if(c != null && c.getMessage != null) c.getMessage
    else ex.getMessage
  }


  handle[Exception] {
    log(LoggingLevel.WARN,"Error for patient ${header.other_identifier}\n${exception.message}\n${exception.stacktrace}")
    setHeader("error", simple("${exception.message}"))
    transform(e =>{
      val exception: Exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, classOf[Exception])
      e.getIn.setHeader("exception",getExceptionMessage(exception))
      logger.info(exception.getMessage,exception)
      e.in[Message].generateACK(AcknowledgmentCode.AR,new HL7Exception(getExceptionMessage(exception),ErrorCode.APPLICATION_INTERNAL_ERROR))
    })
    to("failMsgHistory")
  }.handled.maximumRedeliveries(0)

}
