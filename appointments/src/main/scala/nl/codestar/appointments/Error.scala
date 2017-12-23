package nl.codestar.appointments

import scala.collection.immutable.Seq

sealed trait Error {
  def code: String
  def msg: String
  def values: Seq[AnyRef]
}

case class AlreadyExists(msg: String, values: Seq[AnyRef]) extends Error {
  val code = "Exists"
}

case class CancelledError(msg: String, values: Seq[AnyRef]) extends Error {
  val code = "Cancelled"
}

case class ValidationError(code: String, msg: String, values: Seq[AnyRef])
    extends Error
