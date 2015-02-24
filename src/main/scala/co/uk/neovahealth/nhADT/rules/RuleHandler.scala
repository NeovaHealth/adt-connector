package co.uk.neovahealth.nhADT.rules

import co.uk.neovahealth.nhADT.ADTProcessing
import co.uk.neovahealth.nhADT.utils.ConfigHelper
import org.apache.camel.{Processor, Exchange}
import scala.util.control.Exception._
import scalaz.std.string._
import scalaz.syntax.monoid._
import scalaz.syntax.std.option._
/**
 * Created by max on 24/02/15.
 */
trait RuleHandler extends RuleParser with ADTProcessing{

 val rules:List[Rule] = ConfigHelper.ruleFile.map(parse(line,_).get)

  def evalExpression(z:Expr)(implicit e:Exchange): Boolean = z.value(getField(_))

  lazy val processRules:Processor = new Processor {
    override def process(e: Exchange): Unit = {
      val result = allCatch opt rules.par.collect {
        case (action, exec, fail) if action != evalExpression(exec)(e) => fail
      }
      result.map(r => {
        if(r.nonEmpty) {
          val c = r.reduce(_ |+| _)
          throw new ADTRuleException(c.msg)
        }
      })
    }
  }
}
