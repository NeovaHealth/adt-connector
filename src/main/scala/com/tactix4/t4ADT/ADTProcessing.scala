package com.tactix4.t4ADT

import ca.uhn.hl7v2.util.Terser
import ca.uhn.hl7v2.HL7Exception
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormatter, DateTimeFormat}
import scala.util.control.Exception.catching
import scala.collection.mutable
import scala.util.control.Exception._
import com.tactix4.t4skr.core.VisitId

/**
 * @author max@tactix4.com
 *         11/10/2013
 */
trait ADTProcessing {

  val fromDateTimeFormat:DateTimeFormatter
  val toDateTimeFormat:DateTimeFormatter
  val datesToParse:List[String]
  val sexMap:Map[String,String]
  val hosptialNumber = "other_identifier"
  val oldHospitalNumber = "old_other_identifier"  
  /**
   * query a terser object for a specified path
   * @param s the terser path
   * @param t the terser object to query
   * @return the value from the specified terser path
   * @throws ADApplicationException if there was an error parsing the supplied path
   * @throws ADTFieldException if the field was empty
   */
  def getValueFromTerserPath(s: String, t: Terser): String  = {
    val e = catching(classOf[Throwable]) either {
      t.get(s) match {
          case null => throw new ADTFieldException("no value found at terser path: " + s)
          case "" => throw new ADTFieldException("no value found at terser path: " + s)
          case "\"\"" => throw new ADTFieldException("no value found at terser path: " + s)
          case someString:String => someString
      }
    }

    e.fold(
      {
        case e: ADTFieldException => throw e
        case e: HL7Exception => throw new ADTFieldException(e.getMessage, e)
        case e: IllegalArgumentException => throw new ADTApplicationException("Error in terser path: " + e.getMessage, e)
        case e: NullPointerException => throw new ADTApplicationException("Error in terser path: " + e.getMessage, e)
      },
      (value: String) => value
    )

  }

  /**
   * Convenience method to check a valid date
   *
   * @param date the date string
   */
  def checkDate(date: String,fromDateFormat:DateTimeFormatter, toDateFormat:DateTimeFormatter): String = {
    val c = catching(classOf[Exception]) either  DateTime.parse(date, fromDateFormat).toString(toDateFormat)
    c.fold(
      (throwable: Throwable) => throw new ADTFieldException("Error parsing date: " + date + "\nError: " + throwable.getMessage, throwable),
      (s:String) => s)
  }

  def getMessageTypeMap(terserMap:Map[String, Map[String,String]], messageType:String): Map[String, String] = {
    terserMap.get(messageType).getOrElse(throw new ADTApplicationException("Could not find terser configuration for messages of type: "+ messageType))
  }
  def getTerserPathFromMap(messageTypeMap: Map[String,String], attribute: String): String = {
    messageTypeMap.get(attribute).getOrElse(throw new ADTApplicationException("Could not find attribute: " + attribute + " in terser configuration"))
  }

  def getAttribute(attribute: String)(implicit terser: Terser,messageTypeMap: Map[String, String]): (String, String) = {
    val path = getTerserPathFromMap(messageTypeMap, attribute)
    val value = getValueFromTerserPath(path, terser)
    attribute -> value
  }

  def getMessageType(implicit terser: Terser):String = {
    getValueFromTerserPath("MSH-9-2", terser)
  }

  def parseSexField(m: mutable.HashMap[String,String]): Unit = catching(classOf[NoSuchElementException]).opt(m.get("sex").map((s:String) => m("sex") = sexMap(s))).map(throw new ADTFieldException("Value for sex field is unknown"))

  def validateRequiredFields(fields: List[String])(implicit mappings: Map[String,String], terser: Terser ) : Map[String,String]= {
    val rs = mutable.HashMap[String,String](fields.map(getAttribute).toMap.toSeq: _*)
    if(fields exists (_ == "sex"))  parseSexField(rs)

    datesToParse.foreach(d =>
      rs.get(d).map(dob=> rs(d) = checkDate(dob, fromDateTimeFormat, toDateTimeFormat))
    )
    rs.toMap
  }

  def validateAllOptionalFields(requiredFields: Map[String,String])(implicit mappings: Map[String,String], terser: Terser): Map[String, String] = {
   validateOptionalFields(getOptionalFields(requiredFields)).toMap
  }

  def validateOptionalFields(fields: List[String])(implicit mappings: Map[String,String], terser: Terser): mutable.HashMap[String, String] = {
    val opts = mutable.HashMap(fields.map(f => catching(classOf[ADTFieldException], classOf[ADTApplicationException]).opt(getAttribute(f))).flatten.toMap.toSeq: _*)

    if(fields exists (_ == "sex")) catching(classOf[ADTFieldException]).opt(parseSexField(opts))

    datesToParse.foreach(d =>
      opts.get(d).map(dob=> opts(d) = checkDate(dob, fromDateTimeFormat, toDateTimeFormat))
    )
    opts
  }

  def getOptionalFields(requiredFields: Map[String,String])(implicit mappings:Map[String,String]): List[String] = {
    (mappings.keySet diff requiredFields.keySet).toList
  }

  def getMappings(implicit terser:Terser, terserMap:Map[String, Map[String,String]]) = {
    getMessageTypeMap(terserMap, getMessageType(terser))
  }

  def getHospitalNumber(implicit mappings:Map[String,String],terser:Terser): String = {
      validateRequiredFields(List(hosptialNumber)).apply(hosptialNumber)
  }

  def getVisitNumber(implicit mappings:Map[String,String],terser:Terser) : Option[VisitId] = {
      validateOptionalFields(List("visit_identifier")).get("visit_identifier")
  }

}
