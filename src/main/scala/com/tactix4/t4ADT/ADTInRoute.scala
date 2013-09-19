package com.tactix4.t4ADT

import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.Exchange
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.{AcknowledgmentCode, HL7Exception}
import ca.uhn.hl7v2.model.v24.message._

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.TimeoutException

import com.tactix4.wardware.wardwareConnector
import com.tactix4.openerpConnector.exception.OpenERPException

import scalaz._
import Scalaz._
import java.io.IOException
import java.util.Date
import ca.uhn.hl7v2.util.Terser
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

/**
 * A Camel Route for receiving ADT messages over an MLLP connector
 * via the mina2 component, validating them, then calling the associated
 * wardwareConnetor methods and returning an appropriate ack
 * Note: we block on the async wardwareConnector methods because the mina connection
 * is synchronous
 */
class ADTInRoute extends RouteBuilder {

  val dateFormat = DateTimeFormat.forPattern("yyyyMMDDHHmmSS")

  val connector = new wardwareConnector("http", "localhost", 8069,"admin","admin","ww_test3")

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
        case x     => exchange.out = exchange.in[Message].generateACK(AcknowledgmentCode.AR, new HL7Exception("unsupported message type: " + x, HL7Exception.UNSUPPORTED_MESSAGE_TYPE ))
      }})
    .to("log:out")
    .to("mock:out")
  }
  /**
   * Convenience method to check for null in supplied argument
   * and return a [[scalaz.ValidationNel[String, T]]
   * @param s the value to check for null
   * @param failMessage the message to return if s is null
   * @tparam T the type of the value to check for null
   * @return a [[scalaz.ValidationNel[String, T] ]]
   */
  def nullCheck[T](s: T, failMessage: String): Validation[NonEmptyList[String], T] = {
    s match {
      case null       => failMessage.failNel
      case notNull    => notNull.successNel
    }
  }

  /**
   * Convenience method to check a date for null and valid date
   *
   * @param s the date string
   * @param failMessage the fail message to return if s is null
   * @return a [[scalaz.ValidationNel[String,T] ]]
   */
  def dateCheck(s: String, failMessage: String): Validation[NonEmptyList[String], String] = {
    s match {
      case null       => failMessage.failNel
      case notNull    => try {
        (DateTime.parse(notNull,dateFormat)).toString().successNel
      } catch {
        case e: Exception => e.getMessage.failNel
      }
    }
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
      case Failure(f) => message.generateACK(AcknowledgmentCode.AE,new HL7Exception("Validation Error: " + f.toList.toString, HL7Exception.REQUIRED_FIELD_MISSING))
      case Success(s) => {
        try{
          Await.result(call(s),2000 millis)
          message.generateACK()
        } catch {
          case e: OpenERPException      => createNack(message, AcknowledgmentCode.AR,"OpenERP Error: " + e.getMessage)
          case e: TimeoutException      => message.generateACK(AcknowledgmentCode.AR,new HL7Exception("Timeout calling wardware: " + e.getMessage, HL7Exception.APPLICATION_INTERNAL_ERROR))
          case e: InterruptedException  => message.generateACK(AcknowledgmentCode.AR,new HL7Exception("Interrupted calling wardware: " + e.getMessage, HL7Exception.APPLICATION_INTERNAL_ERROR))
          case e: IOException           => message.generateACK(AcknowledgmentCode.AR,new HL7Exception("IO error calling wardware: " + e.getMessage, HL7Exception.APPLICATION_INTERNAL_ERROR))
          case e: Throwable             => message.generateACK(AcknowledgmentCode.AR,new HL7Exception(e.getMessage, HL7Exception.APPLICATION_INTERNAL_ERROR))
        }
      }
    }
  }

  def createNack(message:Message, code:AcknowledgmentCode, msg: String) = {
    message.generateACK(code,new HL7Exception(msg, HL7Exception.APPLICATION_INTERNAL_ERROR))
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

      val x: ADT_A01 = message.asInstanceOf[ADT_A01]
      x.getPID.getDateTimeOfBirth.getTimeOfAnEvent.getValueAsDate

      val terser = new Terser(message)

      val familyName = nullCheck(terser.get("/PID-5-1"),"no family name provided")
      val firstName = nullCheck(terser.get("PID-5-2"),"no first name provided")
      val dateTimeOfBirth = dateCheck(terser.get("PID-7-1"),"no dateTime of birth provided")
      val sex = nullCheck(terser.get("PID-8"), "no sex provided")



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