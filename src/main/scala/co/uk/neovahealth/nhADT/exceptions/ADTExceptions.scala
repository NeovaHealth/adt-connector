package co.uk.neovahealth.nhADT.exceptions

/**
 * Created by max on 02/06/14.
 */
trait ADTExceptions {

  class ADTRuleException(msg:String = null,cause:Throwable = null) extends Exception(msg,cause)

  class ADTApplicationException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

  class ADTFieldException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

  class ADTUnknownPatientException(msg: String = null, cause: Throwable = null) extends Exception(msg,cause)

  class ADTUnknownVisitException(msg: String = null, cause: Throwable = null) extends Exception(msg,cause)

  class ADTHistoricalMessage(msg: String = null, cause: Throwable = null) extends Exception(msg,cause)

}
