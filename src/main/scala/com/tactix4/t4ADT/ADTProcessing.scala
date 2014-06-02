package com.tactix4.t4ADT

import ca.uhn.hl7v2.util.Terser
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatter
import scala.util.control.Exception._
import com.tactix4.t4skr.core.{HospitalNo, VisitId}
import scalaz._
import Scalaz._
import com.tactix4.t4ADT.exceptions.ADTExceptions

/**
 * @author max@tactix4.com
 *         11/10/2013
 */
trait ADTProcessing extends ADTExceptions{

  val fromDateTimeFormat:DateTimeFormatter
  val toDateTimeFormat:DateTimeFormatter
  val datesToParse:List[String]
  val sexMap:Map[String,String]
  val mappings:Map[String,String]
  val msgTypeMappings:Map[String, Map[String,String]]
  val hospitalNumber = "other_identifier"
  val oldHospitalNumber = "old_other_identifier"
  val EmptyStringMatcher = """^"?\s*"?$""".r
  val Sex = """(?i)^sex$""".r


  /**
   * Convenience method to check a valid date
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
    try sexMap(m)
    catch {
      case e:Throwable => throw new ADTFieldException("Could not find value in sex map")
    }
  }

  def getMsgType(implicit terser:Terser) : Option[String] = getValueFromPath("MSH-9-2")

  def getHospitalNumber(implicit terser:Terser): Option[HospitalNo] = getMessageValue(hospitalNumber)

  def getOldHospitalNumber(implicit terser:Terser) : Option[String] = getMessageValue(oldHospitalNumber)

  def getNHSNumber(implicit terser:Terser): Option[String] =  getMessageValue("patient_identifier")

  def getVisitName(implicit terser:Terser) : Option[VisitId] =  getMessageValue("visit_identifier")

  def getWardIdentifier(implicit terser:Terser): Option[String] =  getMessageValue("ward_identifier")

  def getMessageValue(name:String)(implicit terser:Terser):Option[String] = {

    val msgTypeOverride = for {
      mt <- getMsgType(terser)
      mm <- msgTypeMappings.get(mt)
      path  <- mm.get(name)
      value  <- getValueFromPath(path)
    } yield value

    (msgTypeOverride orElse mappings.get(name).flatMap(getValueFromPath)).map(
      s => {
        if(datesToParse contains name) parseDate(s)
        else if(Sex.findFirstIn(s).isDefined) parseSex(s)
        else s
        }
    )

  }

  def getValueFromPath(path:String)(implicit terser:Terser):Option[String] = {
    allCatch opt terser.get(path) flatMap{
      case null => None
      case EmptyStringMatcher() => None
      case string =>  string.some
    }

  }

}
