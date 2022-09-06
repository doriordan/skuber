package skuber.json

import org.specs2.mutable.Specification
import play.api.libs.functional.syntax._
import play.api.libs.json._
import skuber.json
import skuber.json.EnumDummy.{EnumDummy, EnumValue1, EnumValue2, EnumValue3}
import skuber.json.format._

class EnumTest extends Specification {
  "Test enum formatters\n ".txt


  "Test enum formatters suite\n" >> {
    "Convert json to enum object" >> {

      val expectedTestClass = TestClassEnum(dummyEnum = EnumDummy.EnumValue1)
      val actualTestClass = Json.fromJson[TestClassEnum](Json.parse(
        """
          |{
          |  "dummyEnum": "EnumValue1"
          |}
        """.stripMargin))(TestClassEnum.fmt.reads).get

      val expectedTestClassAfterToJson = Json.toJson(actualTestClass).as[TestClassEnum]

      actualTestClass mustEqual expectedTestClass
      actualTestClass mustEqual expectedTestClassAfterToJson

    }

    "Test default enum formatter" >> {

      val expectedTestClass = TestClassEnumNone(dummyEnum = Some(EnumValue1))

      val actualTestClass = Json.fromJson[TestClassEnumNone](Json.parse(
        """
          |{
          |  "dummyEnum": "asdad"
          |}
        """.stripMargin))(TestClassEnumNone.fmtNone.reads).get

      val expectedTestClassAfterToJson = Json.toJson(actualTestClass).as[TestClassEnumNone]

      actualTestClass mustEqual expectedTestClass
      actualTestClass mustEqual expectedTestClassAfterToJson

    }

    "Test enum formatter with json path" >> {

      val expectedTestClass = JsPathClass(dummyEnum = EnumValue2, dummyEnumOpt = Some(EnumValue1), dummyUseDefault = EnumValue3)

      val actualTestClass = Json.parse(
        """
          |{
          |  "dummyEnum": "EnumValue2",
          |  "dummyEnumOpt": "EnumValue1"
          |}
        """.stripMargin)

      val actualResult: JsPathClass = actualTestClass.as[JsPathClass]

      val expectedTestClassAfterToJson: JsPathClass = Json.toJson(actualResult).as[JsPathClass]

      actualResult mustEqual expectedTestClass
      actualResult mustEqual expectedTestClassAfterToJson

    }

    "Test enum formatter with none value and also default value" >> {

      val expectedTestClass = JsPathClass(dummyEnum = EnumValue2,
        dummyEnumOpt = None,
        dummyUseDefault = EnumValue3)

      val actualTestClass = Json.parse(
        """
          |{
          |  "dummyEnum": "EnumValue2"
          |}
        """.stripMargin)

      val actualResult: JsPathClass = actualTestClass.as[JsPathClass]

      val expectedTestClassAfterToJson: JsPathClass = Json.toJson(actualResult).as[JsPathClass]

      actualResult mustEqual expectedTestClass
      actualResult mustEqual expectedTestClassAfterToJson

    }

    "Test enum formatter with wrong enum value -> fallback to None" >> {

      val expectedTestClass = JsPathClass(dummyEnum = EnumValue2,
        dummyEnumOpt = None,
        dummyUseDefault = EnumValue3)

      val actualTestClass = Json.parse(
        """
          |{
          |  "dummyEnum": "EnumValue2",
          |  "dummyUseDefault" : "faillll"
          |}
        """.stripMargin)

      val actualResult: JsPathClass = actualTestClass.as[JsPathClass]

      val expectedTestClassAfterToJson: JsPathClass = Json.toJson(actualResult).as[JsPathClass]

      actualResult mustEqual expectedTestClass
      actualResult mustEqual expectedTestClassAfterToJson

    }

  }

}

case class TestClassEnum(dummyEnum: EnumDummy)

case class TestClassEnumNone(dummyEnum: Option[EnumDummy])

case class JsPathClass(dummyEnum: EnumDummy, dummyEnumOpt: Option[EnumDummy], dummyUseDefault: EnumDummy)

object JsPathClass {
  implicit val jsPathClassFmt: Format[JsPathClass] =
    ((JsPath \ "dummyEnum").formatEnum(EnumDummy) and
      (JsPath \ "dummyEnumOpt").formatNullableEnum(EnumDummy) and
      (JsPath \ "dummyUseDefault").formatEnum(EnumDummy, EnumValue3.toString)) (JsPathClass.apply, j => (j.dummyEnum, j.dummyEnumOpt, j.dummyUseDefault))
}

object TestClassEnum {
  implicit val enumFmt: Format[json.EnumDummy.Value] = EnumDummy.fmtDefault
  implicit val fmt: OFormat[TestClassEnum] = Json.format[TestClassEnum]
}

object TestClassEnumNone {
  implicit val enumFmt: Format[json.EnumDummy.Value] = EnumDummy.fmtDefault
  implicit val fmtNone: OFormat[TestClassEnumNone] = Json.format[TestClassEnumNone]
}

object EnumDummy extends Enumeration {
  type EnumDummy = Value
  val EnumValue1, EnumValue2, EnumValue3 = Value
  val fmtDefault: Format[json.EnumDummy.Value] = enumDefault(this, EnumValue1.toString)
}
