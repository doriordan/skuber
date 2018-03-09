package skuber

import skuber.Toleration.Effects.NoSchedule


sealed trait Toleration

object Toleration {

  sealed trait TolerationEffect {
    val name: String
  }

  object Effects {

    case object NoSchedule extends TolerationEffect {
      override val name: String = "NoSchedule"
    }

    case object PreferNoSchedule extends TolerationEffect {
      override val name: String = "PreferNoSchedule"
    }

  }


  case class EqualToleration(key: String, value: String, effect: Option[TolerationEffect] = None) extends Toleration

  case class ExistsToleration(key: String, effect: Option[TolerationEffect] = None) extends Toleration

}