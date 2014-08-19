package com.tactix4.t4ADT

/**
 * Tests the A28 and A05 patientNew route
 * @author max@tactix4.com
 * Date: 26/09/13
 */

import java.io.File

import org.scalacheck.Prop.forAllNoShrink
import org.scalatest.prop.Checkers.check
import com.tactix4.t4openerp.connector._
import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector.transport.{OEDictionary, FutureResult}
import com.tactix4.t4skr.T4skrConnector
import com.typesafe.config.{ConfigFactory, _}
import org.apache.camel.ExchangePattern
import org.apache.camel.component.hl7.HL7MLLPCodec
import org.apache.camel.test.spring.CamelSpringTestSupport
import org.junit.runners.MethodSorters
import org.junit.{FixMethodOrder, Test}
import org.springframework.context.support.{AbstractApplicationContext, ClassPathXmlApplicationContext}
import org.scalacheck.Prop.BooleanOperators
import scala.concurrent.Await
import scala.concurrent.duration._
import scalaz.Scalaz._
import scalaz._

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LastModifiedA08Test extends CamelSpringTestSupport with ADTGen{


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

  val f = new File("src/test/resources/com.tactix4.t4ADT.conf")
  val config: Config = ConfigFactory.parseFile(f)

  val protocol: String = config.getString("openERP.protocol")
  val host: String = config.getString("openERP.hostname")
  val port: Int = config.getInt("openERP.port")
  val username: String = config.getString("openERP.username")
  val password: String = config.getString("openERP.password")
  val database: String = config.getString("openERP.database")

  val tsession = new T4skrConnector(protocol,host,port ).startSession(username, password, database)

  @Test
  def atestA28(){
    check {
      forAllNoShrink(createVisit) { (msgs: List[ADTMsg]) =>
        (msgs.exists(_.msh.msgType == "A08") && msgs.exists(m => m.msh.msgType == "A02") )==> {

          println("The full visit: \n")
          msgs.map(_.msh.msgType + " ") foreach print
          println("")

          msgs.foreach(msg =>{
            println("Sending msg: " + msg.toString.replace('\r','\n'))
            val result = template.requestBody(URI, msg.toString,classOf[String])
            assert(result contains s"MSA|AA|${msg.msh.id}", s"Result does not look like a success: $result")
            if(msg.msh.msgType == "A08") {
               val prev = msgs.takeWhile(_ != msg)
               prev.reverse.find(m => m.msh.msgType == "A01" || m.msh.msgType == "A02" || m.msh.msgType == "A03").map(m => {
                 if(msg.evn.evnOccured == m.evn.evnOccured){
                   checkVisit("A08", msg.pv1)
                 }
               })
            }
          })
          true

        }
      }
    }
  }

 def checkVisit(msgType:String, pv1:PV1Segment):Unit = {
    val response = Await.result(tsession.oeSession.searchAndRead("t4clinical.patient.visit", "name" === pv1.visitID, List("pos_location_parent", "pos_location","consulting_doctor_ids", "visit_start","visit_end")).value,5 seconds)
    response.fold(
    (message: ErrorMessage) => ("Check Visit failed: " + message).left[Boolean],
      (v:List[OEDictionary]) => v.headOption.map(d =>{
        //(wardCode:String,bed:Int,wardName:String,consultingDoctor:Doctor,referringDoctor:Doctor,admittingDoctor:Doctor,hospitalService:String,patientType:String,visitID:VisitId,admitDate:String,dischargeDate:String
        val plp = d.get("pos_location").flatMap(_.array).flatMap(_(1).string).map(_.toUpperCase())
        log.info("pos_location:" + plp)
        val cdID = for {
          o <- d.get("consulting_doctor_ids")
          a <- o.array
          h <- a.headOption
          i <- h.int
        } yield  i
        log.info("cdID: " + cdID)
        val cdName = Await.result(tsession.oeSession.read("hr.employee",List(~cdID),List("name")).value, 2 seconds).map(
          _.headOption.flatMap(_.get("name").flatMap(_.string))
        )
        log.info("cdName: " + cdName)
        val cdc = pv1.consultingDoctor.toString
        log.info("cdc: " + cdc)
        val loc = if(msgType == "A11" || msgType == "A03"){
          assert(plp == None)
        } else {
          assert(plp.flatMap(wards.get) == Some(pv1.wardCode))
        }
        assert((cdc.isEmpty ? (None:Option[String]) | Some(cdc)) == cdName.getOrElse(None))

      })
    )
  }
}
