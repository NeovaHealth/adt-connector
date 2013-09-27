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

  val connector = new wardwareConnector(protocol, host, port,username,password,database)

  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)


  "hl7listener" ==> {
    unmarshal(hl7).process((exchange: Exchange) => {
      val message = exchange.in[Message]
      exchange.getIn.getHeader("CamelHL7TriggerEvent").asInstanceOf[String] match {
        //register patient
//        case "A04" =>  exchange.out = exchange.in[Message].generateACK()
        //update patient
        case "A08" => exchange.out = updatePatient(message)
        //add person info
        case "A28" => exchange.out = addPersonInfo(message)
        //merge patient - patient identifier list
        case "A40" => exchange.out = mergePatient(message)
        //update person information
        case "A31" => exchange.out = updatePersonInfo(message)
        //admit patient
        case "A01" => exchange.out = admitPatient(message)
        //cancel admit
        case "A11" => exchange.out = cancelAdmitPatient(message)
        //transfer patient
        case "A02" => exchange.out = transferPatient(message)
        //cancel transfer patient
        case "A12" => exchange.out = cancelTransferPatient(message)
        //discharge patient
        case "A03" => exchange.out = dischargePatient(message)
        //cancel discharge patient
        case "A13" => exchange.out = cancelDischargePatient(message)
        //unsupported message
        case x     => exchange.out = exchange.in[Message].generateACK(AcknowledgmentCode.AR, new HL7Exception("unsupported message type: " + x, ErrorCode.UNSUPPORTED_MESSAGE_TYPE ))
      }})
    .marshal(hl7)
    .to("log:out")
    .to("rabbitmq://localhost/test?username=guest&password=guest")
  }
  /**
   * Convenience method to check for null in supplied argument
   * and return a [[scalaz.ValidationNel[String, T]]
   * @param s the value to check for null
   * @param failMessage the message to return if s is null
   * @return a [[scalaz.ValidationNel[String, T] ]]
   */
  def checkTerser(s: String, failMessage: String)(implicit t: Terser): ValidationNel[String, String] = {
    try {
      t.get(s) match {
        case null => failMessage.failNel
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
  def getTerserPath(messageTypeMap: Map[String,String])(attribute: String): ValidationNel[String, String] = {
    messageTypeMap.get(attribute).map(_.successNel[String]) | ("Could not find attribute: " + attribute + " in terser configuration").failNel[String]
  }

  def checkTerserPath(messageType: String, attribute: String)(implicit terser: Terser): ValidationNel[String, String] = {
      for {
        m <- getMessageTypeMap(messageType)
        r <- getTerserPath(m)(attribute)
        s <- checkTerser(r, attribute + " not found at terser path: " + attribute)
      } yield s
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


  def admitPatient(message: Message): Message = {

    def validateAdmitPatientMessage(message:Message):ValidationNel[String, (String, String, String, String)] = {

      implicit val terser = new Terser(message)

      val familyName = checkTerserPath("A01", "familyName")
      val firstName =  checkTerserPath("A01","firstName")
      val dateTimeOfBirth = checkTerserPath("A01","dateTimeOfBirth").flatMap(checkDate)
      val sex = checkTerserPath("A01", "sex")


      (familyName |@| firstName |@| dateTimeOfBirth |@| sex) {  (_,_,_,_) }
    }

    validateAndProcess(message, validateAdmitPatientMessage,connector.registerPatient)

  }


  def cancelAdmitPatient(message: Message): Message = {
    def validateCancelAdmitPatient(message: Message): ValidationNel[String,(String,String)] ={
      ("TODO","TODO").success
    }
    validateAndProcess(message, validateCancelAdmitPatient, connector.cancelRegisterPatient)
  }

  def transferPatient(message: Message):Message = {
    def validateTransferPatient(message:Message): ValidationNel[String, (String,String)] = {
      ("TODO","TODO").success
    }
    validateAndProcess(message, validateTransferPatient, connector.transferPatient)
  }

  def cancelTransferPatient(message:Message):Message = {
    def validateCancelTransferPatient(message:Message) : ValidationNel[String, (String,String)] = {
      ("TODO","TODO").success
    }
    validateAndProcess(message, validateCancelTransferPatient, connector.cancelTransferPatient)
  }

  def dischargePatient(message: Message) : Message = {
    def validateDischargePatient(message: Message) : ValidationNel[String, (String, String)] = {
      ("TODO","TODO").success
    }
    validateAndProcess(message, validateDischargePatient, connector.dischargePatient)
  }

  def cancelDischargePatient(message: Message) : Message = {
    def validateCancelDischargePatient(message: Message) : ValidationNel[String, (String, String)] = {
      ("TODO","TODO").success
    }
   validateAndProcess(message,validateCancelDischargePatient, connector.cancelDischargePatient)
  }


}