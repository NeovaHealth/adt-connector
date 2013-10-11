package com.tactix4.t4ADT

import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.model.dataformat.HL7DataFormat
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.{ErrorCode, AcknowledgmentCode, HL7Exception}

import scala.concurrent.{duration, Await}
import scala.concurrent.duration._

import com.tactix4.wardware.{WardwareConnector, WardwareException}

import ca.uhn.hl7v2.util.Terser
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.concurrent.TimeoutException
import org.apache.camel.{Exchange, LoggingLevel}
import scala.beans.BeanProperty

/**
 * A Camel Route for receiving ADT messages over an MLLP connector
 * via the mina2 component, validating them, then calling the associated
 * wardwareConnetor methods and returning an appropriate ack
 * Note: we block on the async wardwareConnector methods because the mina connection
 * is synchronous
 */

class ADTApplicationException(msg:String, cause:Throwable=null) extends Throwable(msg,cause)
class ADTFieldException(msg:String,cause:Throwable=null)extends Throwable(msg,cause)

class ADTInRoute(val terserMap: Map[String,Map[String, String]],
                 val protocol: String,
                 val host: String,
                 val port: Int,
                 val username: String,
                 val password: String,
                 val database: String,
                 val dateFormat: String,
                 val timeOutMillis: Int,
                 val redeliveryDelay: Long,
                 val maximumRedeliveries: Int) extends RouteBuilder with ADTProcessing with ADTErrorHandling{



  val dateTimeFormat = DateTimeFormat.forPattern(dateFormat)

  lazy val connector = new WardwareConnector(protocol, host, port,username,password,database)

  val triggerEventHeader = "CamelHL7TriggerEvent"
  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)


  //stick all the messages that generate errors onto the fail queue

  "hl7listener" ==> {
    unmarshal(hl7)
    choice {
      //sort by most common message type?
      when(_.in(triggerEventHeader) == "A08") process (e => e.in = patientUpdate(e.in[Message]))
      when(_.in(triggerEventHeader) == "A31") process (e => e.in = patientUpdate(e.in[Message]))
      when(_.in(triggerEventHeader) == "A28") process (e => e.in = patientNew(e.in[Message]))
      when(_.in(triggerEventHeader) == "A05") process (e => e.in = patientNew(e.in[Message]))
      when(_.in(triggerEventHeader) == "A40") process (e => e.in = patientMerge(e.in[Message]))
      when(_.in(triggerEventHeader) == "A01") process (e => e.in = createVisit(e.in[Message]))
      when(_.in(triggerEventHeader) == "A02") process (e => e.in = updateVisit(e.in[Message]))
      when(_.in(triggerEventHeader) == "A03") process (e => e.in = updateVisit(e.in[Message]))
      when(_.in(triggerEventHeader) == "A11") process (e => e.in = updateVisit(e.in[Message]))
      when(_.in(triggerEventHeader) == "A12") process (e => e.in = updateVisit(e.in[Message]))
      when(_.in(triggerEventHeader) == "A13") process (e => e.in = updateVisit(e.in[Message]))
      otherwise {
           transform(e => e.in[Message].generateACK(AcknowledgmentCode.AR, new HL7Exception("unsupported message type: " + e.in(triggerEventHeader), ErrorCode.UNSUPPORTED_MESSAGE_TYPE)))
      }
    }
    to("rabbitMQSuccess")
  }
  

  def patientMerge(message:Message): Message = {
    val terser = new Terser(message)
    val mappings = getMappings(terser, terserMap)
    val requiredFields = validateRequiredFields(List("otherId,oldOtherId"),mappings,terser)
    Await.result(connector.patientMerge(requiredFields.get("otherId").get, requiredFields.get("oldOtherId").get), timeOutMillis millis)
    message.generateACK()
  }

  def patientUpdate(message: Message): Message = {
    val terser = new Terser(message)
    val mappings = getMappings(terser,terserMap)
    val requiredFields = getIdentifiers(mappings,terser)
    val optionalFields = validateOptionalFields(getOptionalFields(mappings,requiredFields), mappings, terser)
    Await.result(connector.patientUpdate(requiredFields,optionalFields), timeOutMillis millis)
    message.generateACK()
  }

  def patientNew(message: Message): Message = {

    val terser = new Terser(message)
    val mappings = getMappings(terser,terserMap)
    val requiredFields = getIdentifiers(mappings,terser)
    val optionalFields = validateOptionalFields(getOptionalFields(mappings,requiredFields), mappings, terser)
    Await.result(connector.patientNew(requiredFields,optionalFields), timeOutMillis millis)
    message.generateACK()
  }


  def createVisit(m:Message) = ???
  def updateVisit(m:Message) = ???


}