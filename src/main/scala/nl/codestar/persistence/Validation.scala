package nl.codestar.persistence

import java.time.LocalDateTime

import cats.data.{Validated, ValidatedNel}
import cats.data.Validated._

import collection.immutable.Seq

object Validations {
  type Validation[T] = ValidatedNel[Error, T]
  
  
  
  def validateInTheFuture(dateTime: LocalDateTime): Validation[LocalDateTime] = {
    if(dateTime.isAfter(LocalDateTime.now)) valid(dateTime)
    else invalidNel(ValidationError("NotInTheFuture", "Date time should be in the future", Seq(dateTime)))
  }
}



sealed trait Error {
  val code: String
  val msg: String
  val values: Seq[AnyRef]
}

case class ValidationError(code: String, msg: String, values: Seq[AnyRef] = Seq.empty) extends Error