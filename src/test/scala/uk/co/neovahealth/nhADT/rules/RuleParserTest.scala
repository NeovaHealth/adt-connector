package uk.co.neovahealth.nhADT.rules

import java.util

import com.typesafe.config.ConfigFactory
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.FunSuite
import org.scalatest.prop.{PropertyChecks, Checkers}
import org.scalacheck.Prop.forAllNoShrink

import scala.collection.JavaConversions._
import scala.util.Random

/**
 * Created by max on 14/01/15.
 */
class RuleParserTest extends FunSuite with Checkers{


  val config = ConfigFactory.load("uk.co.neovahealth.nhADT.conf")

  val names: util.Set[String] = config.getConfig("ADT_mappings.common").root().keySet()

  val actionGen: Gen[String] = Gen.oneOf(List("ACCEPT","REJECT"))
  val fieldGen: Gen[String] = Gen.oneOf(names.toList)
  val valueGen: Gen[String] = Gen.containerOfN(10,Gen.alphaChar).map(s => s.mkString)
  val existsGen: Gen[String] = fieldGen.map( "(" ++ _ ++ " EXISTS)")
  val alphNumGen: Gen[String] = Gen.listOf(Gen.alphaNumChar).map(_.mkString)

  val binaryOpGen: Gen[String] = for {
    f <- fieldGen
    v <- valueGen
    o <- Gen.oneOf("==", "!=", "<", "<=", ">",">=", "~=")
  } yield "(" ++ f ++ " " ++ o ++ " "++ v ++ ")"

  val oneOfGen:Gen[String] = for {
    f <- fieldGen
    s <- Gen.containerOf(valueGen)
  } yield "(" ++ f ++ " ONEOF [" ++ s.toSet.mkString(",") ++ "] )"
  
  val andGen:Gen[String] = for {
    a <- exprGen
    b <- exprGen
  } yield "(" ++ a ++ " AND " ++ b ++ ")"
  
  val orGen:Gen[String] = for {
    a <- exprGen
    b <- exprGen
  } yield "(" ++  a ++ " OR " ++ b ++ ")"
  
  val notGen:Gen[String] = for {
    a <- exprGen
  } yield "(" ++ "NOT " ++ a ++ ")"


  def exprGen:Gen[String] = Gen.lzy(Gen.oneOf(existsGen,binaryOpGen,oneOfGen,andGen,orGen,notGen))

  def onFailGen:Gen[String] = for {
    a <- Gen.oneOf("ERROR", "IGNORE")
    b <- Gen.containerOfN(10,Gen.alphaChar).map(_.mkString)
  } yield a ++ " " ++ b

  def genRule = for {
    a <- actionGen
    e <- exprGen
    f <- onFailGen
  } yield a ++ " | " ++ e ++ " | " ++ f

  val arbRule: Arbitrary[String] = Arbitrary { genRule }

  object parser extends RuleParser

  test("getEquals test"){
    val e = "(family_name == johnson)"
    parser.parse(parser.getEquals, e) match {
      case parser.Success(_,_) => ()
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }
  }
  test("getEquals test2"){
    val e = """(family_name == "johnson")"""
    parser.parse(parser.getEquals, e) match {
      case parser.Success(x,_) => println(x)
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }
  }
  test("getGT test"){
    val e = "(age > 10)"
    parser.parse(parser.getGT, e) match {
      case parser.Success(_,_) => ()
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }
  }
  test("getOR test"){
    val e = "((age > 10) OR (name == john))"
    parser.parse(parser.getOR, e) match {
      case parser.Success(_,_) => ()
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }
  }
  test("exists parse test") {
    val e = "(family_name EXISTS)"
    parser.parse(parser.getExists, e) match {
      case parser.Success(_,_) => ()
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }
  }
  test("OneOf") {
    val e = "(consultingDoctorCode ONEOF [blagh,blogh] )"
    parser.parse(parser.getOneOf,e) match {
      case parser.Success(_,_) => ()
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }
  }
  test("exists") {
    val e = "ACCEPT | (family_name EXISTS)"
    val p = (parser.getAction <~ "|") ~ parser.getExists
    parser.parse(p,e) match {
      case parser.Success(_,_) => ()
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }
  }
  test("simple regex test") {
    val e = """ACCEPT | (location ~= "WARD A.*")"""
    val p = (parser.getAction <~ "|") ~ parser.getMatches
    parser.parse(p,e) match {
      case parser.Success(x,_) => println(x)
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }
  }
  test("complex or test") {
    val e = "(((referingDoctorFamilyName EXISTS) OR (patient_identifier >= ijnuQtnpri)) OR (NOT (patient_identifier ONEOF [] )))"
    parser.parse(parser.getOR,e) match {
      case parser.Success(_,_) => ()
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }

  }
  test("line test") {
    val e = "ACCEPT | (family_name EXISTS) | ERROR some error"
    parser.parse(parser.line,e) match {
      case parser.Success(_,_) =>  ()
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }

  }
  test("complex test"){
    val e = "((((referingDoctorCode EXISTS) OR (bed oneOf [mcbihjgogM] )) OR (visit_identifier EXISTS)) OR (((given_name ONEOF [] ) AND (timestamp EXISTS)) OR (NOT (timestamp ONEOF [] ))))"
      parser.parse(parser.getOR,e) match {
      case parser.Success(_,_) => ()
      case parser.Failure(e,_) => fail(e)
      case parser.Error(e,_) => fail(e)
    }

  }
  test("check_arb_rules") {
    check(forAllNoShrink(arbRule.arbitrary) { s =>
      println(s)
      parser.parse(parser.line, s) match {
        case parser.Success(_, _) => true
        case parser.Failure(e, _) => false
        case parser.Error(e, _) => false

      }
    })
  }

}
