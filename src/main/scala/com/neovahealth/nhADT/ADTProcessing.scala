package com.neovahealth.nhADT

import ca.uhn.hl7v2.util.Terser
import com.neovahealth.nhADT.exceptions.ADTExceptions
import com.neovahealth.nhADT.utils.ConfigHelper
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter, DateTimeFormatterBuilder}

import scala.util.control.Exception._
import scala.util.matching.Regex

/**
 * @author max@tactix4.com
 *         11/10/2013
 */
trait ADTProcessing extends ADTExceptions{

  val datesToParse: Set[String] = ConfigHelper.datesToParse
  val sexMap: Map[String, String] = ConfigHelper.sexMap
  val EmptyStringMatcher = """^"?\s*"?$""".r
  val Sex = """(?i)^sex$""".r

  val fromDateTimeFormat: DateTimeFormatter = new DateTimeFormatterBuilder().append(null, ConfigHelper.inputDateFormats.map(DateTimeFormat.forPattern(_).getParser).toArray).toFormatter
  val toDateTimeFormat = DateTimeFormat.forPattern(ConfigHelper.toDateFormat)


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

  def getMsgType(implicit terser:Terser) : Option[String] = getValueFromPath("MSH-9-2")

  def getHospitalNumber(implicit terser:Terser): Option[String] = getMessageValue("other_identifier")

  def getOldHospitalNumber(implicit terser:Terser) : Option[String] = getMessageValue("old_other_identifier")

  def getNHSNumber(implicit terser:Terser): Option[String] =  getMessageValue("patient_identifier")

  def getDischargeDate(implicit terser:Terser) : Option[String] = getMessageValue("discharge_date")

  def getTimestamp(implicit terser:Terser) : Option[String] =  getMessageValue("timestamp")

  def getVisitName(implicit terser:Terser) : Option[String] =  getMessageValue("visit_identifier")

  def getWardIdentifier(implicit terser:Terser): Option[String] =  getMessageValue("ward_identifier")

  def getMessageValue(name:String)(implicit terser:Terser):Option[String] = {
    for {
      msgType <- getMsgType(terser)
      c       <- ConfigHelper.getConfigForType(msgType)
      path    <- allCatch opt c.getString(name)
      value   <- getValueFromPath(path)
    } yield if(datesToParse contains name) parseDate(value)
            else if(Sex.findFirstIn(name).isDefined) parseSex(value)
            else value
  }

  def getValueFromPath(path:String)(implicit terser:Terser):Option[String] = {
    allCatch opt terser.get(path) flatMap {
      case null => None
      case EmptyStringMatcher() => None
      case string =>  Some(string)
    }

  }



}
