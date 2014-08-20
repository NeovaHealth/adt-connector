package com.tactix4.t4ADT

import ca.uhn.hl7v2.util.Terser
import com.tactix4.t4ADT.utils.ConfigHelper
import com.typesafe.config.Config
import org.apache.camel.Exchange
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, DateTimeFormat}
import scala.util.control.Exception._
import com.tactix4.t4skr.core.{HospitalNo, VisitId}
import scala.util.matching.Regex
import scalaz._
import Scalaz._
import com.tactix4.t4ADT.exceptions.ADTExceptions
import scala.collection.JavaConversions._

/**
 * @author max@tactix4.com
 *         11/10/2013
 */
trait ADTProcessing extends ADTExceptions{

  val maximumRedeliveries: Int = ConfigHelper.maximumRedeliveries
  val redeliveryDelay: Long = ConfigHelper.redeliveryDelay
  val bedRegex: Regex = ConfigHelper.bedRegex
  val datesToParse: Set[String] = ConfigHelper.datesToParse
  val sexMap: Map[String, String] = ConfigHelper.sexMap
  val hospitalNumber = "other_identifier"
  val oldHospitalNumber = "old_other_identifier"
  val EmptyStringMatcher = """^"?\s*"?$""".r
  val Sex = """(?i)^sex$""".r

  val fromDateTimeFormat: DateTimeFormatter = new DateTimeFormatterBuilder().append(null, ConfigHelper.inputDateFormats.map(DateTimeFormat.forPattern(_).getParser).toArray).toFormatter
  val toDateTimeFormat = DateTimeFormat.forPattern(ConfigHelper.toDateFormat)

  def reasonCode(implicit e:Exchange) = e.getIn.getHeader("eventReasonCode",classOf[Option[String]])

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

  def extractBedName(s:String) : Option[String] = bedRegex.findFirstIn(s)

  def getMsgType(implicit terser:Terser) : Option[String] = getValueFromPath("MSH-9-2")

  def getHospitalNumber(implicit terser:Terser): Option[HospitalNo] = getMessageValue(hospitalNumber)

  def getOldHospitalNumber(implicit terser:Terser) : Option[String] = getMessageValue(oldHospitalNumber)

  def getNHSNumber(implicit terser:Terser): Option[String] =  getMessageValue("patient_identifier")

  def hasDischargeDate(implicit terser:Terser) : Boolean = {
    val v = getMessageValue("discharge_date")
     v.isDefined
  }
  def getTimestamp(implicit terser:Terser) : Option[String] =  getMessageValue("timestamp")

  def getVisitName(implicit terser:Terser) : Option[VisitId] =  getMessageValue("visit_identifier")

  def getEventReasonCode(implicit terser:Terser) : Option[String] =  getMessageValue("event_reason_code")

  def getWardIdentifier(implicit terser:Terser): Option[String] =  getMessageValue("ward_identifier")

  def getReasonCodes(implicit e:Exchange) : Option[Set[String]] = for {
    msgType <- getMsgType(e.getIn.getHeader("terser",None, classOf[Terser]))
    reasonCodes = ConfigHelper.getConfigForType(msgType).getStringList("reason_codes").toSet
  } yield reasonCodes

  def getMessageValue(name:String)(implicit terser:Terser):Option[String] = {
    for {
      msgType <- getMsgType(terser)
      c = ConfigHelper.getConfigForType(msgType)
      path <- allCatch opt c.getString(name)
      value <- getValueFromPath(path)
    } yield if(name == "bed") extractBedName(value) | value
            else if(datesToParse contains name) parseDate(value)
            else if(Sex.findFirstIn(name).isDefined) parseSex(value)
            else value
  }

  def getValueFromPath(path:String)(implicit terser:Terser):Option[String] = {
    allCatch opt terser.get(path) flatMap{
      case null => None
      case EmptyStringMatcher() => None
      case string =>  string.some
    }

  }



}
