package uk.co.neovahealth.nhADT

import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.util.Terser
import uk.co.neovahealth.nhADT.utils.ConfigHelper
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.apache.camel.Exchange
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter, DateTimeFormatterBuilder}
import uk.co.neovahealth.nhADT.exceptions.ADTExceptions
import uk.co.neovahealth.nhADT.utils.{Action, ConfigHelper}

import scala.reflect.ClassTag
import scala.util.control.Exception._

/**
 * @author max@tactix4.com
 *         11/10/2013
 */
trait ADTProcessing extends ADTExceptions with StrictLogging{

  val datesToParse: Set[String] = ConfigHelper.datesToParse
  val sexMap: Map[String, String] = ConfigHelper.sexMap
  val EmptyStringMatcher = """^"?\s*"?$""".r
  val Sex = """(?i)^sex$""".r

  val fromDateTimeFormat: DateTimeFormatter = new DateTimeFormatterBuilder().append(null, ConfigHelper.inputDateFormats.map(DateTimeFormat.forPattern(_).getParser).toArray).toFormatter
  val toDateTimeFormat = DateTimeFormat.forPattern(ConfigHelper.toDateFormat)

  def getHeader[T:ClassTag](name:String)(implicit e:Exchange):Option[T] = e.getIn.getHeader(name,None,classOf[Option[T]])
  def setHeader(name:String, o:Any)(implicit e:Exchange) = e.getIn.setHeader(name,o)
  
  def getHeaderOrUpdate[T:ClassTag](name:String)(f: => Option[T])(implicit e:Exchange):Option[T] = getHeader[T](name) orElse {
    val v = f
    setHeader(name,v)
    v
  }
  
  def getTerser(implicit e:Exchange):Option[Terser] =
    getHeaderOrUpdate("terser")(allCatch opt new Terser(e.getIn.getBody(classOf[Message])))

  def getField[T:ClassTag](value:String)(implicit e:Exchange) : Option[T] =
    getHeaderOrUpdate[T](value)(getMessageValue(value).map(_.asInstanceOf[T]))

  def handleUnknownVisit(createAction: Exchange => Unit) = (e: Exchange) => {
    ConfigHelper.unknownVisitAction match {
      case Action.IGNORE => logger.warn("Visit doesn't exist - ignoring")
      case Action.ERROR  => throw new ADTUnknownVisitException("Unknown visit")
      case Action.CREATE => createAction(e)
    }
  }

  def handleUnknownPatient(createAction: Exchange => Unit) = (e: Exchange) => {
    ConfigHelper.unknownPatientAction match {
      case Action.IGNORE => logger.warn("Patient doesn't exist - ignoring")
      case Action.ERROR  => throw new ADTUnknownPatientException("Unknown patient")
      case Action.CREATE => createAction(e)
    }
  }

  /** * Convenience method to check a valid date
   *
   * @param date the date string
   */
  def parseDate(date: String):String = {
    try {
      DateTime.parse(date, fromDateTimeFormat).toString(toDateTimeFormat)
    } catch {
      case e:Throwable => throw new ADTFieldException("Could not parse date: " + e.getMessage)
    }
  }

  def parseSex(m: String): String = {
    try {
      sexMap(m.toUpperCase)
    } catch {
      case e:Throwable => throw new ADTFieldException("Could not find value in sex map")
    }
  }

  def getMsgType(e:Exchange): Option[String] = e.getIn.getHeader("CamelHL7TriggerEvent", "", classOf[String]) match {
    case "" => None
    case anything => Some(anything)
  }

  def getHospitalNumber(implicit e:Exchange): Option[String] = getField("other_identifier")

  def getOldHospitalNumber(implicit e:Exchange) : Option[String] = getField("old_other_identifier")

  def getNHSNumber(implicit e:Exchange): Option[String] =  getField("patient_identifier")

  def getDischargeDate(implicit e:Exchange) : Option[String] = getField("discharge_date")

  def getTimestamp(implicit e:Exchange) : Option[String] =  getField("timestamp")

  def getVisitName(implicit e:Exchange) : Option[String] = getField("visit_identifier")

  def getWardIdentifier(implicit e:Exchange): Option[String] =  getField("location")

  def getOriginalWardIdentifier(implicit e:Exchange): Option[String] = getField("original_location")

  def getVisitStartDate(implicit e:Exchange) : Option[String] = getField("visit_start_date")

  def getBed(implicit e:Exchange) : Option[String] = getField("bed")


  def getPath(name:String)(implicit e:Exchange):Option[String]  =
    for {
      msgType <- getMsgType(e)
      c <- ConfigHelper.getConfigForType(msgType)
      path <- allCatch opt c.getString(name)
    } yield path


  def getMessageValue(name:String)(implicit e:Exchange):Option[String] =
     for {
      path    <- getPath(name)
      value   <- getValueFromPath(path)
    } yield if(datesToParse contains name) parseDate(value)
            else if(Sex.findFirstIn(name).isDefined) parseSex(value)
            else value

  def getValueFromPath(path:String)(implicit e:Exchange):Option[String] =
    for {
      t <- getTerser
      v <- allCatch opt t.get(path) flatMap {
        case null => None
        case EmptyStringMatcher() => None
        case string => Some(string)
      }
    } yield v

}
