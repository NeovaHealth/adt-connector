package com.tactix4.t4ADT

import java.io.File
import java.util.concurrent.TimeUnit
import com.typesafe.config.{Config, ConfigValue, ConfigFactory}
import org.apache.camel.impl.{PropertyPlaceholderDelegateRegistry, JndiRegistry}
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.{EndpointConfiguration, LoggingLevel, Exchange}
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.component.redis.RedisConstants._
import org.apache.camel.component.redis._

import org.apache.camel.component.hl7.TerserLanguage
import ca.uhn.hl7v2.util.Terser
import ca.uhn.hl7v2.model.Message
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.{StringRedisSerializer, RedisSerializer}
import scalaz._
import Scalaz._

import org.joda.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, DateTimeFormat}

import com.tactix4.t4skr.T4skrConnector
import com.tactix4.t4skr.core._

import com.typesafe.scalalogging.slf4j.Logging
import org.apache.camel.scala.dsl.SIdempotentConsumerDefinition
import com.tactix4.t4ADT.exceptions.ADTExceptions
import scala.util.matching.Regex
import scala.collection.JavaConversions._
import org.apache.camel.component.hl7.HL7.terser

class ADTInRoute() extends RouteBuilder with T4skrCalls with ADTErrorHandling with ADTProcessing with ADTExceptions with Logging {

  type VisitName = VisitId


  val f = new File("etc/tactix4/com.tactix4.t4ADT.conf")

  val config: Config = ConfigFactory.parseFile(f)

  val protocol: String = config.getString("openERP.protocol")
  val host: String = config.getString("openERP.hostname")
  val port: Int = config.getInt("openERP.port")
  val username: String = config.getString("openERP.username")
  val password: String = config.getString("openERP.password")
  val database: String = config.getString("openERP.database")
  val wards : List[Regex] = config.getStringList("misc.ward_names").map(_.r).toList
  val sexMap: Map[String, String] = config.getObject("ADT_mappings.sex_map").toMap.mapValues(_.unwrapped().asInstanceOf[String])
  val inputDateFormats: List[String] = config.getStringList("misc.valid_date_formats").toList
  val toDateFormat: String = config.getString("openERP.to_date_format")
  val datesToParse: Set[String] = config.getStringList("ADT_mappings.dates_to_parse").toSet
  val timeOutMillis: Long = config.getDuration("camel_redelivery.time_out",TimeUnit.MILLISECONDS)
  val redeliveryDelay: Long = config.getDuration("camel_redelivery.delay",TimeUnit.MILLISECONDS)
  val maximumRedeliveries: Int = config.getInt("camel_redelivery.maximum_redeliveries")
  val ignoreUnknownWards: Boolean = config.getBoolean("misc.ignore_unknown_wards")
  val bedRegex:Regex = config.getString("misc.bed_regex").r
  val ratePer2Seconds:Int = config.getInt("misc.rate_per_2_seconds")
  val supportedMsgTypes = config.getStringList("misc.supported_msg_types")

  val connector = new T4skrConnector(protocol, host, port).startSession(username, password, database)
  val fromDateTimeFormat: DateTimeFormatter = new DateTimeFormatterBuilder().append(null, inputDateFormats.map(DateTimeFormat.forPattern(_).getParser).toArray).toFormatter
  val toDateTimeFormat = DateTimeFormat.forPattern(toDateFormat)
  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)

  val updateVisitRoute = "direct:updateOrCreateVisit"
  val updatePatientRoute = "direct:updateOrCreatePatient"
  val msgHistory = "msgHistory"
  val detectDuplicates = "direct:detectDuplicates"
  val detectUnsupportedMsg = "direct:detectUnsupportedMsgs"
  val detectUnsupportedWards = "direct:detectUnsupportedWards"
  val detectHistoricalMsg = "direct:detectHistoricalMsg"
  val setBasicHeaders = "direct:setBasicHeaders"
  val setExtraHeaders = "direct:setExtraHeaders"
  val detectIdConflict = "direct:idConflictCheck"
  val detectVisitConflict = "direct:visitConflictCheck"


  val A08Route = "direct:A08"
  val A31Route = "direct:A31"
  val A05Route = "direct:A05"
  val A28Route = "direct:A28"

  val A01Route = "direct:A01"
  val A02Route = "direct:A02"
  val A03Route = "direct:A03"
  val A11Route = "direct:A11"
  val A12Route = "direct:A12"
  val A13Route = "direct:A13"
  val A40Route = "direct:A40"

  val incomingQueue = "seda:incoming"

  val msgType = (e:Exchange) => e.getIn.getHeader(triggerEventHeader, classOf[String])

  def reasonCode(implicit e:Exchange) = e.getIn.getHeader("eventReasonCode",classOf[Option[String]])

  "hl7listener" --> incomingQueue routeId "incoming"

  incomingQueue ==> {
    throttle(ratePer2Seconds per(2 seconds)) {
      unmarshal(hl7)
      SIdempotentConsumerDefinition(idempotentConsumer(_.in("CamelHL7MessageControl")).messageIdRepositoryRef("messageIdRepo").skipDuplicate(false).removeOnFailure(false))(this) {
        setHeader("msgBody", e => e.getIn.getBody.toString)
        -->(setBasicHeaders)
        -->(detectDuplicates)
        -->(detectUnsupportedMsg)
        -->(detectUnsupportedWards)
        -->(setExtraHeaders)
        -->(detectIdConflict)
        -->(detectVisitConflict)
        -->(detectHistoricalMsg)
        //split on msgType
        choice {
          when(msgEquals("A08")) --> A08Route
          when(msgEquals("A31")) --> A31Route
          when(msgEquals("A05")) --> A05Route
          when(msgEquals("A28")) --> A28Route
          when(msgEquals("A01")) --> A01Route
          when(msgEquals("A11")) --> A11Route
          when(msgEquals("A03")) --> A03Route
          when(msgEquals("A13")) --> A13Route
          when(msgEquals("A02")) --> A02Route
          when(msgEquals("A12")) --> A12Route
          when(msgEquals("A40")) --> A40Route
          otherwise {
            throwException(new ADTUnsupportedMessageException("Unsupported msg type"))
          }
        }
        -->(msgHistory)
        process(e => e.in = e.in[Message].generateACK())
      }
    }
  } routeId "Main Route"

  detectHistoricalMsg ==> {
    when(e => msgType(e) != "A03" && e.getIn.getHeader("hasDischargeDate", false, classOf[Boolean])) {
      throwException(new ADTHistoricalMessageException("Historical message detected"))
    }

  }

  detectDuplicates ==>{
    when(_.getProperty(Exchange.DUPLICATE_MESSAGE)) throwException new ADTDuplicateMessageException("Duplicate message")
  } routeId "Detect Duplicates"

  detectUnsupportedMsg ==> {
    when(e => !(supportedMsgTypes contains e.in(triggerEventHeader).toString)) throwException new ADTUnsupportedMessageException("Unsupported msg type")
  } routeId "Detect Unsupported Msg"

  detectUnsupportedWards ==> {
    when(e => !isSupportedWard(e)) throwException new ADTUnsupportedWardException("Unsupported ward")
  } routeId "Detect Unsupported Wards"

  detectIdConflict ==> {
     when(e => {
        val p1 = getPatientLinkedToHospitalNo(e)
        val p2 = getPatientLinkedToNHSNo(e)
        p1.isDefined && p2.isDefined && p1 != p2
      }) {
        process( e => throw new ADTConsistencyException("Hospital number: " +e.in("hospitalNo") + " is linked to patient: "+ e.in("patientLinkedToHospitalNo") +
          " but NHS number: " + e.in("NHSNo") + "is linked to patient: " + e.in("patientLinkedToNHSNo")))
      }
  } routeId "Detect Id Conflict"

  detectVisitConflict ==> {
     //check for conflict with visits
      when(e => {
        val pv = getPatientLinkedToVisit(e)
        val ph = getPatientLinkedToHospitalNo(e)
        pv.isDefined && pv != ph
      }){
        process( e => throw new ADTConsistencyException("Hospital number: " + e.in("hospitalNo") + " is linked to patient: " + e.in("patientLinkedToHospitalNo") + " " +
          " but visit: " + e.in("visitName") + " is linked to patient: " + e.in("patientLinkedToVisit") + ""))
      }
  } routeId "Detect Visit Conflict"

 setBasicHeaders ==> {
   process(e => {
     val message = e.in[Message]
     val t = new Terser(message)

     e.getIn.setHeader("origMessage", message)
     e.getIn.setHeader("terser", t)
     e.getIn.setHeader("hospitalNo", getHospitalNumber(t))
     e.getIn.setHeader("hospitalNoString", ~getHospitalNumber(t))
     e.getIn.setHeader("NHSNo", getNHSNumber(t))
     e.getIn.setHeader("eventReasonCode",getEventReasonCode(t))
     e.getIn.setHeader("visitName", getVisitName(t))
     e.getIn.setHeader("visitNameString", ~getVisitName(t))
     e.getIn.setHeader("ignoreUnknownWards", ignoreUnknownWards)
     e.getIn.setHeader("hasDischargeDate", hasDischargeDate(t))
     e.getIn.setHeader("timestamp", ~getTimestamp(t))
   })
 } routeId "Set Basic Headers"

  setExtraHeaders ==> {
    process(e => {
      val hid = e.getIn.getHeader("hospitalNo",None,classOf[Option[String]])
      val nhs = e.getIn.getHeader("NHSNo",None,classOf[Option[String]])
      val visitName = e.getIn.getHeader("visitName",None,classOf[Option[String]])
      val visitId = visitName.flatMap(getVisit)

      e.getIn.setHeader("patientLinkedToHospitalNo", hid.flatMap(getPatientByHospitalNumber))
      e.getIn.setHeader("patientLinkedToNHSNo", nhs.flatMap(getPatientByNHSNumber))
      e.getIn.setHeader("visitId", visitId)
      e.getIn.setHeader("patientLinkedToVisit", visitId.flatMap(getPatientByVisitId))
    })
  } routeId "Set Extra Headers"

  updatePatientRoute ==> {
    when(e => patientExists(e)) {
      process(e => patientUpdate(e))
    } otherwise {
      process(e => patientNew(e))
    }
  } routeId "Create/Update Patient"

  updateVisitRoute ==> {
    when(e => visitExists(e)) {
      process(e => visitUpdate(e))
    } otherwise {
      when(_.in("visitName") != None) process (e => visitNew(e))
    }
  } routeId "Create/Update Visit"


  A01Route ==> {
    when(e => visitExists(e)) throwException new ADTFieldException("Visit already exists")
    -->(updatePatientRoute)
    process(e => visitNew(e))
    -->("direct:persistTimestamp")
  } routeId "A01"



  A11Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancel admit on visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelVisitNew(e))
    }
  } routeId "A11"

  A02Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling transferPatient for a visit that doesn't exist - ignoring")
      to(updateVisitRoute)
    } otherwise {
      process(e => patientTransfer(e))
    }
    -->("direct:persistTimestamp")
  } routeId "A02"

  from("direct:persistTimestamp") ==> {
    setHeader(KEY,simple("${header.visitNameString}"))
    setHeader(VALUE,simple("${header.timestamp}"))
    to("spring-redis://localhost:6379?command=SET&redisTemplate=#redisTemplate")
  }

  from("direct:getVisitTimestamp") ==> {
    setHeader(COMMAND,"GET")
    setHeader(KEY,simple("${header.visitNameString}"))
    enrich("spring-redis://localhost:6379?redisTemplate=#redisTemplate",new AggregateLastModTimestamp)
    log(LoggingLevel.INFO,"Last Mod Timestamp: ${header.lastModTimestamp} vs This message timestamp: ${header.timestamp}")
  }

  A12Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)){
      log(LoggingLevel.WARN, "Calling cancelTransferPatient for visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelPatientTransfer(e))
    }
    to(updateVisitRoute)
  } routeId "A12"

  A03Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling discharge for a visit that doesn't exist - ignoring")
    } otherwise {
      process(e => patientDischarge(e))
      -->("direct:persistTimestamp")
    }
  } routeId "A03"

  A13Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancelDischarge on visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelPatientDischarge(e))
    }
    to(updateVisitRoute)
  } routeId "A13"

  A05Route ==> {
    when(e => patientExists(e)) process(e => throw new ADTApplicationException("Patient with hospital number: " + e.in("hospitalNo") + " already exists"))
    process(e => patientNew(e))
    to(updateVisitRoute)
  } routeId "A05"

  A28Route ==> {
    when(e => patientExists(e)) process(e => throw new ADTApplicationException("Patient with hospital number: " + e.in("hospitalNo") + " already exists"))
    process(e => patientNew(e))
    to(updateVisitRoute)
  } routeId "A28"

  A40Route ==> {
    when(e => !patientExists(e) || !mergeTargetExists(e)) throwException new ADTConsistencyException("Patients to merge did not exist")
    process(e => patientMerge(e))
    to(updateVisitRoute)
  } routeId "A40"

  A08Route ==> {
    -->("direct:getVisitTimestamp")
    when(e => e.in("lastModTimestamp") == e.in("timestamp") || e.in("lastModTimestamp") == ""){
      log(LoggingLevel.INFO,"Updating latest visit")
      -->(updatePatientRoute)
      filter(e => (for {
        codes <- getReasonCodes(e)
        mycode <- reasonCode(e)
      } yield codes contains mycode) | true ) {
        to(updateVisitRoute)
      }
    } otherwise {
      process(e => {
        logger.info("last mod timestamp: " + e.getIn.getHeader("lastModTimestamp"))
      })
      process(e => logger.info("timestamp: " + e.getIn.getHeader("timestamp")))
      log(LoggingLevel.INFO,"Ignoring Historical Message")
    }
  } routeId "A08"

  A31Route ==> {
    -->(updatePatientRoute)
  } routeId "A31"
}

