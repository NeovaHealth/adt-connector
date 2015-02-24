package co.uk.neovahealth.nhADT.utils

import java.util
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import scala.util.matching.Regex
import scala.collection.JavaConversions._
import scala.util.control.Exception._


/**
 * Created by max on 25/07/14.
 */
object ConfigHelper {
  def getConfigForType(s: String): Option[Config] =  allCatch opt config.getConfig(s"ADT_mappings.$s").withFallback(config.getConfig("ADT_mappings.common"))

  val f = {
   val t =new java.io.File("etc/nh/co.uk.neovahealth.nhADT.conf")
    if (!t.canRead) new java.io.File("src/test/resources/co.uk.neovahealth.nhADT.conf")
    else t
  }

  lazy val optionalPatientFields: List[String] = config.getStringList("ADT_mappings.optional_patient_fields").toList
  lazy val optionalVisitFields: List[String] = config.getStringList("ADT_mappings.optional_visit_fields").toList
  val ruleFile = (allCatch  opt io.Source.fromFile("etc/nh/co.uk.neovahealth.nhADT.rules") orElse {
      allCatch opt io.Source.fromFile("src/test/resources/co.uk.neovahealth.nhADT.rules")
    } getOrElse(throw new Exception("Can not read rules config file"))).getLines.filterNot(l => l.startsWith("#") || l.isEmpty).toList

  val config: Config = ConfigFactory.parseFile(f)

  val consultingDoctorFields: List[String] = config.getStringList("ADT_mappings.consulting_doctor_fields").toList
  val referringDoctorFields: List[String] = config.getStringList("ADT_mappings.referring_doctor_fields").toList


  val protocol: String = config.getString("openERP.protocol")
  val host: String = config.getString("openERP.hostname")
  val port: Int = config.getInt("openERP.port")
  val username: String = config.getString("openERP.username")
  val password: String = config.getString("openERP.password")
  val database: String = config.getString("openERP.database")
  val autoAck:Boolean = config.getBoolean("misc.auto_ack")
  val sexMap: Map[String, String] = config.getObject("ADT_mappings.sex_map").toMap.mapValues(_.unwrapped().asInstanceOf[String])
  val inputDateFormats: List[String] = config.getStringList("misc.valid_date_formats").toList
  val toDateFormat: String = config.getString("openERP.to_date_format")
  val datesToParse: Set[String] = config.getStringList("ADT_mappings.dates_to_parse").toSet
  val timeOutMillis: Long = config.getDuration("openERP.time_out",TimeUnit.MILLISECONDS)

  val historicalMessageAction: Action.Value = config.getString("misc.historic_message_action").toLowerCase match {
    case "ignore" => Action.IGNORE
    case "error" => Action.ERROR
    case fail => throw new Exception(s"Unknown option for misc.historic_message_action: $fail")
  }
  val unknownPatientAction: Action.Value = config.getString("misc.unknown_patient_action").toLowerCase match {
    case "ignore" => Action.IGNORE
    case "create" => Action.CREATE
    case "error" => Action.ERROR
    case fail => throw new Exception(s"Unknown option for misc.unknown_patient_action: $fail")
  }
  val unknownVisitAction: Action.Value = config.getString("misc.unknown_visit_action").toLowerCase match {
    case "ignore" => Action.IGNORE
    case "create" => Action.CREATE
    case "error" => Action.ERROR
    case fail => throw new Exception(s"Unknown option for misc.unknown_visit_action: $fail")

  }
//  val supportedMsgTypes = config.getStringList("misc.supported_msg_types").toSet

  val getRecipientLists: Map[String, List[String]] = config.getObject("Recipient_Lists").unwrapped().mapValues(_.asInstanceOf[util.ArrayList[String]].toList).toMap[String,List[String]]


}
