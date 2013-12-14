package com.tactix4.t4ADT

import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.model.dataformat.HL7DataFormat
import ca.uhn.hl7v2.model.Message
import scala.concurrent.ExecutionContext.Implicits.global


import scala.concurrent.{Awaitable, Future, Await}
import scala.concurrent.duration._


import ca.uhn.hl7v2.util.Terser
import org.joda.time.format.DateTimeFormat
import com.tactix4.t4wardware.WardwareConnector
import scala.util.{Failure, Success}
import scala.collection.mutable.HashMap

/**
 * A Camel Route for receiving ADT messages over an MLLP connector
 * via the mina2 component, validating them, then calling the associated
 * wardwareConnetor methods and returning an appropriate ack
 * Note: we block on the async wardwareConnector methods because the mina connection
 * is synchronous
 */

class ADTApplicationException(msg:String, cause:Throwable=null) extends Throwable(msg,cause)
class ADTFieldException(msg:String,cause:Throwable=null)extends Throwable(msg,cause)
class ADTUnsupportedMessageException(msg:String=null,cause:Throwable=null)extends Throwable(msg,cause)

class ADTInRoute(implicit val terserMap: Map[String,Map[String, String]],
                 val protocol: String,
                 val host: String,
                 val port: Int,
                 val username: String,
                 val password: String,
                 val database: String,
                 val fromDateFormat: String,
                 val toDateFormat: String,
                 val timeOutMillis: Int,
                 val redeliveryDelay: Long,
                 val maximumRedeliveries: Int) extends RouteBuilder with ADTProcessing with ADTErrorHandling{



  val fromDateTimeFormat = DateTimeFormat.forPattern(fromDateFormat)
  val toDateTimeFormat = DateTimeFormat.forPattern(toDateFormat)
  val datesToParse = List("dob")
  lazy val connector = new WardwareConnector(protocol, host, port).startSession(username,password,database)
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
      when(_.in(triggerEventHeader) == "A01") process (e => e.in = visitNew(e.in[Message]))
      when(_.in(triggerEventHeader) == "A02") process (e => e.in = visitUpdate(e.in[Message]))
      when(_.in(triggerEventHeader) == "A03") process (e => e.in = patientDischarge(e.in[Message]))
      when(_.in(triggerEventHeader) == "A11") process (e => e.in = visitUpdate(e.in[Message]))
      when(_.in(triggerEventHeader) == "A12") process (e => e.in = visitUpdate(e.in[Message]))
      when(_.in(triggerEventHeader) == "A13") process (e => e.in = visitUpdate(e.in[Message]))
      otherwise process(e =>  throw new ADTUnsupportedMessageException("Unsupported message type: " + e.in(triggerEventHeader)) )
    }
    marshal(hl7)
    to("rabbitMQSuccess")
  }
  

  def patientMerge(message:Message): Message = {
    implicit val terser = new Terser(message)
    implicit val mappings = getMappings(terser, terserMap)
    val requiredFields = validateRequiredFields(List("otherId", "oldOtherId"))
    awaitAndWrapException(connector.flatMap(_.patientMerge(requiredFields.get("otherId").get, requiredFields.get("oldOtherId").get)))
    message.generateACK()
  }

  def patientUpdate(message: Message): Message = {
    implicit val terser = new Terser(message)
    implicit val mappings = getMappings(terser,terserMap)
    val requiredFields = getIdentifiers()
    val optionalFields = validateOptionalFields(getOptionalFields(mappings,requiredFields))
    awaitAndWrapException(connector.flatMap(_.patientUpdate(requiredFields,optionalFields.toMap)))
    message.generateACK()
  }

  def patientDischarge(message: Message) : Message = {

    implicit val terser = new Terser(message)
    implicit val mappings = getMappings(terser,terserMap)
    val requiredFields = getIdentifiers()

    awaitAndWrapException(connector.flatMap(_.patientDischarge(requiredFields)))
    message.generateACK()
  }

  def patientNew(message: Message): Message = {

    implicit val terser = new Terser(message)
    implicit val mappings = getMappings(terser,terserMap)

    val requiredFields = getIdentifiers()
    val optionalFields = validateOptionalFields(getOptionalFields(mappings,requiredFields))

    awaitAndWrapException(connector.flatMap(_.patientNew(requiredFields,optionalFields.toMap)))



    message.generateACK()
  }

  def visitNew(message:Message) : Message =
  {
    implicit val terser = new Terser(message)
    implicit val mappings = getMappings(terser,terserMap)
    val identifier = getIdentifiers()
    val dateTime = getAttribute("visitStartDateTime")
    val requiredFields =  validateRequiredFields(List("wardId","visitId"))  ++ Map(dateTime._1 -> checkDate(dateTime._2, fromDateTimeFormat, toDateTimeFormat))

    awaitAndWrapException(connector.flatMap(_.visitNew(identifier, requiredFields.get("wardId").get, requiredFields.get("visitId").get.toInt, requiredFields.get("visitStartDateTime").get)))

    message.generateACK()

  }
  def visitUpdate(m:Message) = ???

  def awaitAndWrapException[T](method: Future[T]) = {
    method.onFailure({
      case f => throw new ADTApplicationException(f.getMessage, f)
    })
    Await.result(method, timeOutMillis millis)

  }


}