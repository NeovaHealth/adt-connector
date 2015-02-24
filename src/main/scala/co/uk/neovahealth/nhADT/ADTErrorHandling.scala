package co.uk.neovahealth.nhADT

import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.{AcknowledgmentCode, ErrorCode, HL7Exception}

import co.uk.neovahealth.nhADT.exceptions.ADTExceptions
import co.uk.neovahealth.nhADT.utils.{Action, ConfigHelper}

import com.typesafe.scalalogging.slf4j.StrictLogging

import org.apache.camel.scala.Preamble
import org.apache.camel.scala.dsl.DSL
import org.apache.camel.scala.dsl.languages.Languages
import org.apache.camel.{Exchange, LoggingLevel}
import scalaz.syntax.std.option._
import scalaz.std.string._

/**
 * @author max@tactix4.com
 *         11/10/2013
 */
trait ADTErrorHandling extends Preamble with DSL with ADTExceptions with StrictLogging with Languages with ADTProcessing{

  handle[ADTHistoricalMessage]{
    process(e => logger.warn("Error for patient " + ~getHospitalNumber(e)))
    log(LoggingLevel.WARN,"${exception.message}\n${exception.stacktrace}")
    setHeader("error", (e:Exchange) => simple("${exception.message}")(e))
    process(e => ConfigHelper.historicalMessageAction match {
      case Action.IGNORE => {
        logger.info("Ignoring historical message")
        e.in = e.in[Message].generateACK()
      }
      case Action.ERROR => {
        logger.info("Returning error for  historical message")
        e.in = e.in[Message].generateACK(AcknowledgmentCode.AR, new HL7Exception(e.in("error").toString))
      }
    })
    to("failMsgHistory")
  }.handled

  handle[ADTRuleException] {
    process(e => logger.warn("Error for patient " + ~getHospitalNumber(e)))
    log(LoggingLevel.WARN,"${exception.message}\n${exception.stacktrace}")
    setHeader("error", (e:Exchange) => simple("${exception.message}")(e))
    choice{
      when(_.in("IGNORE") == false){
        transform(_.in[Message].generateACK())
      }
      otherwise {
        transform(e => e.in[Message].generateACK(AcknowledgmentCode.AR, new HL7Exception(e.in("error").toString)))
      }
    }
    to("failMsgHistory")
  }.handled

  handle[Exception] {
    process(e => logger.warn("Error for patient " + ~getHospitalNumber(e)))
    log(LoggingLevel.WARN,"${exception.message}\n${exception.stacktrace}")
    setHeader("error", (e:Exchange) => simple("${exception.message}")(e))
    transform(e => e.in[Message].generateACK(AcknowledgmentCode.AR,new HL7Exception(e.in("error").toString)))
    to("failMsgHistory")
  }.handled

}
