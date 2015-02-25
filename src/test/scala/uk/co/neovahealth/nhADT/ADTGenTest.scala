package uk.co.neovahealth.nhADT

import com.tactix4.t4openerp.connector.{OEConnector, _}
import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector.domain._
import com.tactix4.t4openerp.connector.transport.{OEString, OEType}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logging
import com.typesafe.scalalogging.slf4j.StrictLogging
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
class ADTGenTest extends CamelSpringTestSupport with PropertyChecks with ADTGen with Matchers with TripleEqualsSupport with StrictLogging{


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

  @Test
  def randomVisitTest() = {
    val property = forAllNoShrink(createVisit) { (msgs: List[ADTMsg]) =>
      logger.info("Testing visit for " + ~msgs.headOption.map(_.pid.p.hospitalNo) +":  " +msgs.filter(_.msh.msgType != "A40").map(_.msh.msgType).mkString(","))
          msgs.foreach(msg => {
            if (msg.msh.msgType != "A40") {
              val result:String = template.requestBody(URI, msg.toString, classOf[String])
              assert(result contains s"MSA|AA|${msg.msh.id}", s"Result does not look like a success: $result")
            }
          })
          true
      }
    check( property,Parameters.default.withMinSuccessfulTests(100).withWorkers(1))
  }

}
