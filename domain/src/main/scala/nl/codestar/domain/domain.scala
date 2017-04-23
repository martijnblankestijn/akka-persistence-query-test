package nl.codestar.domain

object domain {
  // states of an appointment
  trait State
  case object Busy extends State
  case object Tentative extends State
  case object Confirmed extends State
  case object Cancelled extends State

}
