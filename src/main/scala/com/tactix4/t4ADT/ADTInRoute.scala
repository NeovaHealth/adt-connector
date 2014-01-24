package com.tactix4.t4ADT

import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.model.dataformat.HL7DataFormat
import scala.concurrent.ExecutionContext.Implicits.global

import ca.uhn.hl7v2.model.Message
import scala.util.control.Exception._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._


import ca.uhn.hl7v2.util.Terser
import org.joda.time.format.DateTimeFormat
import com.tactix4.t4skr.T4skrConnector
import com.tactix4.t4ADT.utils.Instrumented
import com.codahale.metrics.Meter
import nl.grons.metrics.scala
import org.apache.camel.Exchange

/**
 * A Camel Route for receiving ADT messages over an MLLP connector
 * via the mina2 component, validating them, then calling the associated
 * t4skrConnetor methods and returning an appropriate ack
 * Note: we block on the async t4skrConnector methods because the mina connection
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
                 val maximumRedeliveries: Int) extends RouteBuilder with ADTProcessing with ADTErrorHandling with Instrumented{



  val fromDateTimeFormat = DateTimeFormat.forPattern(fromDateFormat)
  val toDateTimeFormat = DateTimeFormat.forPattern(toDateFormat)
  val datesToParse = List("dob","visitStartDateTime")
  val connector = new T4skrConnector(protocol, host, port).startSession(username,password,database).map(s => {s.openERPSession.context.setTimeZone("Europe/London"); s})

  val triggerEventHeader = "CamelHL7TriggerEvent"
  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)




  "hl7listener" ==> {
    unmarshal(hl7)
    process(_ => metrics.meter("AllMessages").mark())
    choice {
      //sort by most common message type?
      when(_.in(triggerEventHeader) == "A08") process (e => {metrics.meter("A08").mark();e.in =  patientUpdate(e.in[Message])})
      when(_.in(triggerEventHeader) == "A31") process (e => {metrics.meter("A31").mark();e.in =  patientUpdate(e.in[Message])})
      when(_.in(triggerEventHeader) == "A28") process (e => {metrics.meter("A28").mark();e.in =  patientNew(e.in[Message])})
      when(_.in(triggerEventHeader) == "A05") process (e => {metrics.meter("A05").mark();e.in =  patientNew(e.in[Message])})
      when(_.in(triggerEventHeader) == "A40") process (e => {metrics.meter("A40").mark();e.in =  patientMerge(e.in[Message])})
      when(_.in(triggerEventHeader) == "A01") process (e => {metrics.meter("A01").mark();e.in =  visitNew(e.in[Message])})
      when(_.in(triggerEventHeader) == "A02") process (e => {metrics.meter("A02").mark();e.in =  patientTransfer(e.in[Message])})
      when(_.in(triggerEventHeader) == "A03") process (e => {metrics.meter("A03").mark();e.in =  patientDischarge(e.in[Message])})
      when(_.in(triggerEventHeader) == "A11") process (e => {metrics.meter("A11").mark();e.in =  visitUpdate(e.in[Message])})
      when(_.in(triggerEventHeader) == "A12") process (e => {metrics.meter("A12").mark();e.in =  visitUpdate(e.in[Message])})
      when(_.in(triggerEventHeader) == "A13") process (e => {metrics.meter("A13").mark();e.in =  visitUpdate(e.in[Message])})
      otherwise process(e =>  {metrics.meter("Unsupported").mark(); throw new ADTUnsupportedMessageException("Unsupported message type: " + e.in(triggerEventHeader)) })
    }
    marshal(hl7)
    to("rabbitMQSuccess")
  }

  def extract(f : Terser => Map[String,String] => Future[_]) (implicit message:Message): Message = {

    implicit val terser = new Terser(message)
    implicit val mappings = getMappings(terser, terserMap)

    val result = allCatch either Await.result(f(terser)(mappings), timeOutMillis millis)

    result.left.map((error: Throwable) => throw new ADTApplicationException(error.getMessage, error))

    message.generateACK()
  }

  def patientMerge(implicit message:Message): Message = extract { implicit terser => implicit mappings=>
    val requiredFields = validateRequiredFields(List("otherId", "oldOtherId"))
    connector.flatMap(_.patientMerge(requiredFields("otherId"), requiredFields("oldOtherId")))
  }

  def patientTransfer(implicit message:Message): Message = extract { implicit terser => implicit mappings=>
    val i = getIdentifiers
    val w = validateRequiredFields(List("wardId"))
    connector.flatMap(_.patientTransfer(i,w("wardId")))
  }

  def patientUpdate(implicit message:Message) :Message = extract {implicit terser => implicit map =>
    val i = getIdentifiers
    val o = validateAllOptionalFields
    connector.flatMap(_.patientUpdate(i,o))
  }

  def patientDischarge(implicit message: Message)  = extract{implicit t => implicit m =>
    val i = getIdentifiers
    connector.flatMap(_.patientDischarge(i))
  }

  def patientNew(implicit message: Message) = extract{implicit t => implicit m =>
    val i = getIdentifiers
    val o = validateAllOptionalFields
    connector.flatMap(_.patientNew(i,o))
  }

  def visitNew(implicit message: Message) = extract{implicit t => implicit m =>
    val requiredFields =  validateRequiredFields(List("wardId","visitId","visitStartDateTime"))
    connector.flatMap(_.visitNew(getIdentifiers,requiredFields("wardId"), requiredFields("visitId"), requiredFields("visitStartDateTime")))
  }

  def visitUpdate(implicit message:Message) = extract{ implicit t => implicit m =>
    Future.successful()
  }

}
