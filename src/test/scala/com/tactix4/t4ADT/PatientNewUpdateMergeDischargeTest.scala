package com.tactix4.t4ADT

/**
 * Tests the A28 and A05 patientNew route
 * @author max@tactix4.com
 * Date: 26/09/13
 */

import java.io.File

import com.tactix4.t4openerp.connector.transport.FutureResult
import com.tactix4.t4skr.T4skrConnector
import com.typesafe.config.ConfigFactory
import com.typesafe.config._
import org.junit.{FixMethodOrder, Test}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random
import scalaz._
import Scalaz._
import com.tactix4.t4openerp.connector._
import com.tactix4.t4openerp.connector.domain.Domain._
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PatientNewUpdateMergeDischargeTest extends ADTTest{


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
  def fatestA01{
    log.info("new visit for patientTwo")
    sendMessageAndExpectResponse(TestVars.visitNew, "MSA|AA|")
  }
  @Test
  def fbtestA01{
    log.info("new visit for patientTwo")
    sendMessageAndExpectError(TestVars.visitNewBroken, "Hospital number: None")
  }
  @Test
  def fctestA08{
    log.info("trying to historically updating patientTwo")
    sendMessageAndExpectError(TestVars.patientUpdateADT_08H, "MSA|AA|")
    Thread.sleep(2000)
    val b = tsession.oeSession.search("t4clinical.patient", "other_identifier" === TestVars.patientTwoId ).flatMap(
      ids => FutureResult(ids.headOption.toSuccess("No Patient Found")).flatMap(
        i => tsession.getPatient(i).map(
          p => ! (p.name contains "BERYL")
        )
      )
    )
    assert(Await.result(b.value, 2 seconds) | false, "name should not be updated by historical message")

  }
  @Test
  def gtestA03 {
    log.info("discharge patientTwo")
    sendMessageAndExpectResponse(TestVars.patientDischarge, "MSA|AA|")
  }

  @Test
  def hfail28{
    sendMessageAndExpectError(TestVars.patientNewADT28No_Identity, "Required field missing")
  }
  @Test
  def ifail05{
    sendMessageAndExpectError(TestVars.patientNewADT05No_Identity, "Required field missing")
  }
  @Test
  def jfail08{
    sendMessageAndExpectError(TestVars.patientUpdateADTNo_Identity_08, "Required field missing")
  }
  @Test
  def kfail31{
    sendMessageAndExpectError(TestVars.patientUpdateADTNo_Identity_31, "Required field missing")
  }
  @Test
  def lfail40{
    sendMessageAndExpectError(TestVars.patientMergeADTNo_Identifier, "Patients to merge did not exist")
  }

}
