package guixin.mm

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

abstract class UnitSpec extends FunSuite with Matchers with OptionValues with Inside
  with Inspectors with ScalaFutures
