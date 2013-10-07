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


  var EITHER_ID: String = "either"
  
  val dateTimeFormat = DateTimeFormat.forPattern(dateFormat)

  lazy val connector = new wardwareConnector(protocol, host, port,username,password,database)

  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)


  "hl7listener" ==> {
    unmarshal(hl7)
    .process((exchange: Exchange) => {
      val message = exchange.in[Message]
      exchange.in("CamelHL7TriggerEvent").asInstanceOf[String] match {
        //register patient
        //        case "A04" =>  exchange.out = exchange.in[Message].generateACK()
        //update patient
        case "A08" => exchange.in = updatePatient(message)
        //add person info / i.e. create patient
        case "A28" => exchange.in = patientNew(message)
        //check....
        case "A05" => exchange.in = patientNew(message)
        //merge patient - patient identifier list
        case "A40" => exchange.in = mergePatient(message)
        //update person information
        case "A31" => exchange.in = updatePatient(message)
        //admit patient
        case "A01" => exchange.in = createVisit(message)
        //cancel admit
        case "A11" => exchange.in = updateVisit(message)
        //transfer patient
        case "A02" => exchange.in = updateVisit(message)
        //cancel transfer patient
        case "A12" => exchange.in = updateVisit(message)
        //discharge patient
        case "A03" => exchange.in = updateVisit(message)
        //cancel discharge patient
        case "A13" => exchange.in = updateVisit(message)
        //unsupported message
        case x => exchange.in = message.generateACK(AcknowledgmentCode.AR, new HL7Exception("unsupported message type: " + x, ErrorCode.UNSUPPORTED_MESSAGE_TYPE))
      }
    })
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

  /**
   * Method takes a message, a validation method, and a method that calls [[com.tactix4.wardware.wardwareConnector]]
   * if the message passes the validation the result is passed to the wardwareConnector method,
   * the result of which is blocked on, and an appropriate [[ca.uhn.hl7v2.model.v24.message.ACK]] is returned.
   * If the validation fails a NACK is returned
   * @param message the incoming message
   * @param validation the validation required for this message
   * @param call the call to [[com.tactix4.wardware.wardwareConnector]]
   * @tparam T the type of successful result from the validation and the input to the wardwareConnector call
   * @tparam R the return type of the wardwareConnector call
   * @return a message representing an ACK or a NACK depending on the outcome of the validation or any other exceptions
   *         that might have occured
   */
  def validateAndProcess[T,R](message: Message, validation: Message => ValidationNel[String, T], call: T => Future[R]): Message ={

    validation(message) match{
      case Failure(f) => message.generateACK(AcknowledgmentCode.AE,new HL7Exception("Validation Error: " + f.toList.toString, ErrorCode.REQUIRED_FIELD_MISSING))
      case Success(s) => {
        try{
          Await.result(call(s),2000 millis)
          message.generateACK()
        } catch {
          case e: OpenERPException      => message.generateACK(AcknowledgmentCode.AR,new HL7Exception("OpenERP Error: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: TimeoutException      => message.generateACK(AcknowledgmentCode.AR,new HL7Exception("Timeout calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: InterruptedException  => message.generateACK(AcknowledgmentCode.AR,new HL7Exception("Interrupted calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: IOException           => message.generateACK(AcknowledgmentCode.AR,new HL7Exception("IO error calling wardware: " + e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
          case e: Throwable             => message.generateACK(AcknowledgmentCode.AR,new HL7Exception(e.getMessage, ErrorCode.APPLICATION_INTERNAL_ERROR))
        }
      }
    }
  }

  def createVisit(m:Message) = ???
  def updateVisit(m:Message) = ???

  def updatePatient(message:Message): Message = {
    def validateUpdatePatient(message: Message): ValidationNel[String,(String,String)] ={
      ("TODO","TODO").success
    }

    validateAndProcess(message, validateUpdatePatient, connector.updatePatient)
  }


  def addPersonInfo(message: Message): Message = {

    def validateAddPersonInfo(message: Message): ValidationNel[String,(String,String)] ={
      ("TODO","TODO").success
    }

    validateAndProcess(message, validateAddPersonInfo, connector.addPersonInfo)
  }

  def mergePatient(message: Message): Message = {
    def validateMergePatient(message: Message) : ValidationNel[String, (String, String)]={
      ("TODO","TODO").success
    }
    validateAndProcess(message, validateMergePatient, connector.mergePatient)
  }

  def updatePersonInfo(message: Message): Message = {
    def validateUpdatePersonInfo(message:Message) : ValidationNel[String, (String, String)]={
      ("TODO","TODO").success
    }
    validateAndProcess(message, validateUpdatePersonInfo, connector.updatePersonInfo)
  }

  def getMessageType(terser: Terser):ValidationNel[String,String] = {
    checkTerser("MSH-9-2", terser)
  }

  def validateMessage(message: Message, requiredFields: List[String]) :
    (ValidationNel[String,List[(String,String)]], Map[String,String]) = {

    val terser = new Terser(message)

    val mtm: Validation[NonEmptyList[String], Map[String, String]] = for {
      messageType <- getMessageType(terser) //get the message type
      messageTypeMap <- getMessageTypeMap(messageType) // get the message type mappings
    } yield messageTypeMap

    // get all the mappings minus the supplied required fields and use as the optional fields
    val optionalFields: Option[Set[String]] = mtm.map(_.keys.toSet diff requiredFields.toSet).toOption

    val requiredList = requiredFields.map(f => mtm.flatMap(m => checkAttribute(m,f,terser))).sequenceU
    val optionalVal = optionalFields.map(_.map(g => mtm.flatMap(m => checkAttribute(m,g,terser))))



    val optionalMap = optionalVal.map(_.map(_.toOption).flatMap(x=>x).toMap).getOrElse(Map())

    requiredList -> optionalMap

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

  def patientMerge(message:Message): Message = {
    val terser = new Terser(message)
    val mappings = getMappings(terser)

    val requiredFields = List("identifier")

    //the mappings for this message type defined in a properties file
    val requiredList: Validation[NonEmptyList[String], Map[String,String]] = mappings.flatMap(m => validateRequiredFields(requiredFields,m,terser))
    val optionalList: Map[String, String] = mappings.flatMap(m => requiredList.map(rl => validateOptionalFields(getOptionalFields(m,rl), m, terser).toMap)).toOption.getOrElse(Map())

    (requiredList, optionalList) match {
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