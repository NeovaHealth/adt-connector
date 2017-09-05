package uk.co.neovahealth.nhADT

/**
 * Tests the A28 and A05 patientNew route
 * @author max@tactix4.com
 * Date: 26/09/13
 */

import java.io.File

import com.tactix4.t4openerp.connector._
import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector.transport.OEType
import com.typesafe.config.{ConfigFactory, _}
import org.apache.camel.component.hl7.HL7MLLPCodec
import org.apache.camel.test.spring.CamelSpringTestSupport
import org.junit.runners.MethodSorters
import org.junit.{FixMethodOrder, Test}
import org.scalacheck.Prop.{BooleanOperators, forAllNoShrink}
import org.scalatest.prop.Checkers.check
import org.springframework.context.support.{AbstractApplicationContext, ClassPathXmlApplicationContext}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scalaz.Scalaz._

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

  val f = new File("src/test/resources/uk.co.neovahealth.nhADT.conf")
  val config: Config = ConfigFactory.parseFile(f)

  val protocol: String = config.getString("odoo.protocol")
  val host: String = config.getString("odoo.hostname")
  val port: Int = config.getInt("odoo.port")
  val username: String = config.getString("odoo.username")
  val password: String = config.getString("odoo.password")
  val database: String = config.getString("odoo.database")

  val tsession = new OEConnector(protocol,host,port ).startSession(username, password, database)

//  @Test
//  def atestA28(){
//    check {
//      forAllNoShrink(createVisit) { (msgs: List[ADTMsg]) =>
//        (msgs.exists(_.msh.msgType == "A08") && msgs.exists(m => m.msh.msgType == "A02") )==> {
//
//          println("The full visit: \n")
//          msgs.map(_.msh.msgType + " ") foreach print
//          println("")
//
//          msgs.foreach(msg =>{
//            println("Sending msg: " + msg.toString.replace('\r','\n'))
//            val result = template.requestBody(URI, msg.toString,classOf[String])
//            assert(result contains s"MSA|AA|${msg.msh.id}", s"Result does not look like a success: $result")
//            if(msg.msh.msgType == "A08") {
//               val prev = msgs.takeWhile(_ != msg)
//               prev.reverse.find(m => m.msh.msgType == "A01" || m.msh.msgType == "A02" || m.msh.msgType == "A03").map(m => {
//                 if(msg.evn.evnOccured == m.evn.evnOccured){
//                   checkVisit("A08", msg.pv1)
//                 }
//               })
//            }
//          })
//          true
//
//        }
//      }
//    }
//  }

 def checkVisit(msgType:String, pv1:PV1Segment):Unit = {
    val response = Await.result(tsession.searchAndRead("t4clinical.patient.visit", "name" === pv1.visitID, List("pos_location_parent", "pos_location","consulting_doctor_ids", "visit_start","visit_end")).run,5 seconds)
    response.fold(
    (message: ErrorMessage) => ("Check Visit failed: " + message).left[Boolean],
      (v:List[Map[String,OEType]]) => v.headOption.map(d =>{
        val plp = d.get("pos_location").flatMap(_.array).flatMap(_(1).string).map(_.toUpperCase())
        log.info("pos_location:" + plp)
        val cdID = for {
          o <- d.get("consulting_doctor_ids")
          a <- o.array
          h <- a.headOption
          i <- h.int
        } yield  i
        log.info("cdID: " + cdID)
        val cdName = Await.result(tsession.read("hr.employee",List(~cdID),List("name")).run, 2 seconds).map(
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
