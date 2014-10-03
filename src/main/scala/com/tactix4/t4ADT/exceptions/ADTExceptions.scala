package com.tactix4.t4ADT.exceptions

/**
 * Created by max on 02/06/14.
 */
trait ADTExceptions {

  class ADTApplicationException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

  class ADTConsistencyException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

  class ADTFieldException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

  class ADTUnsupportedMessageException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

  class ADTUnsupportedWardException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

  class ADTDuplicateMessageException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

  class ADTHistoricalMessageException(msg: String = null, cause: Throwable = null) extends Exception(msg,cause)

  class ADTUnknownPatientException(msg: String = null, cause: Throwable = null) extends Exception(msg,cause)

  class ADTUnknownVisitException(msg: String = null, cause: Throwable = null) extends Exception(msg,cause)

}
