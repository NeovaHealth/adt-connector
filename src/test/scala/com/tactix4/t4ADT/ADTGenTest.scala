package com.tactix4.t4ADT

import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import org.apache.camel.component.hl7.HL7MLLPCodec
import org.apache.camel.test.spring.CamelSpringTestSupport
import org.springframework.context.support.{ClassPathXmlApplicationContext, AbstractApplicationContext}
import org.apache.camel.ExchangePattern
import org.junit.Test
import com.tactix4.t4skr.T4skrConnector
import com.typesafe.config.ConfigFactory
import java.util.Properties
import java.io.FileInputStream
import scala.concurrent.Await
import scala.concurrent.duration._
import com.tactix4.t4openerp.connector.transport.{OEString, OEDictionary}
import org.scalautils._
import TypeCheckedTripleEquals._
import org.scalacheck.Prop.forAllNoShrink
import org.scalatest.prop.Checkers.check
import com.tactix4.t4openerp.connector.domain.DomainTuple
import com.tactix4.t4openerp.connector
import scalaz._
import Scalaz._
import org.scalacheck.Gen
import org.scalacheck.Prop.{forAll, BooleanOperators}


/**
 * Created by max on 06/06/14.
 */
class ADTGenTest extends CamelSpringTestSupport with PropertyChecks with ADTGen with Matchers with TripleEqualsSupport {
  val config = ConfigFactory.load("com.tactix4.t4ADT.conf")

  val protocol: String = config.getString("openERP.protocol")
  val host: String = config.getString("openERP.hostname")
  val port: Int = config.getInt("openERP.port")
  val username: String = config.getString("openERP.username")
  val password: String = config.getString("openERP.password")
  val database: String = config.getString("openERP.database")

  val connector = new T4skrConnector(protocol, host, port).startSession(username, password, database)

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




  @Test
  def randomVisitTest() = {
    check {
      forAllNoShrink(createVisit) { (msgs: List[ADTMsg]) =>
          log.info("Testing the visit: " + msgs.map(_.evn.msgType).mkString(" - "))
          msgs.foreach(msg => {
            if (msg.msh.msgType != "A40") {
              log.info("sending: \n" + msg.toString.replace("\r", "\n"))
              val result:String = template.requestBody(URI, msg.toString, classOf[String])
              log.info("got result: \n" + result.replace("\r", "\n"))
              assert(result contains s"MSA|AA|${msg.msh.id}", s"Result does not look like a success: $result")
              Thread.sleep(2000)
              checkPID(msg.pid)
              checkVisit(msg.msh.msgType, msg.pv1)
            }
          })
          true

      }
    }
  }

  def checkVisit(msgType:String, pv1:PV1Segment) : Unit = {
    val response = Await.result(connector.oeSession.searchAndRead("t4clinical.patient.visit", DomainTuple("name", com.tactix4.t4openerp.connector.domain.Equality(),OEString(pv1.visitID)), List("pos_location_parent", "pos_location","consultants_string", "visit_start","visit_end")).value,5 seconds)
    response.fold(
    (message: ErrorMessage) => fail("Check Visit failed: " + message),
      (v:List[OEDictionary]) => v.headOption.map(d =>{
        //(wardCode:String,bed:Int,wardName:String,consultingDoctor:Doctor,referringDoctor:Doctor,admittingDoctor:Doctor,hospitalService:String,patientType:String,visitID:VisitId,admitDate:String,dischargeDate:String
        val plp = d.get("pos_location").flatMap(_.array).flatMap(_(1).string).map(_.toUpperCase())
        log.info("pos_location:" + plp)
        val cs = d.get("consultants_string").flatMap(_.string)
        val cdc = pv1.consultingDoctor.toString
        val vs = d.get("visit_start").flatMap(_.string)
        val ve = d.get("visit_end").flatMap(_.string)
        if(msgType == "A11" || msgType == "A03"){
          assert(plp == None)
        } else {
          assert(plp.flatMap(wards.get) == Some(pv1.wardCode))
        }
        assert((cdc.isEmpty ? (None:Option[String]) | Some(cdc)) == (cs.getOrElse("").isEmpty ? (None:Option[String]) | cs))
        if(pv1.admitDate != None) assert(vs == pv1.admitDate.map(_.toString(oerpDateTimeFormat)))
        if(msgType == "A13"){
          assert(ve == None, s"Discharge date should be None/Null because we just cancelled the discharge. ${~ve} was returned instead")
        } else {
          assert(ve == pv1.dischargeDate.map(_.toString(oerpDateTimeFormat)))
        }
      })
    )
  }

  def checkPID(pid:PIDSegment): Unit = {
    val response = Await.result(connector.oeSession.searchAndRead("t4clinical.patient", DomainTuple("other_identifier",com.tactix4.t4openerp.connector.domain.Equality(), OEString(pid.p.hospitalNo)), List("given_name","family_name","middle_names","title","dob","sex")).value,5 seconds)
    response.fold(
      (message: ErrorMessage) => fail("Check PID failed: " + message),
      (v: List[OEDictionary]) => v.headOption.map(d =>{
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
