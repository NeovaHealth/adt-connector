package co.uk.neovahealth.nhADT

/**
 * Tests the A28 and A05 patientNew route
 * @author max@tactix4.com
 * Date: 26/09/13
 */

import java.io.File

import com.tactix4.t4openerp.connector._
import com.tactix4.t4openerp.connector.domain.Domain._
import com.typesafe.config.{ConfigFactory, _}
import org.junit.runners.MethodSorters
import org.junit.{FixMethodOrder, Test}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scalaz.EitherT
import scalaz.std.option.optionSyntax._


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PatientNewUpdateMergeDischargeTest extends ADTTest{

  val f = new File("src/test/resources/co.uk.neovahealth.nhADT.conf")
  lazy val config: Config = ConfigFactory.parseFile(f)

  lazy val protocol: String = config.getString("openERP.protocol")
  lazy val host: String = config.getString("openERP.hostname")
  lazy val port: Int = config.getInt("openERP.port")
  lazy val username: String = config.getString("openERP.username")
  lazy val password: String = config.getString("openERP.password")
  lazy val database: String = config.getString("openERP.database")

  lazy val tsession = new OEConnector(protocol,host,port ).startSession(username, password, database)

  @Test
  def atestA28(){
    log.info("sending patientNew with ADT_A28 : id: " + TestVars.patientOneId)
    sendMessageAndExpectResponse(TestVars.patientNewADT28, "MSA|AA|")
  }
  @Test
  def btestA05{
    log.info("sending patientNew with ADT_A05 : id: " + TestVars.patientTwoId)
    sendMessageAndExpectResponse(TestVars.patientNewADT05, "MSA|AA|")
  }
  @Test
  def ctestA08 {
    log.info("updating patientTwo")
    sendMessageAndExpectResponse(TestVars.patientUpdateADT_08, "MSA|AA|")
  }

  @Test
  def dtestA31{
    log.info("updating patientOne")
    sendMessageAndExpectResponse(TestVars.patientUpdateADT_31, "MSA|AA|")
  }
  @Test
  def etestA40{
    log.info("merge patientOne into patientTwo")
    sendMessageAndExpectResponse(TestVars.patientMerge, "MSA|AA|")
  }
  @Test
  def fatestA01 {
  log.info("new visit for patientTwo")
  sendMessageAndExpectResponse(TestVars.visitNew, "MSA|AA|")
}
  @Test
  def fbtestA01{
    log.info("new visit for patientTwo")
    sendMessageAndExpectError(TestVars.visitNewBroken, "Could not locate hospital number")
  }
  @Test
  def fctestA08{
    log.info("trying to historically updating patientTwo")
    sendMessageAndExpectResponse(TestVars.patientUpdateADT_08H, "MSA|AA|")
    val result: EitherT[Future, ErrorMessage, Boolean] = for {
        r <- tsession.searchAndRead("t4clinical.patient", "other_identifier" === TestVars.patientTwoId, List("given_name") )
        h <- (r.headOption \/> "Patient not found").asOER
        n <- (h.get("given_name").flatMap(_.string) \/> "given_name not found").asOER
      } yield !(n contains "BERYL")
    assert(Await.result(result.getOrElse(false), 2 seconds), "name should not be updated by historical message")

  }

  @Test
  def fdtestA02{
    log.info("Trying to transfer")
    sendMessageAndExpectResponse(TestVars.transfer, "MSA|AA|")
  }
  @Test
  def gtestA03 {
    log.info("discharge patientTwo")
    sendMessageAndExpectResponse(TestVars.patientDischarge, "MSA|AA|")
  }

  @Test
  def hfail28{
    sendMessageAndExpectError(TestVars.patientNewADT28No_Identity, "Could not locate hospital number")
  }
  @Test
  def ifail05{
    sendMessageAndExpectError(TestVars.patientNewADT05No_Identity, "Could not locate hospital number")
  }
  @Test
  def jfail08{
    sendMessageAndExpectError(TestVars.patientUpdateADTNo_Identity_08, "Could not locate hospital number")
  }
  @Test
  def kfail31{
    sendMessageAndExpectError(TestVars.patientUpdateADTNo_Identity_31, "Could not locate hospital number")
  }

}
