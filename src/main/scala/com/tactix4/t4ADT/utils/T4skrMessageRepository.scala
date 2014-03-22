package com.tactix4.t4ADT.utils

import org.apache.camel.processor.idempotent.jdbc.{JdbcMessageIdRepository, AbstractJdbcMessageIdRepository}
import javax.sql.DataSource
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.TransactionStatus
import org.springframework.dao.DataAccessException
import java.sql.Timestamp
import scala.beans.BeanProperty

/**
 * Created by max on 19/03/14.
 */
class T4skrMessageRepository(d: DataSource, name: String) extends JdbcMessageIdRepository(d, name) {

  @BeanProperty
  var tableName = "T4SKR_MSG_REPO"

  protected var createTableIfNotExists = true
  override def isCreateTableIfNotExists:Boolean = createTableIfNotExists
  override def setCreateTableIfNotExists(b:Boolean) = createTableIfNotExists = b

  protected var tableExistsString = s"SELECT 1 FROM $tableName WHERE 1 = 0"
  override def getTableExistsString = tableExistsString
  override def setTableExistsString(s:String) = tableExistsString = s

  protected var createString = s"CREATE TABLE $tableName (processorName VARCHAR(255), messageId VARCHAR(100), createdAt TIMESTAMP)"
  override def getCreateString = createString
  override def setCreateString(s:String) = createString = s

  protected var queryString = s"SELECT COUNT(*) FROM $tableName WHERE processorName = ? AND messageId = ?"
  override  def getQueryString = queryString
  override def setQueryString(s:String) = queryString = s

  protected var insertString = s"INSERT INTO $tableName (processorName, messageId, createdAt) VALUES (?, ?, ?)"
  override def getInsertString = insertString
  override def setInsertString(s:String) = insertString = s

  protected var deleteString = s"DELETE FROM $tableName WHERE processorName = ? AND messageId = ?"
  override def getDeleteString = deleteString
  override def setDeleteString(s:String) = deleteString = s


}
