package com.tactix4.t4ADT

/**
 * @author max@tactix4.com
 * Date: 26/09/13
 */


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.{TestContextManager, ContextConfiguration}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.scalatest.matchers.ShouldMatchers
import ca.uhn.hl7v2.model.v24.message.ADT_A01
import ca.uhn.hl7v2.util.Terser


@ContextConfiguration(locations=Array("classpath:META-INF/spring/testBeans.xml"))
class ADTInRouteTest extends FunSuite with ShouldMatchers with BeforeAndAfterAll{

  val configPath = "src/test/resources/ADT_A01.properties"

  @Autowired val route :ADTInRoute  = null

  new TestContextManager(this.getClass).prepareTestInstance(this)

  val testMessage = new ADT_A01()
  testMessage.initQuickstart("ADT", "A01", "P")
  implicit val terser = new Terser(testMessage)
  terser.set("PID-5-1", "Bobkins")
  terser.set("PID-5-2", "Bob")
  terser.set("PID-5-3", null)
  terser.set("PID-7-1", "19850101000000")
  terser.set("PID-8", "M")


  test("read from the terserMap"){
    assert(route.checkTerserPath("A01","firstName").isSuccess)
  }
  test("generate error on non existent message type in terserMap"){
    route.checkTerserPath("A099","firstName").fold(
      l => l.head should equal ("Could not find terser configuration for messages of type: A099"),
      _ => fail("did not fail"))
  }
  test("generate error on non existent message attribute in terserMap"){
    route.checkTerserPath("A01","middleName").fold(
      l => l.head should equal ("Could not find attribute: middleName in terser configuration"),
      _ => fail("did not fail"))
  }
  test("generate error on empty message attribute in terserMap"){
    route.checkTerserPath("A01","middleName").fold(
      l => l.head should equal ("Could not find attribute: middleName in terser configuration"),
      _ => fail("did not fail"))
  }
  test("generate a failure on an invalid date") {
    val invalidDate = "0o8ijasdf"
    route.checkDate(invalidDate).fold(
    l => l.head should startWith ("Invalid format: \"" + invalidDate +"\""),
    _ => fail("did not fail")
    )
  }
  test("generate a failure on an invalid terserPath") {
    route.checkTerserPath("A01", "terserFail").fold(
    l => l.head should include regex "The pattern .* is not valid",
    _ => fail("did not fail")
    )
  }

}
