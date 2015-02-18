package com.neovahealth.nhADT

import com.tactix4.t4openerp.connector.{OEConnector, _}
import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector.domain._
import com.tactix4.t4openerp.connector.transport.{OEString, OEType}
import com.typesafe.config.ConfigFactory
import org.apache.camel.component.hl7.HL7MLLPCodec
import org.apache.camel.test.spring.CamelSpringTestSupport
import org.junit.Test
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Test.Parameters
import org.scalactic.TripleEqualsSupport
import org.scalatest.Matchers
import org.scalatest.prop.Checkers.check
import org.scalatest.prop.{Checkers, PropertyChecks}
import org.springframework.context.support.{AbstractApplicationContext, ClassPathXmlApplicationContext}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scalaz.std.anyVal._
import scalaz.Monad._
import scalaz.syntax.all._
import scalaz.std.option._
import scalaz.std.option.optionSyntax._
import scalaz.std.string._
import scalaz.syntax.std.boolean._


/**
 * Created by max on 06/06/14.
 */
class ADTGenTest extends CamelSpringTestSupport with PropertyChecks with ADTGen with Matchers with TripleEqualsSupport {
  val config = ConfigFactory.load("com.neovahealth.nhADT.conf")

  val protocol: String = config.getString("openERP.protocol")
  val host: String = config.getString("openERP.hostname")
  val port: Int = config.getInt("openERP.port")
  val username: String = config.getString("openERP.username")
  val password: String = config.getString("openERP.password")
  val database: String = config.getString("openERP.database")

  val connector = new OEConnector(protocol, host, port).startSession(username, password, database)


 def createApplicationContext(): AbstractApplicationContext = {
    new ClassPathXmlApplicationContext("META-INF/spring/testBeans.xml")
  }

  val URI:String = "mina2:tcp://localhost:31337?sync=true&codec=#hl7codec"

  override def createRegistry() ={

    val jndi = super.createRegistry()
    val codec = new HL7MLLPCodec()
    codec.setCharset("iso-8859-1")
    jndi.bind("hl7codec", codec)
    jndi
  }
  val titleMap = Map("Mr." -> "Mister","Mrs" -> "Madam", "Miss" -> "Miss", "Ms" -> "Ms")


  def notHistorical(msg:ADTMsg, msgs:List[ADTMsg]) : Boolean =  {
    val previous = msgs.takeWhile(_ != msg).dropWhile(_.evn.evnOccured != msg.evn.evnOccured)
    previous.forall(p => if(List("A01", "A02", "A03").contains(p.msh.msgType)) p.evn.evnOccured == msg.evn.evnOccured else true)
  }


  @Test
  def randomVisitTest() = {
    val property = forAllNoShrink(createVisit) { (msgs: List[ADTMsg]) =>
      println("Testing visit: " + msgs.filter(_.msh.msgType != "A40").map(_.msh.msgType))
          msgs.foreach(msg => {
            if (msg.msh.msgType != "A40") {
              println(msg.toString.replaceAll("\r","\n"))
              val result:String = template.requestBody(URI, msg.toString, classOf[String])
//              assert(result contains s"MSA|AA|${msg.msh.id}", s"Result does not look like a success: $result")
//             checkPID(msg.pid)
//             if(notHistorical(msg,msgs) && List("A01","A02", "A03", "A08").contains(msg.msh.msgType)) checkVisit(msg.msh.msgType, msg.pv1)
            }
          })
          true
      }
    check( property,Parameters.default.withMinSuccessfulTests(1).withWorkers(1))
  }

  def checkVisit(msgType:String, pv1:PV1Segment) : Unit = {
    val response = Await.result(
      (for {
        ids <- connector.search("t4clinical.patient.visit", "name" === pv1.visitID)
        result <- connector.read("t4clinical.patient.visit", ids,List("pos_location_parent", "pos_location","consulting_doctor_ids", "visit_start","visit_end"))
      } yield result).run ,5 seconds)
    response.fold(
    (message: ErrorMessage) => fail("Check Visit failed: " + message),
    (v:List[Map[String,OEType]]) => v.headOption.map(d =>{
        val cdID = (d.get("consulting_doctor_ids") >>= (_.array) >>= (_.headOption) >>= (_.int)).orZero

        val cdName = Await.result(connector.read("hr.employee",List(cdID),List("name")).run, 5 seconds).map(
          _.headOption.flatMap(_.get("name").flatMap(_.string))
        )

        val cdc = pv1.consultingDoctor.toString
        val vs = d.get("visit_start").flatMap(_.string)
        val ve = d.get("visit_end").flatMap(_.string)
        val plp = d.get("pos_location").flatMap(_.array).flatMap(_(1).string).map(_.toUpperCase())
        if(msgType == "A11" || msgType == "A03"){
          assert(plp == None, s"pos_location should be None: $plp")
        } else {
          assert(plp.flatMap(wards.get) == Some(pv1.wardCode), s"pos_locations don't match: ${plp.flatMap(wards.get)} vs ${pv1.wardCode}")
        }
        assert((cdc.isEmpty ? (None:Option[String]) | Some(cdc)) == cdName.getOrElse(None), s"Consulting doctor string doesn't match: $cdc vs $cdName")
        val admitDate =pv1.admitDate.map(_.toString(oerpDateTimeFormat))
        if(pv1.admitDate != None) assert(vs == admitDate , s"Admit dates don't match: ${pv1.admitDate}")
        if(msgType == "A13"){
          assert(ve == None, s"Discharge date should be None/Null because we just cancelled the discharge. ${~ve} was returned instead")
        } else {
          val dd = pv1.dischargeDate.map(_.toString(oerpDateTimeFormat))
          assert(ve == dd, s"Discharge dates don't match: $vs vs $dd")
        }
      })
    )
  }

  def checkPID(pid:PIDSegment): Unit = {
    val response = Await.result(connector.searchAndRead("nh.clinical.patient", DomainTuple("other_identifier",com.tactix4.t4openerp.connector.domain.Equality(), OEString(pid.p.hospitalNo)), List("given_name","family_name","middle_names","title","dob","sex")).run,5 seconds)
    response.fold(
      (message: ErrorMessage) => fail("Check PID failed: " + message),
      (v: List[Map[String,OEType]]) => v.headOption.map(d =>{
        val gn = d.get("given_name").flatMap(_.string)
        val mns = d.get("middle_names").flatMap(_.string)
        val fn = d.get("family_name").flatMap(_.string)
        val t = d.get("title").flatMap(_.array).flatMap(_(1).string)
        val dob = d.get("dob").flatMap(_.string)
        val sex = d.get("sex").flatMap(_.string)
        assert(gn == pid.p.givenName)
        assert(mns == pid.p.middleNames)
        assert(fn == pid.p.familyName)
        assert( t == pid.p.title.flatMap(t2 => titleMap.get(t2)))
        assert(dob == pid.p.dob.map(_.toString(oerpDateTimeFormat)))
        assert(sex == pid.p.sex.map(_.toUpperCase))
      }) orElse fail(s"no result from server for patient ${pid.p.hospitalNo}")
    )


  }

}
