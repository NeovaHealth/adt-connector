package co.uk.neovahealth.nhADT.rules

import scala.util.matching.Regex
import scala.util.parsing.combinator.{RegexParsers, JavaTokenParsers}
import scalaz.Monoid

/**
 * Created by max on 13/01/15.
 */
trait RuleParser extends JavaTokenParsers {


  val Accept = true
  val Reject = false

  case class OnFail(msg:String, ignore:Boolean)

  object OnFail{
    implicit val monoidInstance = new Monoid[OnFail] {
      override def zero: OnFail = OnFail("",true)
      override def append(f1: OnFail, f2: => OnFail): OnFail = OnFail(f1.msg ++ " " ++ f2.msg, f1.ignore && f2.ignore)
    }
  }

  type Field = String
  type Value = String


  sealed trait Expr {
    def value(g:Field => Option[String]):Boolean
  }

  case class Exists(f:Field) extends Expr {
    def value(g:Field => Option[String]) = g(f).isDefined
  }
  case class Equals(f:Field, v:Value) extends Expr {
    def value(g:Field => Option[String]):Boolean =g(f).exists(_ == v)
  }
  case class NotEquals(f:Field, v:Value) extends Expr{
    def value(g:Field => Option[String]):Boolean = g(f).exists(_ != v)
  }
  case class GT(f:Field,v:Value) extends Expr {
    def value(g:Field => Option[String]):Boolean = g(f).exists(_ > v)
  }
  case class GTE(f:Field,v:Value) extends Expr{
    def value(g:Field => Option[String]):Boolean = g(f).exists(_ >= v)
  }
  case class LT(f:Field,v:Value) extends Expr {
    def value(g:Field => Option[String]):Boolean = g(f).exists(_ < v)
  }
  case class LTE(f:Field,v:Value) extends Expr {
    def value(g:Field => Option[String]):Boolean = g(f).exists(_ <= v)
  }
  case class Matches(f:Field,r:Regex) extends Expr {
    def value(g:Field => Option[String]):Boolean = g(f).exists(r.findFirstIn(_).isDefined)
  }
  case class OneOf(f:Field,l:Set[Value]) extends Expr{
    def value(g:Field => Option[String]):Boolean = g(f).exists(l contains)
  }
  case class AND(e1:Expr, e2:Expr) extends Expr {
    def value(g:Field => Option[String]):Boolean = e1.value(g) && e2.value(g)
  }
  case class OR(e1:Expr,e2: Expr) extends Expr {
    def value(g:Field => Option[String]):Boolean = e1.value(g) || e2.value(g)
  }
  case class NOT(e:Expr) extends Expr {
    def value(g:Field => Option[String]):Boolean = ! e.value(g)
  }


  type Rule = (Boolean,Expr,OnFail)

  def getField: Parser[Field] ="(" ~> """\w+""".r
  def getValue: Parser[Value] = ("""[^"\s)]+""".r | """".*"""".r) <~ ")" ^^ { _.replaceAll(""""(.*)"""","""$1""") }
  def getExists: Parser[Exists] = getField <~  "EXISTS"  <~ ")"^^ Exists
  def getEquals: Parser[Equals] = ((getField <~  "==") ~ getValue) ^^ (f => Equals(f._1, f._2))
  def getNotEquals: Parser[Equals] = ((getField <~  "!= ") ~ getValue) ^^ (f => Equals(f._1, f._2))
  def getGT:Parser[GT] = ((getField <~  "> ") ~ getValue) ^^ (f => GT(f._1, f._2))
  def getGTE:Parser[GTE] = ((getField <~  ">= ") ~ getValue) ^^ (f => GTE(f._1, f._2))
  def getLT:Parser[LT] = ((getField <~  "< ") ~ getValue) ^^ (f => LT(f._1, f._2))
  def getLTE:Parser[LTE] = ((getField <~  "<= ") ~ getValue) ^^ (f => LTE(f._1, f._2))
  def getMatches: Parser[Matches] =((getField <~  "~=") ~ getValue) ^^ (f => Matches(f._1, f._2.r))
  def getOneOf: Parser[OneOf] = (getField <~ "ONEOF" <~ "[") ~ (repsep("""[^,\]]+""".r,",") <~ "]") <~ ")" ^^ { x => OneOf(x._1,x._2.toSet)}
  def getAND: Parser[AND] = ("(" ~> getExpr <~ "AND") ~ getExpr <~ ")" ^^ {a => AND(a._1,a._2)}
  def getOR: Parser[OR] = ("(" ~> getExpr <~ "OR") ~ getExpr <~ ")" ^^ { o => OR(o._1,o._2)}
  def getNOT: Parser[NOT] = "(" ~> "NOT" ~> getExpr <~ ")" ^^ NOT

  def getAction: Parser[Boolean] = "ACCEPT" ^^ (_ => Accept) | "REJECT" ^^ (_ => Reject)
  def getExpr:Parser[Expr] = getExists | getEquals | getNotEquals |getGT | getGTE | getLT | getLTE | getMatches | getOneOf | getAND | getOR | getNOT
  def getOnFail: Parser[OnFail] = ("IGNORE" ~> """.+$""".r ^^ (e => OnFail(e,true))) | ("ERROR" ~> """.+$""".r ^^ (e => OnFail(e,false)))

  val line:Parser[Rule] = (getAction <~ "|") ~ (getExpr <~ "|") ~ getOnFail ^^ { s => (s._1._1, s._1._2, s._2)}




}
