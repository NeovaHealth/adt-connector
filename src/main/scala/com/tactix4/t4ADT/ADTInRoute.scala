package com.tactix4.t4ADT

import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.Exchange
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.{ErrorCode, AcknowledgmentCode, HL7Exception}
import ca.uhn.hl7v2.model.v24.message._

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.TimeoutException

import com.tactix4.wardware.wardwareConnector

import scalaz._
import Scalaz._
import java.io.IOException
import ca.uhn.hl7v2.util.Terser
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import com.tactix4.t4openerp.connector.exception.OpenERPException

/**
 * A Camel Route for receiving ADT messages over an MLLP connector
 * via the mina2 component, validating them, then calling the associated
 * wardwareConnetor methods and returning an appropriate ack
 * Note: we block on the async wardwareConnector methods because the mina connection
 * is synchronous
 */
class ADTInRoute(val terserMap: Map[String,Map[String, String]],
                 val protocol: String,
                 val host: String,
                 val port: Int,
                 val username: String,
                 val password: String,
                 val database: String,
                 val dateFormat: String) extends RouteBuilder {

  val dateTimeFormat = DateTimeFormat.forPattern(dateFormat)

  lazy val connector = new wardwareConnector(protocol, host, port,username,password,database)

  val triggerEventHeader = "CamelHL7TriggerEvent"
  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)


  "hl7listener" ==> {
    unmarshal(hl7)
    choice {
      when(_.in(triggerEventHeader) == "A08") process (e => e.in = patientUpdate(e.in[Message]))
      when(_.in(triggerEventHeader) == "A31") process (e => e.in = patientUpdate(e.in[Message]))
      when(_.in(triggerEventHeader) == "A28") process (e => e.in = patientNew(e.in[Message]))
      when(_.in(triggerEventHeader) == "A05") process (e => e.in = patientNew(e.in[Message]))
      when(_.in(triggerEventHeader) == "A40") process (e => e.in = patientMerge(e.in[Message]))
      otherwise {
           process(e => e.in = e.in[Message].generateACK(AcknowledgmentCode.AR, new HL7Exception("unsupported message type: " + e.in(triggerEventHeader), ErrorCode.UNSUPPORTED_MESSAGE_TYPE)))

      }

    }
//    .process((exchange: Exchange) => {
//      val message = exchange.in[Message]
//      exchange.in("CamelHL7TriggerEvent").asInstanceOf[String] match {
//        //register patient
//        //        case "A04" =>  exchange.out = exchange.in[Message].generateACK()
//        //update patient
//        case "A08" => exchange.in = patientUpdate(message)
//        //add person info / i.e. create patient
//        case "A28" => exchange.in = patientNew(message)
//        //check....
//        case "A05" => exchange.in = patientNew(message)
//        //merge patient - patient identifier list
//        case "A40" => exchange.in = patientMerge(message)
//        //update person information
//        case "A31" => exchange.in = patientUpdate(message)
//        //admit patient
//        case "A01" => exchange.in = createVisit(message)
//        //cancel admit
//        case "A11" => exchange.in = updateVisit(message)
//        //transfer patient
//        case "A02" => exchange.in = updateVisit(message)
//        //cancel transfer patient
//        case "A12" => exchange.in = updateVisit(message)
//        //discharge patient
//        case "A03" => exchange.in = updateVisit(message)
//        //cancel discharge patient
//        case "A13" => exchange.in = updateVisit(message)
//        //unsupported message
//        case x => exchange.in = message.generateACK(AcknowledgmentCode.AR, new HL7Exception("unsupported message type: " + x, ErrorCode.UNSUPPORTED_MESSAGE_TYPE))
//      }
//    })
    -->("rabbitMQEndpoint")
  }
  /**
   * Convenience method to check for null in supplied argument
   * and return a [[scalaz.ValidationNel[String, T]]
   * @param s the value to check for null
   * @param t the terser object to query
   * @return a [[scalaz.ValidationNel[String, T] ]]
   */
  def checkTerser(s: String, t: Terser): ValidationNel[String, String] = {
    try {
      t.get(s) match {
        case null => ("no value found at terser path: " + s).failNel
        case notNull => notNull.successNel
      }
    }
    catch { case e: Exception => e.getMessage.failNel }
  }

  /**
   * Convenience method to check a valid date
   *
   * @param date the date string
   * @return a [[scalaz.ValidationNel[String,T] ]]
   */
  def checkDate(date: String): ValidationNel[String, String] = {
    try { (DateTime.parse(date, dateTimeFormat)).toString().successNel }
    catch { case e: Exception => e.getMessage.failNel }
  }

  def getMessageTypeMap(messageType:String): ValidationNel[String, Map[String, String]] = {
    terserMap.get(messageType).map(_.successNel[String]) | ("Could not find terser configuration for messages of type: "+ messageType).failNel[Map[String,String]]
  }
  def getTerserPath(messageTypeMap: Map[String,String], attribute: String): ValidationNel[String, String] = {
    messageTypeMap.get(attribute).map(_.successNel[String]) | ("Could not find attribute: " + attribute + " in terser configuration").failNel[String]
  }

  def checkAttribute(messageTypeMap: Map[String,String], attribute: String, terser: Terser): Validation[NonEmptyList[String], (String, String)] = {
      for {
        r <- getTerserPath(messageTypeMap,attribute)
        s <- checkTerser(r, terser)
      } yield (attribute,s)
  }

  def createVisit(m:Message) = ???
  def updateVisit(m:Message) = ???

  def getMessageType(terser: Terser):ValidationNel[String,String] = {
    checkTerser("MSH-9-2", terser)
  }


  def validateRequiredFields(fields: List[String], mappings: Map[String,String], terser: Terser ) : ValidationNel[String, Map[String,String]]= {
    fields.map(f => checkAttribute(mappings,f,terser)).sequenceU.map(_.toMap)
  }


  def validateOptionalFields(fields: List[String], mappings: Map[String,String], terser: Terser): List[(String, String)] = {
   fields.map(f => checkAttribute(mappings,f,terser).toOption).flatten
  }

  def getOptionalFields(mappings:Map[String,String], requiredFields: Map[String,String]): List[String] = {
    (mappings.keySet diff requiredFields.keySet).toList
  }

  def getMappings(terser:Terser) = {
    getMessageType(terser).flatMap(getMessageTypeMap)
  }

  def getIdentifiers(mappings:Map[String,String],terser:Terser): ValidationNel[String, Map[String, String]] = {
    val identifiers = validateOptionalFields(List("patientId", "otherId"),mappings, terser)
    if(identifiers.isEmpty) "Unable To Find Identifier".failNel[Map[String,String]]
    else identifiers.toMap.successNel[String]
  }

  def patientMerge(message:Message): Message = {
    val terser = new Terser(message)
    val mappings = getMappings(terser)

    val requiredFields = mappings.flatMap(m => getIdentifiers(m,terser))

    //the mappings for this message type defined in a properties file
    val optionalList: Map[String, String] = mappings.flatMap(m => requiredFields.map(rl => validateOptionalFields(getOptionalFields(m,rl), m, terser).toMap)).toOption.getOrElse(Map())

    (requiredFields, optionalList) match {
      case (Failure(f), _) => message.generateACK(AcknowledgmentCode.AE,new HL7Exception("Validation Error: " + f.toList.toString, ErrorCode.REQUIRED_FIELD_MISSING))
      case (Success(newIdentifier), oldIdentifier) =>
        try {
          Await.result(connector.patientMerge(newIdentifier.head._2,oldIdentifier.head._2), 2000 millis)
          message.generateACK()
        } catch {
          case e: OpenERPException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("OpenERP Error: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: TimeoutException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("Timeout calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: InterruptedException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("Interrupted calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: IOException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("IO error calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: Throwable => message.generateACK(AcknowledgmentCode.AR, new HL7Exception(e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
        }
    }

  }
  def patientUpdate(message: Message): Message = {
    val terser = new Terser(message)
    val mappings = getMappings(terser)

    val requiredFields = List("identifier")

    //the mappings for this message type defined in a properties file
    val requiredList: Validation[NonEmptyList[String], Map[String, String]] = mappings.flatMap(m => validateRequiredFields(requiredFields,m,terser))
    val optionalList: Map[String, String] = mappings.flatMap(m => requiredList.map(rl => validateOptionalFields(getOptionalFields(m,rl.toMap), m, terser).toMap)).toOption.getOrElse(Map())

    (requiredList, optionalList) match {
      case (Failure(f), _) => message.generateACK(AcknowledgmentCode.AE,new HL7Exception("Validation Error: " + f.toList.toString, ErrorCode.REQUIRED_FIELD_MISSING))
      case (Success(s), otherArgs) =>
        try {
          Await.result(connector.patientUpdate(s,otherArgs), 2000 millis)
          message.generateACK()
        } catch {
          case e: OpenERPException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("OpenERP Error: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: TimeoutException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("Timeout calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: InterruptedException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("Interrupted calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: IOException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("IO error calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: Throwable => message.generateACK(AcknowledgmentCode.AR, new HL7Exception(e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
        }
    }
  }

  def patientNew(message: Message): Message = {

    val terser = new Terser(message)
    val mappings = getMappings(terser)

    val requiredFields = List("identifier")

    //the mappings for this message type defined in a properties file
    val requiredList: Validation[NonEmptyList[String], Map[String, String]] = mappings.flatMap(m => validateRequiredFields(requiredFields,m,terser))
    val optionalList: Map[String, String] = mappings.flatMap(m => requiredList.map(rl => validateOptionalFields(getOptionalFields(m,rl.toMap), m, terser).toMap)).toOption.getOrElse(Map())

    (requiredList, optionalList) match {
      case (Failure(f), _) => message.generateACK(AcknowledgmentCode.AE,new HL7Exception("Validation Error: " + f.toList.toString, ErrorCode.REQUIRED_FIELD_MISSING))
      case (Success(s), otherArgs) =>
        try {
          Await.result(connector.patientNew(s,otherArgs), 2000 millis)
          message.generateACK()
        } catch {
          case e: OpenERPException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("OpenERP Error: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: TimeoutException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("Timeout calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: InterruptedException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("Interrupted calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: IOException => message.generateACK(AcknowledgmentCode.AR, new HL7Exception("IO error calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: Throwable => message.generateACK(AcknowledgmentCode.AR, new HL7Exception(e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
        }
    }
  }

}